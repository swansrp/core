package com.bidr.llm.sse;

import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.model.StreamingResponseHandler;
import dev.langchain4j.model.output.Response;
import lombok.extern.slf4j.Slf4j;
import org.springframework.util.StringUtils;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

/**
 * langchain4j 流式回调到 Spring {@link SseEmitter} 的桥接器。
 * <p>
 * 将模型的流式输出转换为标准 SSE 事件推送给前端：
 * <ul>
 *     <li>{@code delta} 事件：每个 token 增量片段；</li>
 *     <li>{@code done} 事件：生成结束，携带完整内容；</li>
 *     <li>{@code error} 事件：生成失败，携带错误信息（发送后正常关闭连接，便于前端展示）。</li>
 * </ul>
 * 客户端断开（完成/超时/网络异常）后自动停止写入，不影响模型侧回调线程。
 * </p>
 * <p>
 * 典型用法（Controller 中）：
 * <pre>{@code
 * SseEmitter emitter = new SseEmitter(0L);
 * streamingModel.generate(prompt, new SseStreamingResponseHandler(emitter));
 * return emitter;
 * }</pre>
 * </p>
 *
 * @author Sharp
 */
@Slf4j
public class SseStreamingResponseHandler implements StreamingResponseHandler<AiMessage> {

    /**
     * token 增量事件名
     */
    public static final String EVENT_DELTA = "delta";

    /**
     * 生成完成事件名
     */
    public static final String EVENT_DONE = "done";

    /**
     * 生成失败事件名
     */
    public static final String EVENT_ERROR = "error";

    private final SseEmitter emitter;

    /**
     * 生成完成回调（可选），携带完整内容，供业务方落库等后续处理
     */
    private final Consumer<String> completionCallback;

    /**
     * 客户端是否已断开（完成/超时/网络异常），断开后不再写入
     */
    private final AtomicBoolean clientGone = new AtomicBoolean(false);

    /**
     * 累积已生成的内容，onComplete 无完整文本时兜底使用
     */
    private final StringBuilder builder = new StringBuilder();

    public SseStreamingResponseHandler(SseEmitter emitter) {
        this(emitter, null);
    }

    public SseStreamingResponseHandler(SseEmitter emitter, Consumer<String> completionCallback) {
        this.emitter = emitter;
        this.completionCallback = completionCallback;
        // 注册客户端生命周期回调：断开后置标记，停止后续写入
        emitter.onCompletion(() -> clientGone.set(true));
        emitter.onTimeout(() -> clientGone.set(true));
        emitter.onError(error -> clientGone.set(true));
    }

    @Override
    public void onNext(String token) {
        if (token == null || clientGone.get()) {
            return;
        }
        synchronized (builder) {
            builder.append(token);
        }
        // 推送增量片段，前端按序拼接即可
        send(EVENT_DELTA, token);
    }

    @Override
    public void onComplete(Response<AiMessage> response) {
        // 优先取模型返回的完整文本，缺失时用累积内容兜底
        AiMessage message = response == null ? null : response.content();
        String finalText = message == null ? null : message.text();
        String fullContent;
        synchronized (builder) {
            fullContent = StringUtils.hasText(finalText) ? finalText : builder.toString();
        }
        send(EVENT_DONE, fullContent);
        complete();
        // 业务回调放在连接关闭之后，回调异常不影响前端收流
        if (completionCallback != null) {
            try {
                completionCallback.accept(fullContent);
            } catch (Exception e) {
                log.warn("SSE 完成回调执行异常", e);
            }
        }
    }

    @Override
    public void onError(Throwable error) {
        log.warn("SSE 流式生成失败", error);
        // 发送 error 事件后正常关闭，前端可展示错误信息（completeWithError 会触发容器错误页，不友好）
        send(EVENT_ERROR, error == null ? "生成失败" : String.valueOf(error.getMessage()));
        complete();
    }

    /**
     * 发送单个 SSE 事件，客户端已断开时静默跳过
     */
    private void send(String eventName, String data) {
        if (clientGone.get()) {
            return;
        }
        try {
            emitter.send(SseEmitter.event().name(eventName).data(data == null ? "" : data));
        } catch (Exception e) {
            // 写入失败通常意味着客户端已断开，置标记停止后续写入
            clientGone.set(true);
            log.debug("SSE 写入失败，客户端可能已断开: {}", e.getMessage());
        }
    }

    /**
     * 关闭 SSE 连接，重复关闭时静默处理
     */
    private void complete() {
        if (clientGone.compareAndSet(false, true)) {
            try {
                emitter.complete();
            } catch (Exception e) {
                log.debug("SSE 关闭连接异常: {}", e.getMessage());
            }
        }
    }
}
