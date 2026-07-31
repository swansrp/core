package com.bidr.llm.sse;

import com.bidr.llm.store.StreamAnswerState;
import com.bidr.llm.store.StreamAnswerStore;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.model.StreamingResponseHandler;
import dev.langchain4j.model.chat.StreamingChatLanguageModel;
import dev.langchain4j.model.output.Response;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.MediaType;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.UUID;

/**
 * 流式聊天三种"拿数据"方式的示例接口（框架内置）。
 * <p>
 * <b>一句话判断法：</b>
 * <ul>
 *     <li>消费端能和生产端保持同一条连接？→ 能（浏览器直连你的服务）→ <b>方式一</b>，不需要 store；</li>
 *     <li>不能保持连接，读的人是自己前端？→ 是，且单实例部署 → <b>方式二</b> + 内存版 store；</li>
 *     <li>同上，但多实例部署？→ 是 → <b>方式二</b> + Redis 版 store；</li>
 *     <li>读的人是第三方服务器（回调）？→ 是 → <b>方式三</b>，基本必 Redis。</li>
 * </ul>
 * 三种方式对应端点：
 * <ul>
 *     <li><b>方式一 真 SSE 直推</b>：{@code GET/POST /web/api/llm/chat/sse}，连接保持，token 直接流过连接不落地，
 *     事件协议 delta/done/error 见 {@link SseStreamingResponseHandler}；</li>
 *     <li><b>方式二 轮询</b>：{@code POST /chat/poll/start} 发起生成拿 streamId，
 *     前端定时 {@code GET /chat/poll/{streamId}} 取当前状态，两次请求之间靠 {@link StreamAnswerStore} 存中间态；</li>
 *     <li><b>方式三 回调刷新</b>：{@code POST /chat/callback}，模拟三方服务器（如企微）带 streamId 来拉当前内容，
 *     生产端与方式二共用（写 store），差别只在"读的人是谁"。</li>
 * </ul>
 * 装配条件：类路径存在 langchain4j 与 spring-webmvc（排除了 langchain4j 的模块自动跳过），
 * 且未配置 {@code llm.chat-sse.enabled=false}。模型取应用中唯一的
 * {@link StreamingChatLanguageModel} Bean，缺失或不唯一时报错提示。
 * </p>
 *
 * @author Sharp
 */
@Slf4j
@RestController
@RequestMapping("/web/api/llm")
@ConditionalOnClass(name = {
        "dev.langchain4j.model.chat.StreamingChatLanguageModel",
        "org.springframework.web.servlet.mvc.method.annotation.SseEmitter"})
@ConditionalOnProperty(prefix = "llm.chat-sse", name = "enabled", havingValue = "true", matchIfMissing = true)
public class LlmChatDemoController {

    /**
     * 惰性解析流式模型：装配 Controller 时不强求模型 Bean 存在，调用时再取
     */
    private final ObjectProvider<StreamingChatLanguageModel> streamingModelProvider;

    /**
     * 流式回答状态存储：内存/Redis 按类路径自动二选一，方式二/三共用
     */
    private final ObjectProvider<StreamAnswerStore> streamAnswerStoreProvider;

    public LlmChatDemoController(ObjectProvider<StreamingChatLanguageModel> streamingModelProvider,
                                 ObjectProvider<StreamAnswerStore> streamAnswerStoreProvider) {
        this.streamingModelProvider = streamingModelProvider;
        this.streamAnswerStoreProvider = streamAnswerStoreProvider;
    }

    // ==================== 方式一：真 SSE 直推（推模式，不需要 store） ====================

    /**
     * GET 版：适配浏览器原生 EventSource（EventSource 只支持 GET）
     *
     * @param prompt 提示词
     * @return SSE 事件流
     */
    @GetMapping(value = "/chat/sse", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter chatByGet(@RequestParam("prompt") String prompt) {
        return startChatStream(prompt);
    }

    /**
     * POST 版：适配 fetch + ReadableStream，prompt 放请求体（长提示词/复杂参数场景）
     *
     * @param request 聊天请求
     * @return SSE 事件流
     */
    @PostMapping(value = "/chat/sse", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter chatByPost(@RequestBody ChatRequest request) {
        return startChatStream(request.getPrompt());
    }

    /**
     * 创建 SSE 连接并发起流式生成：token 回调经桥接器转为 SSE 事件推给前端，内容不落地
     */
    private SseEmitter startChatStream(String prompt) {
        // 超时设为 0 表示不限时，由模型侧的生成结束/失败来关闭连接
        SseEmitter emitter = new SseEmitter(0L);
        SseStreamingResponseHandler handler = new SseStreamingResponseHandler(emitter);
        try {
            requireStreamingModel().generate(prompt, handler);
        } catch (Exception e) {
            // 发起调用即失败（如模型 Bean 缺失、配置缺失）时，以 error 事件结束连接
            log.warn("SSE 聊天发起失败", e);
            handler.onError(e);
        }
        return emitter;
    }

    // ==================== 方式二：轮询（拉模式，前端定时来问，靠 store 存中间态） ====================

    /**
     * 发起生成：立即返回 streamId，token 回调持续写入 store，前端拿 streamId 去轮询
     *
     * @param request 聊天请求
     * @return streamId
     */
    @PostMapping(value = "/chat/poll/start", produces = MediaType.APPLICATION_JSON_VALUE)
    public String startPollChat(@RequestBody ChatRequest request) {
        String streamId = UUID.randomUUID().toString();
        StreamAnswerStore store = requireStore();
        // 先写一条空的运行中状态，避免前端首次轮询读到 null 误判为流不存在
        store.updateContent(streamId, "", false);
        // generate 立即返回，token 在回调线程中持续写 store（Redis 实现自带写入节流）
        requireStreamingModel().generate(request.getPrompt(), new StoreWritingHandler(store, streamId));
        return streamId;
    }

    /**
     * 前端轮询端点：每次都是全新请求，跨请求靠 store 拿到最新内容（单实例内存版够用，多实例必须 Redis 版）
     *
     * @param streamId 流标识
     * @return 当前状态快照（content + finish），流不存在/已过期时返回 null
     */
    @GetMapping(value = "/chat/poll/{streamId}", produces = MediaType.APPLICATION_JSON_VALUE)
    public StreamAnswerState pollChat(@PathVariable("streamId") String streamId) {
        return requireStore().getState(streamId);
    }

    // ==================== 方式三：回调刷新（三方拉模式，读的人是外部服务器） ====================

    /**
     * 模拟三方回调端点：外部平台（如企微 stream 刷新）带 streamId 来拉当前内容。
     * 生产端与方式二完全共用（{@code /chat/poll/start} 写 store）；
     * 与方式二的差别只在"读的人是谁"——三方回调经过负载均衡打到哪个节点不可控，所以基本必 Redis。
     *
     * @param request 回调请求（streamId 放请求体，贴近真实三方回调形态）
     * @return 当前状态快照，供组装三方应答报文
     */
    @PostMapping(value = "/chat/callback", produces = MediaType.APPLICATION_JSON_VALUE)
    public StreamAnswerState callbackChat(@RequestBody CallbackRequest request) {
        return requireStore().getState(request.getStreamId());
    }

    // ==================== 公共 ====================

    /**
     * 取应用中唯一的流式模型 Bean；有多个时业务应标注 @Primary 指定
     */
    private StreamingChatLanguageModel requireStreamingModel() {
        StreamingChatLanguageModel model = streamingModelProvider.getIfUnique();
        if (model == null) {
            throw new IllegalStateException("未找到唯一的 StreamingChatLanguageModel Bean，请装配模型或用 @Primary 指定");
        }
        return model;
    }

    /**
     * 取流式回答状态存储（引入 llm 依赖后内存/Redis 实现必有其一）
     */
    private StreamAnswerStore requireStore() {
        StreamAnswerStore store = streamAnswerStoreProvider.getIfUnique();
        if (store == null) {
            throw new IllegalStateException("未找到唯一的 StreamAnswerStore Bean");
        }
        return store;
    }

    /**
     * 方式二/三共用的生产端回调：把流式输出持续写入 store（覆盖式全量内容）
     */
    private static class StoreWritingHandler implements StreamingResponseHandler<AiMessage> {

        private final StreamAnswerStore store;
        private final String streamId;

        /**
         * 累积已生成的内容
         */
        private final StringBuilder builder = new StringBuilder();

        StoreWritingHandler(StreamAnswerStore store, String streamId) {
            this.store = store;
            this.streamId = streamId;
        }

        @Override
        public void onNext(String token) {
            if (token == null) {
                return;
            }
            synchronized (builder) {
                builder.append(token);
                // 中间态写入（Redis 实现按 min-write-interval-ms 节流，不丢内容）
                store.updateContent(streamId, builder.toString(), false);
            }
        }

        @Override
        public void onComplete(Response<AiMessage> response) {
            AiMessage message = response == null ? null : response.content();
            String finalText = message == null ? null : message.text();
            synchronized (builder) {
                // 终态写入永不被节流跳过
                store.updateContent(streamId, StringUtils.hasText(finalText) ? finalText : builder.toString(), true);
            }
        }

        @Override
        public void onError(Throwable error) {
            log.warn("轮询/回调模式流式生成失败: streamId={}", streamId, error);
            synchronized (builder) {
                // 以终态收口，消费端读到 finish=true 即停止轮询
                store.updateContent(streamId, builder.length() > 0 ? builder.toString() : "生成失败: " + error.getMessage(), true);
            }
        }
    }

    /**
     * 聊天请求体（方式一 POST 版、方式二发起端共用）
     */
    @Data
    public static class ChatRequest {
        /**
         * 提示词
         */
        private String prompt;
    }

    /**
     * 方式三回调请求体
     */
    @Data
    public static class CallbackRequest {
        /**
         * 流标识
         */
        private String streamId;
    }
}
