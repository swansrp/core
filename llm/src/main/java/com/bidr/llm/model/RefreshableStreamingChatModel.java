package com.bidr.llm.model;

import com.bidr.llm.provider.ModelConfigProvider;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.model.StreamingResponseHandler;
import dev.langchain4j.model.chat.StreamingChatLanguageModel;
import dev.langchain4j.model.openai.OpenAiStreamingChatModel;
import lombok.extern.slf4j.Slf4j;

import java.net.Proxy;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

/**
 * 可刷新的 StreamingChatLanguageModel 代理（支持按用户隔离缓存）。
 * <p>
 * 以 userId + 配置签名 + 用户级 apiKey 作为缓存键，配置或 Key 变化时自动重建底层模型，
 * 实现配置热刷新与用户隔离。用途（purpose）由构造时传入。
 * </p>
 *
 * @author Sharp
 */
@Slf4j
public class RefreshableStreamingChatModel implements StreamingChatLanguageModel {

    /**
     * 用户级模型缓存上限，超出后整体清空重建，防止无淘汰机制导致内存膨胀
     */
    private static final int MAX_CACHE_SIZE = 256;

    private final ModelConfigProvider configProvider;
    private final String purposeType;
    private final Proxy proxy;
    private final Supplier<Long> userIdSupplier;
    /** SSE 原文日志开关（logRequests/logResponses）：长任务可观测性用，DEBUG 级输出含 reasoning_content 事件 */
    private final boolean verbose;

    /**
     * 按用户缓存模型实例：key = userId_configSignature_apiKey, value = StreamingChatLanguageModel
     */
    private final ConcurrentHashMap<String, StreamingChatLanguageModel> userDelegates = new ConcurrentHashMap<>();

    /**
     * 当前线程使用的 delegate
     */
    private final ThreadLocal<StreamingChatLanguageModel> currentDelegate = new ThreadLocal<>();

    public RefreshableStreamingChatModel(ModelConfigProvider configProvider,
                                         String purposeType,
                                         Proxy proxy,
                                         Supplier<Long> userIdSupplier) {
        this(configProvider, purposeType, proxy, userIdSupplier, false);
    }

    public RefreshableStreamingChatModel(ModelConfigProvider configProvider,
                                         String purposeType,
                                         Proxy proxy,
                                         Supplier<Long> userIdSupplier,
                                         boolean verbose) {
        this.configProvider = configProvider;
        this.purposeType = purposeType;
        this.proxy = proxy;
        this.userIdSupplier = userIdSupplier;
        this.verbose = verbose;
    }

    @Override
    public void generate(List<ChatMessage> messages, StreamingResponseHandler<AiMessage> handler) {
        // 确保底层代理模型为最新配置
        ensureFreshDelegate();
        currentDelegate.get().generate(messages, handler);
    }

    /**
     * 带工具清单的流式生成：接口默认实现抛 UnsupportedOperationException，补齐委托以支持工具调用型上游
     */
    @Override
    public void generate(List<ChatMessage> messages, List<ToolSpecification> toolSpecifications,
                         StreamingResponseHandler<AiMessage> handler) {
        ensureFreshDelegate();
        currentDelegate.get().generate(messages, toolSpecifications, handler);
    }

    /**
     * 带单个强制工具的流式生成，语义同 {@link #generate(List, List, StreamingResponseHandler)}
     */
    @Override
    public void generate(List<ChatMessage> messages, ToolSpecification toolSpecification,
                         StreamingResponseHandler<AiMessage> handler) {
        ensureFreshDelegate();
        currentDelegate.get().generate(messages, toolSpecification, handler);
    }

    // 检查配置签名是否变化，若变化则重新构建模型
    private void ensureFreshDelegate() {
        Long userId = userIdSupplier == null ? null : userIdSupplier.get();
        String configSignature = configProvider.getConfigSignatureWithoutKey(purposeType);
        String userKey = configProvider.getApiKey(purposeType, userId);
        String cacheKey = (userId != null ? userId : "default") + "_" + configSignature + "_" + userKey;

        String maskedKey = userKey != null && userKey.length() > 8
                ? userKey.substring(0, 4) + "..." + userKey.substring(userKey.length() - 4)
                : "****";
        log.info("[{}] StreamingChatModel 使用 apiKey: userId={}, apiKey={}", purposeType, userId, maskedKey);

        StreamingChatLanguageModel delegate = userDelegates.get(cacheKey);
        if (delegate == null) {
            log.info("[{}] 用户级流式模型缓存未命中，正在重建 StreamingChatLanguageModel, userId={}", purposeType, userId);
            if (userDelegates.size() >= MAX_CACHE_SIZE) {
                log.warn("[{}] 用户级流式模型缓存超过上限 {}，整体清空重建", purposeType, MAX_CACHE_SIZE);
                userDelegates.clear();
            }
            delegate = buildDelegate(userKey);
            userDelegates.put(cacheKey, delegate);
        }
        currentDelegate.set(delegate);
    }

    private StreamingChatLanguageModel buildDelegate(String apiKey) {
        return OpenAiStreamingChatModel.builder()
                .baseUrl(configProvider.getBaseUrl(purposeType))
                .apiKey(apiKey)
                .modelName(configProvider.getModelName(purposeType))
                .timeout(Duration.ofSeconds(configProvider.getTimeoutSeconds(purposeType)))
                .proxy(proxy)
                .logRequests(verbose)
                .logResponses(verbose)
                .build();
    }
}
