package com.bidr.llm.model;

import com.bidr.llm.provider.ModelConfigProvider;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.openai.OpenAiChatModel;
import dev.langchain4j.model.output.Response;
import lombok.extern.slf4j.Slf4j;

import java.net.Proxy;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

/**
 * 可刷新的聊天语言模型委托（支持按用户隔离缓存）。
 * <p>
 * 以 userId + 配置签名 + 用户级 apiKey 作为缓存键，配置或 Key 变化时自动重建底层模型，
 * 实现配置热刷新与用户隔离。用途（purpose）由构造时传入。
 * </p>
 *
 * @author Sharp
 */
@Slf4j
public class RefreshableChatModel implements ChatLanguageModel {

    /**
     * 用户级模型缓存上限，超出后整体清空重建，防止无淘汰机制导致内存膨胀
     */
    private static final int MAX_CACHE_SIZE = 256;

    private final ModelConfigProvider configProvider;
    private final String purposeType;
    private final int maxRetries;
    private final Proxy proxy;
    private final Supplier<Long> userIdSupplier;

    /**
     * 按用户缓存模型实例：key = userId_configSignature_apiKey, value = ChatLanguageModel
     */
    private final ConcurrentHashMap<String, ChatLanguageModel> userDelegates = new ConcurrentHashMap<>();

    /**
     * 当前线程使用的 delegate
     */
    private final ThreadLocal<ChatLanguageModel> currentDelegate = new ThreadLocal<>();

    public RefreshableChatModel(ModelConfigProvider configProvider,
                                String purposeType,
                                int maxRetries,
                                Proxy proxy,
                                Supplier<Long> userIdSupplier) {
        this.configProvider = configProvider;
        this.purposeType = purposeType;
        this.maxRetries = maxRetries;
        this.proxy = proxy;
        this.userIdSupplier = userIdSupplier;
    }

    @Override
    public Response<AiMessage> generate(List<ChatMessage> messages) {
        // 确保当前线程的 delegate 是最新的
        ensureFreshDelegate();
        return currentDelegate.get().generate(messages);
    }

    /**
     * 带工具清单的生成（function calling 入口）：接口默认实现抛 UnsupportedOperationException，
     * AiServices 工具循环经此委托到底层模型
     */
    @Override
    public Response<AiMessage> generate(List<ChatMessage> messages, List<ToolSpecification> toolSpecifications) {
        ensureFreshDelegate();
        return currentDelegate.get().generate(messages, toolSpecifications);
    }

    /**
     * 带单个强制工具的生成（function calling 入口），语义同 {@link #generate(List, List)}
     */
    @Override
    public Response<AiMessage> generate(List<ChatMessage> messages, ToolSpecification toolSpecification) {
        ensureFreshDelegate();
        return currentDelegate.get().generate(messages, toolSpecification);
    }

    /**
     * 执行消息生成请求，发送消息数组给 LLM 并获取 AI 响应
     *
     * @param messages 聊天消息数组，包含对话上下文和历史消息
     * @return 包含 AI 回复消息的响应对象
     */
    @Override
    public Response<AiMessage> generate(ChatMessage... messages) {
        ensureFreshDelegate();
        return currentDelegate.get().generate(messages);
    }

    /**
     * 执行单条用户消息生成请求，发送用户消息给 LLM 并获取文本响应
     *
     * @param userMessage 用户输入的文本消息
     * @return AI 回复的文本内容
     */
    @Override
    public String generate(String userMessage) {
        ensureFreshDelegate();
        return currentDelegate.get().generate(userMessage);
    }

    /**
     * 确保底层委托模型为最新配置，按用户隔离缓存
     */
    private void ensureFreshDelegate() {
        Long userId = userIdSupplier == null ? null : userIdSupplier.get();
        String configSignature = configProvider.getConfigSignatureWithoutKey(purposeType);
        String userKey = configProvider.getApiKey(purposeType, userId);
        String cacheKey = (userId != null ? userId : "default") + "_" + configSignature + "_" + userKey;

        String maskedKey = userKey != null && userKey.length() > 8
                ? userKey.substring(0, 4) + "..." + userKey.substring(userKey.length() - 4)
                : "****";
        log.info("[{}] ChatModel 使用 apiKey: userId={}, apiKey={}", purposeType, userId, maskedKey);

        ChatLanguageModel delegate = userDelegates.get(cacheKey);
        if (delegate == null) {
            log.info("[{}] 用户级模型缓存未命中，正在重建 ChatLanguageModel, userId={}", purposeType, userId);
            if (userDelegates.size() >= MAX_CACHE_SIZE) {
                log.warn("[{}] 用户级模型缓存超过上限 {}，整体清空重建", purposeType, MAX_CACHE_SIZE);
                userDelegates.clear();
            }
            delegate = buildDelegate(userKey);
            userDelegates.put(cacheKey, delegate);
        }
        currentDelegate.set(delegate);
    }

    /**
     * 使用指定 apiKey 构建 OpenAiChatModel 委托实例
     */
    private ChatLanguageModel buildDelegate(String apiKey) {
        return OpenAiChatModel.builder()
                .baseUrl(configProvider.getBaseUrl(purposeType))
                .apiKey(apiKey)
                .modelName(configProvider.getModelName(purposeType))
                .timeout(Duration.ofSeconds(configProvider.getTimeoutSeconds(purposeType)))
                .maxRetries(maxRetries)
                .proxy(proxy)
                .logRequests(false)
                .logResponses(false)
                .build();
    }
}
