package com.bidr.llm.sse;

import lombok.extern.slf4j.Slf4j;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Title: FlowSseSender
 * Description: 流程链路 SSE 事件发送器——封装 {@link SseEmitter} 的写入与生命周期：
 * <ul>
 *     <li>send：单事件推送，内容含换行时逐行拆成多条 {@code data:} 行（SSE 协议空行即事件结束，客户端按 \n 拼回原文）；</li>
 *     <li>断连保护：客户端完成/超时/写入异常后置标记，后续写入静默跳过；</li>
 *     <li>complete：关闭连接，重复关闭静默处理。</li>
 * </ul>
 * 六事件协议常量也定义在本类，流式结点（llm stream 等）与调用方共用：
 * conv（对话标识）→ delta*（token 增量）→ spec（编排指令）→ msgid（消息标识）→ done（正文完成）→ error（失败）。
 *
 * @author Sharp
 * @since 2026/8/16
 */
@Slf4j
public class FlowSseSender {

    /**
     * delta 事件：token 增量片段
     */
    public static final String EVENT_DELTA = "delta";

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

    public FlowSseSender(SseEmitter emitter) {
        this.emitter = emitter;
        // 注册客户端生命周期回调：断开后置标记，停止后续写入
        emitter.onCompletion(() -> clientGone.set(true));
        emitter.onTimeout(() -> clientGone.set(true));
        emitter.onError(error -> clientGone.set(true));
    }

    /**
     * 发送单个 SSE 事件，客户端已断开时静默跳过
     */
    public void send(String eventName, String data) {
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
     * 关闭 SSE 连接，重复关闭时静默处理
     */
    public void complete() {
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
