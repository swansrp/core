package com.bidr.agent.runtime.relay;

import com.bidr.agent.runtime.config.AgentRuntimeConfigProvider;
import com.bidr.llm.agent.runtime.spi.RuntimeTurnLink;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import javax.annotation.PreDestroy;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Title: AgentStreamRelay
 * Description: <b>与上游实现无关</b>的帧泵：把一条 {@link RuntimeTurnLink} 的帧接到浏览器 SSE 上，
 * 只管生命周期、心跳与线程，不碰任何上游协议细节（那些关在 {@code AgentRuntimeProvider} 实现里）。
 * <ul>
 * <li><b>帧原样下发</b>：relay 不解析帧，顺序即上游顺序；</li>
 * <li><b>断流 ≠ 取消</b>：浏览器断开只 {@code link.close()} 停止消费，绝不取消上游轮次；</li>
 * <li><b>注释心跳</b>：周期 {@code : ping} 防反代空闲断连；</li>
 * <li><b>有界线程池</b>：每活跃轮次占 1 线程 + 1 条上游连接。</li>
 * </ul>
 * 开流前错误不在这里处理——由 provider 在 {@code openTurn()} 阶段同步抛出，
 * 控制器因此能回 JSON 信封而不是半开的 SSE。
 *
 * @author sharp
 * @since 2026/9/22
 */
@Slf4j
public class AgentStreamRelay {

    /**
     * 共享心跳调度器（daemon 单线程：发送轻量，多连接串行复用足够，随应用存活）
     */
    private static final ScheduledExecutorService HEARTBEAT =
            Executors.newSingleThreadScheduledExecutor(runnable -> {
                Thread thread = new Thread(runnable, "agent-relay-heartbeat");
                thread.setDaemon(true);
                return thread;
            });

    private final AgentRuntimeConfigProvider config;
    private final ExecutorService pumpPool;

    public AgentStreamRelay(AgentRuntimeConfigProvider config) {
        this.config = config;
        this.pumpPool = Executors.newFixedThreadPool(Math.max(4, config.getRelayThreads()), runnable -> {
            Thread thread = new Thread(runnable, "agent-relay-pump");
            thread.setDaemon(true);
            return thread;
        });
    }

    /**
     * 把已建好的链挂到浏览器 SSE 上（调用方已完成同步建链，故此处不再抛业务异常）
     */
    public SseEmitter attach(RuntimeTurnLink link) {
        SseEmitter emitter = new SseEmitter(0L);
        AtomicBoolean finished = new AtomicBoolean(false);
        ScheduledFuture<?> heartbeat = HEARTBEAT.scheduleAtFixedRate(() -> {
            try {
                emitter.send(SseEmitter.event().comment("ping"));
            } catch (Exception e) {
                finished.set(true);
            }
        }, config.getHeartbeatSeconds(), config.getHeartbeatSeconds(), TimeUnit.SECONDS);

        Runnable closeLink = () -> {
            if (finished.compareAndSet(false, true)) {
                heartbeat.cancel(false);
                link.close();
            }
        };
        // 浏览器断开/超时/出错：只停止本次消费，不取消上游轮次
        emitter.onCompletion(closeLink);
        emitter.onTimeout(closeLink);
        emitter.onError(error -> closeLink.run());

        pumpPool.execute(() -> {
            try {
                pump(link, new FrameSink() {
                    @Override
                    public void onFrame(String rawFrameJson) throws Exception {
                        emitter.send(SseEmitter.event().data(rawFrameJson));
                    }

                    @Override
                    public void onEnd() {
                        emitter.complete();
                    }
                });
            } catch (Exception e) {
                // 读超时/断流/客户端已断开：收流即可，由前端挂流恢复（不臆造终局帧）
                if (!(e instanceof IllegalStateException)) {
                    log.debug("上游流结束（{}）", e.getMessage());
                }
                try {
                    emitter.complete();
                } catch (Exception ignored) {
                    // 已收流
                }
            } finally {
                finished.set(true);
                heartbeat.cancel(false);
                link.close();
            }
        });
        return emitter;
    }

    /**
     * 帧泵（可测接缝）：逐帧交给 sink，收流时回调 {@link FrameSink#onEnd()}；
     * 读取/下发异常一律上抛由调用方收流，<b>本方法不做任何帧语义处理</b>。
     */
    void pump(RuntimeTurnLink link, FrameSink sink) throws Exception {
        String frame;
        while ((frame = link.nextFrame()) != null) {
            sink.onFrame(frame);
        }
        sink.onEnd();
    }

    /**
     * 帧落点（relay → 浏览器 的抽象：实现为 SseEmitter；单测用记录型实现）
     */
    public interface FrameSink {

        /**
         * 一帧原始 JSON（§5.7 形状，原样下发）
         */
        void onFrame(String rawFrameJson) throws Exception;

        /**
         * 上游正常收流（终局帧后关闭）
         */
        void onEnd();
    }

    @PreDestroy
    public void shutdown() {
        pumpPool.shutdownNow();
    }
}