package com.bidr.agent.capability.health;

import lombok.extern.slf4j.Slf4j;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import javax.annotation.PreDestroy;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Title: AgentHealthSseHub
 * Description: Agent 能力探活健康度的 <b>SSE 广播注册表</b>（照站内信
 * {@code NotificationSseHub} 模式的 broadcast 简化版）——去 receiver 维度：
 * 探活快照对全员广播（健康度无收件人语义），前端徽标实时亮/灰不靠刷新：
 * <ul>
 *     <li><b>注册</b>：{@code GET /web/api/agent/health/stream} 建连即挂 {@link SseEmitter}
 *     （单全局集合，连接数由前端单订阅保证）；立即发一条 {@code connected} 事件
 *     （data=ok，前端据此确认通道），发送失败/完成/超时/异常自动摘除——
 *     侦测断线靠 15s 心跳 comment（nginx {@code proxy_buffering off} 形态下，
 *     不心跳会被代理层吃掉连接）；</li>
 *     <li><b>广播</b>：探活件 {@code probeOnce()} 状态变化时
 *     {@code broadcast("health", lastSnapshot)}（Map 经 {@code SseEmitter.data(Object)}
 *     Jackson 序列化为 JSON）——逐连接 send，<b>发送失败的连接摘除</b>（complete＋remove），
 *     不影响其余订阅者；</li>
 *     <li><b>多实例边界</b>：注册表在进程内存——当前部署形态（单容器）成立；上多实例时
 *     须把广播改走 Redis pub/sub（llm 会话流 StreamBus 先例），注册表仍各实例持有本机连接。</li>
 * </ul>
 * 装配：{@code AgentCapabilityAutoConfiguration} 的 {@code @Bean}（宿主自建同类型 Bean 让位），
 * 非扫描件（区别于站内信 hub 的 {@code @Component}）。
 *
 * @author Sharp
 * @since 2026/11/14
 */
@Slf4j
public class AgentHealthSseHub {

    /** 心跳周期：nginx 默认 60s 斩空闲连接，15s comment 足够续命（照站内信 hub 口径） */
    private static final long HEARTBEAT_SECONDS = 15;

    private final CopyOnWriteArrayList<SseEmitter> subscribers = new CopyOnWriteArrayList<>();

    private final ScheduledExecutorService heartbeat = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "agent-health-sse-heartbeat");
        t.setDaemon(true);
        return t;
    });

    public AgentHealthSseHub() {
        heartbeat.scheduleAtFixedRate(this::pingAll, HEARTBEAT_SECONDS, HEARTBEAT_SECONDS, TimeUnit.SECONDS);
    }

    /** 销毁收口：心跳停摆＋全部连接收尾（@Bean 销毁推断，照站内信 hub） */
    @PreDestroy
    void shutdown() {
        heartbeat.shutdownNow();
        subscribers.forEach(SseEmitter::complete);
        subscribers.clear();
    }

    /**
     * 建连注册：挂全局集合＋挂完成/超时/异常自摘钩，立即发一条 connected 事件
     * （前端据此确认通道就绪）；首帧发送失败＝连接已断，摘除不占位。
     */
    public void register(SseEmitter emitter) {
        subscribers.add(emitter);
        Runnable remove = () -> subscribers.remove(emitter);
        emitter.onCompletion(remove);
        emitter.onTimeout(remove);
        emitter.onError(t -> remove.run());
        try {
            emitter.send(SseEmitter.event().name("connected").data("ok"));
        } catch (Exception e) {
            remove.run();
        }
    }

    /**
     * 全员广播（事件名＋载荷）：逐连接 send（Map 载荷由 SseEmitter.data(Object) Jackson
     * 序列化），发送失败的连接主动收尾＋摘除，不影响其余订阅者。
     */
    public void broadcast(String eventName, Object data) {
        for (SseEmitter emitter : subscribers) {
            try {
                emitter.send(SseEmitter.event().name(eventName).data(data));
            } catch (Exception e) {
                removeFailed(emitter);
            }
        }
    }

    /** 全员心跳 comment（SSE 规范 {@code :ping} 不触发前端事件，只保活）；失败连接摘除 */
    void pingAll() {
        for (SseEmitter emitter : subscribers) {
            try {
                emitter.send(SseEmitter.event().comment("ping"));
            } catch (Exception e) {
                removeFailed(emitter);
            }
        }
    }

    /** 失败连接收口：complete 可能同样抛（连接已断），吞掉保证摘除必达 */
    private void removeFailed(SseEmitter emitter) {
        if (subscribers.remove(emitter)) {
            try {
                emitter.complete();
            } catch (Exception ignore) {
                // 已断开，收尾失败不必记
            }
        }
    }

    /** 心跳调度器在场口（测试口；shutdown 后 isShutdown=true，照探活件 scheduler() 口径） */
    ScheduledExecutorService heartbeat() {
        return heartbeat;
    }
}
