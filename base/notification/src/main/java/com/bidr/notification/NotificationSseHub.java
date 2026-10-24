package com.bidr.notification;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import javax.annotation.PreDestroy;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * 站内信 SSE 在线注册表（M21；铃铛实时推送的广播口，替代轮询）。
 *
 * <p><b>注册</b>：{@code GET /api/notification/stream} 建连即按登录人挂 {@link SseEmitter}；
 * 发送失败/完成/超时自动摘除（侦测断线靠 15s 心跳 comment——nginx 已
 * {@code proxy_buffering off}，不心跳会被代理层吃掉连接）。</p>
 *
 * <p><b>广播</b>：{@code NotificationPublisher} 落库后按 target_user 推 {@code notification}
 * 事件（前端收到即刷新未读数，不携带内容——内容以 list 接口为准，避免双份语义）。</p>
 *
 * <p><b>多实例边界</b>：注册表在进程内存——当前部署形态（单容器）成立；上多实例时须把
 * 广播改走 Redis pub/sub（core/ai/llm 的 RTopic 先例），注册表仍各实例持有本机连接。</p>
 *
 * @author sharp (M21：站内信机制 2026-10-25)
 */
@Slf4j
@Component
public class NotificationSseHub {

    /** 心跳周期：nginx 默认 60s 斩空闲连接，15s comment 足够续命 */
    private static final long HEARTBEAT_SECONDS = 15;

    private final Map<String, CopyOnWriteArrayList<SseEmitter>> registry = new ConcurrentHashMap<>();
    private final ScheduledExecutorService heartbeat = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "notification-sse-heartbeat");
        t.setDaemon(true);
        return t;
    });

    public NotificationSseHub() {
        heartbeat.scheduleAtFixedRate(this::pingAll, HEARTBEAT_SECONDS, HEARTBEAT_SECONDS, TimeUnit.SECONDS);
    }

    @PreDestroy
    void shutdown() {
        heartbeat.shutdownNow();
        registry.values().forEach(list -> list.forEach(SseEmitter::complete));
        registry.clear();
    }

    /** 建连注册（立即发一条 connected 事件，前端据此确认通道并刷新一次未读） */
    public void register(String receiver, SseEmitter emitter) {
        CopyOnWriteArrayList<SseEmitter> list =
                registry.computeIfAbsent(receiver, k -> new CopyOnWriteArrayList<>());
        list.add(emitter);
        Runnable remove = () -> list.remove(emitter);
        emitter.onCompletion(remove);
        emitter.onTimeout(remove);
        emitter.onError(t -> remove.run());
        try {
            emitter.send(SseEmitter.event().name("connected").data("ok"));
        } catch (Exception e) {
            remove.run();
        }
    }

    /** 定向广播：该收件人的全部在线连接各推一条（失败连接摘除，不影响其余） */
    public void pushTo(String receiver, String eventName, Object data) {
        List<SseEmitter> list = registry.get(receiver);
        if (list == null || list.isEmpty()) {
            return;
        }
        for (SseEmitter emitter : list) {
            try {
                emitter.send(SseEmitter.event().name(eventName).data(data));
            } catch (Exception e) {
                list.remove(emitter);
            }
        }
    }

    /** 全员心跳 comment（SSE 规范 `:ping` 不触发前端事件，只保活） */
    private void pingAll() {
        registry.values().forEach(list -> list.forEach(emitter -> {
            try {
                emitter.send(SseEmitter.event().comment("ping"));
            } catch (Exception e) {
                list.remove(emitter);
            }
        }));
    }
}
