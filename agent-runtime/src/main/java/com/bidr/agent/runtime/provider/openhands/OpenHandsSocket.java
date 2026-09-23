package com.bidr.agent.runtime.provider.openhands;

import lombok.extern.slf4j.Slf4j;

import javax.websocket.ClientEndpointConfig;
import javax.websocket.CloseReason;
import javax.websocket.Endpoint;
import javax.websocket.EndpointConfig;
import javax.websocket.MessageHandler;
import javax.websocket.Session;
import javax.websocket.WebSocketContainer;
import java.io.IOException;
import java.net.SocketTimeoutException;
import java.net.URI;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

/**
 * Title: OpenHandsSocket
 * Description: OpenHands 会话级 socket 的**阻塞读**包装（{@code javax.websocket} API + Tomcat 实现，
 * 二者由 spring-boot-starter-web 传递带入，不新增依赖）。
 * <p>
 * 上游是异步推送、我方 relay 是同步泵帧，故用队列把两种模型接起来：容器回调线程入队、
 * {@link #nextFrame(long)} 出队。对端正常关闭 → 返回 {@code null}（收流）；异常关闭或等待超时 →
 * 抛 {@code IOException}（relay 按**断流**处理，绝不当取消）。
 * <p>
 * 鉴权走握手头 {@code X-Session-API-Key}：密钥不进 URL，因此不落反代/网关访问日志
 * （上游把 query 参数方式标为 deprecated，另有一种"首帧 auth"方式，服务端三者等价）。
 *
 * @author sharp
 * @since 2026/9/23
 */
@Slf4j
final class OpenHandsSocket implements FrameSource {

    /**
     * 关闭哨兵：把「对端已收流」作为一个队列元素传递，避免与真实帧混淆
     */
    private static final String CLOSED = "\u0000closed";

    private final BlockingQueue<String> queue = new LinkedBlockingQueue<>();
    private volatile Throwable failure;
    private Session session;

    private OpenHandsSocket() {
    }

    /**
     * 建链（同步：握手失败即抛，供 provider 在开流前把错误交给框架回 JSON）
     *
     * @param container        共享容器（客户端模式下不自起后台线程，可全应用复用一个）
     * @param uri              {@code ws(s)://host/sockets/session/{cid}[?after_seq=N]}
     * @param apiKey           会话密钥（只进握手头）
     * @param idleTimeoutMs    空闲超时（0 = 不限；上游执行长命令期间可能长时间无帧）
     */
    static OpenHandsSocket open(WebSocketContainer container, URI uri, String apiKey,
                                int idleTimeoutMs) throws IOException {
        final OpenHandsSocket socket = new OpenHandsSocket();
        ClientEndpointConfig config = ClientEndpointConfig.Builder.create()
                .configurator(new ClientEndpointConfig.Configurator() {
                    @Override
                    public void beforeRequest(Map<String, List<String>> headers) {
                        headers.put("X-Session-API-Key", Collections.singletonList(apiKey));
                    }
                })
                .build();
        Endpoint endpoint = new Endpoint() {
            @Override
            public void onOpen(Session opened, EndpointConfig endpointConfig) {
                opened.addMessageHandler(new MessageHandler.Whole<String>() {
                    @Override
                    public void onMessage(String text) {
                        socket.queue.offer(text);
                    }
                });
            }

            @Override
            public void onClose(Session closed, CloseReason reason) {
                if (reason != null && reason.getCloseCode() != CloseReason.CloseCodes.NORMAL_CLOSURE
                        && reason.getCloseCode() != CloseReason.CloseCodes.GOING_AWAY) {
                    socket.failure = new IOException("会话 socket 被上游关闭：" + reason);
                }
                socket.queue.offer(CLOSED);
            }

            @Override
            public void onError(Session errored, Throwable throwable) {
                socket.failure = throwable;
                socket.queue.offer(CLOSED);
            }
        };
        try {
            socket.session = container.connectToServer(endpoint, config, uri);
            socket.session.setMaxIdleTimeout(idleTimeoutMs);
        } catch (Exception e) {
            throw new IOException("连接会话 socket 失败（" + e.getMessage() + "）", e);
        }
        return socket;
    }

    /**
     * 取下一帧原文
     *
     * @param timeoutMs 等待上限（≤0 表示无限等）
     * @return 帧 JSON；{@code null} = 对端已收流
     * @throws IOException 链路异常或等待超时（按断流处理）
     */
    @Override
    public String nextFrame(long timeoutMs) throws IOException {
        String frame;
        try {
            frame = timeoutMs > 0 ? queue.poll(timeoutMs, TimeUnit.MILLISECONDS) : queue.take();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("等待会话 socket 帧被中断", e);
        }
        if (frame == null) {
            throw new SocketTimeoutException("会话 socket 空闲超时（" + timeoutMs + "ms）");
        }
        if (CLOSED.equals(frame)) {
            Throwable cause = failure;
            if (cause != null) {
                throw new IOException(cause.getMessage(), cause);
            }
            return null;
        }
        return frame;
    }

    /**
     * 收流（只断本次消费，**不中断上游执行**——断流 ≠ 取消）
     */
    @Override
    public void close() {
        try {
            if (session != null && session.isOpen()) {
                session.close();
            }
        } catch (Exception e) {
            log.debug("关闭会话 socket 异常（忽略）：{}", e.getMessage());
        }
    }
}
