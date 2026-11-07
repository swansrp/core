package com.bidr.llm.model;

import com.bidr.llm.config.LlmDefaultAutoConfiguration;
import com.bidr.llm.provider.ModelConfigProvider;
import dev.langchain4j.http.client.jdk.JdkHttpClientBuilder;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.openai.OpenAiChatModel;
import lombok.extern.slf4j.Slf4j;

import java.net.Proxy;
import java.time.Duration;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

/**
 * 可刷新的聊天语言模型委托（支持按用户隔离缓存）。
 * <p>
 * 以 userId + 配置签名 + 用户级 apiKey 作为缓存键，配置或 Key 变化时自动重建底层模型，
 * 实现配置热刷新与用户隔离。用途（purpose）由构造时传入。
 * </p>
 * <p>
 * langchain4j 1.23 适配：0.33 的 5 个 generate 重载收敛为 {@link #doChat(ChatRequest)} 单方法
 * （消息/工具清单随 ChatRequest 携带，chat(String)/chat(ChatMessage...) 走接口 default 最终路由至此）。
 * </p>
 *
 * @author Sharp
 */
@Slf4j
public class RefreshableChatModel implements ChatModel {

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
     * 按用户缓存模型实例：key = userId_configSignature_apiKey, value = ChatModel
     */
    private final ConcurrentHashMap<String, ChatModel> userDelegates = new ConcurrentHashMap<>();

    /**
     * 当前线程使用的 delegate
     */
    private final ThreadLocal<ChatModel> currentDelegate = new ThreadLocal<>();

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

    /**
     * 执行消息生成请求：消息数组与工具清单（function calling）均随 {@link ChatRequest} 携带，
     * 经此委托到底层模型（接口方法全为 default，显式 @Override 防漏写静默失效）
     */
    @Override
    public ChatResponse doChat(ChatRequest request) {
        // 确保当前线程的 delegate 是最新的
        ensureFreshDelegate();
        return currentDelegate.get().chat(request);
    }

    /**
     * 执行单条用户消息生成请求，发送用户消息给 LLM 并获取文本响应
     *
     * @param userMessage 用户输入的文本消息
     * @return AI 回复的文本内容
     */
    public String generate(String userMessage) {
        ensureFreshDelegate();
        return currentDelegate.get().chat(userMessage);
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

        ChatModel delegate = userDelegates.get(cacheKey);
        if (delegate == null) {
            log.info("[{}] 用户级模型缓存未命中，正在重建 ChatModel, userId={}", purposeType, userId);
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
     * 使用指定 apiKey 构建 OpenAiChatModel 委托实例。
     * 1.23 起 builder 无 {@code proxy(Proxy)} 方法：代理腿改经 JDK HttpClient
     * （{@link JdkHttpClientBuilder} + ProxySelector），与 {@link LlmDefaultAutoConfiguration#buildProxy}
     * 同源配置；无代理时行为与 0.33 完全一致（不设置 httpClientBuilder）
     */
    private ChatModel buildDelegate(String apiKey) {
        OpenAiChatModel.OpenAiChatModelBuilder builder = OpenAiChatModel.builder()
                .baseUrl(configProvider.getBaseUrl(purposeType))
                .apiKey(apiKey)
                .modelName(configProvider.getModelName(purposeType))
                .timeout(Duration.ofSeconds(configProvider.getTimeoutSeconds(purposeType)))
                .maxRetries(maxRetries)
                .logRequests(false)
                .logResponses(false);
        JdkHttpClientBuilder httpClient = LlmDefaultAutoConfiguration.buildLc4jHttpClientBuilder(proxy);
        if (httpClient != null) {
            builder.httpClientBuilder(httpClient);
        }
        return builder.build();
    }
}
