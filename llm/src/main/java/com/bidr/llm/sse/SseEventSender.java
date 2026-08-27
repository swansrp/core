package com.bidr.llm.sse;

import lombok.extern.slf4j.Slf4j;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Supplier;

/**
 * Title: SseEventSender
 * Description: 通用 SSE 事件发送器（不限 flow 链路，任何 SSE 端点可用）——封装 {@link SseEmitter}
 * 的写入与生命周期：
 * <ul>
 *     <li>send：单事件推送，内容含换行时逐行拆成多条 {@code data:} 行（SSE 协议空行即事件结束，客户端按 \n 拼回原文）；</li>
 *     <li>线程安全：方法级同步——心跳线程（startHeartbeat）与业务执行线程可安全并发写入；</li>
 *     <li>断连保护：客户端完成/超时/写入异常后置标记，后续写入静默跳过，心跳自动停跳；</li>
 *     <li>complete：关闭连接（连带停跳），重复关闭静默处理。</li>
 * </ul>
 * 七事件协议常量也定义在本类，流式结点（llm stream 等）与调用方共用：
 * conv（对话标识）→ delta*（token 增量）→ tick（活性心跳）→ spec（编排指令）→ msgid（消息标识）
 * → done（正文完成）→ error（失败）。
 * 前身 FlowSseSender（出身于 flow 管线时期，更名以正名通用定位）。
 *
 * @author Sharp
 * @since 2026/8/16
 */
@Slf4j
public class SseEventSender {

    /**
     * delta 事件：token 增量片段
     */
    public static final String EVENT_DELTA = "delta";

    /**
     * tick 事件：活性心跳（data 由心跳供给函数计算，如已耗时秒数）。长链路里业务线程可能长时间
     * 阻塞在无信号环节（如思考类模型首应答 token 前的网关读），期间 2s 一跳的 tick 是前端
     * 分辨「死了还是想着」的唯一活性证明——AI 接口禁裸转圈的框架机制
     */
    public static final String EVENT_TICK = "tick";

    /**
     * conv 事件：对话标识（流式链路发起时先于 delta 下发；新对话在此创建并回传，前端续问按它续接）
     */
    public static final String EVENT_CONV = "conv";

    /**
     * spec 事件：编排指令 JSON 全文（如 chart-spec 代码块，后端提取校验后下发）
     */
    public static final String EVENT_SPEC = "spec";

    /**
     * done 事件：剔除编排指令块后的正文（前端可直接渲染）
     */
    public static final String EVENT_DONE = "done";

    /**
     * msgid 事件：消息标识（链路收口补写业务记录后、done 之前下发；前端定位该条回复用）
     */
    public static final String EVENT_MSGID = "msgid";

    /**
     * error 事件：生成失败，携带错误信息
     */
    public static final String EVENT_ERROR = "error";

    private final SseEmitter emitter;

    /**
     * 客户端是否已断开（完成/超时/网络异常），断开后不再写入
     */
    private final AtomicBoolean clientGone = new AtomicBoolean(false);

    /**
     * 共享心跳调度器（daemon 单线程：发送轻量非阻塞，多连接串行复用足够；应用生命周期存活）
     */
    private static final ScheduledExecutorService HEARTBEAT_SCHEDULER =
            Executors.newSingleThreadScheduledExecutor(r -> {
                Thread t = new Thread(r, "flow-sse-heartbeat");
                t.setDaemon(true);
                return t;
            });

    /**
     * 当前连接的心跳句柄（startHeartbeat 启动，complete/断连停跳；单连接至多一个心跳）
     */
    private ScheduledFuture<?> heartbeatFuture;

    public SseEventSender(SseEmitter emitter) {
        this.emitter = emitter;
        // 注册客户端生命周期回调：断开后置标记停跳，停止后续写入
        emitter.onCompletion(() -> {
            clientGone.set(true);
            stopHeartbeat();
        });
        emitter.onTimeout(() -> {
            clientGone.set(true);
            stopHeartbeat();
        });
        emitter.onError(error -> {
            clientGone.set(true);
            stopHeartbeat();
        });
    }

    /**
     * 启动活性心跳：周期推送 tick 事件（data 由供给函数计算，如已耗时秒数），首跳延迟=周期。
     * 连接 complete/断开后自动停跳；返回句柄供调用方提前停跳（生成结束不需再跳时）。
     * 发送经 send 的同步保护，可与业务线程安全并发
     */
    public synchronized ScheduledFuture<?> startHeartbeat(long intervalSeconds, Supplier<String> dataSupplier) {
        stopHeartbeat();
        ScheduledFuture<?> future = HEARTBEAT_SCHEDULER.scheduleAtFixedRate(
                () -> send(EVENT_TICK, dataSupplier.get()), intervalSeconds, intervalSeconds, TimeUnit.SECONDS);
        heartbeatFuture = future;
        return future;
    }

    /**
     * 停跳当前心跳（无心跳时空操作；重复取消静默）
     */
    private synchronized void stopHeartbeat() {
        ScheduledFuture<?> future = heartbeatFuture;
        if (future != null) {
            future.cancel(false);
            heartbeatFuture = null;
        }
    }

    /**
     * 发送单个 SSE 事件（线程安全：与心跳/其他执行线程串行化），客户端已断开时静默跳过
     */
    public synchronized void send(String eventName, String data) {
        if (clientGone.get()) {
            return;
        }
        try {
            SseEmitter.SseEventBuilder event = SseEmitter.event().name(eventName);
            String content = data == null ? "" : data;
            // -1 保留末尾空行，确保拼回后与原文一致
            for (String line : content.split("\n", -1)) {
                event.data(line);
            }
            emitter.send(event);
        } catch (Exception e) {
            // 写入失败通常意味着客户端已断开，置标记停止后续写入
            clientGone.set(true);
            log.debug("SSE 写入失败，客户端可能已断开: {}", e.getMessage());
        }
    }

    /**
     * 关闭 SSE 连接（连带停跳），重复关闭时静默处理
     */
    public synchronized void complete() {
        stopHeartbeat();
        if (clientGone.compareAndSet(false, true)) {
            try {
                emitter.complete();
            } catch (Exception e) {
                log.debug("SSE 关闭连接异常: {}", e.getMessage());
            }
        }
    }

    /**
     * 客户端是否已断开（执行器可据此提前终止后续结点）
     */
    public boolean isClientGone() {
        return clientGone.get();
    }
}
