package com.bidr.llm.config;

import com.bidr.llm.model.RefreshableChatModel;
import com.bidr.llm.model.RefreshableStreamingChatModel;
import com.bidr.llm.provider.DbAwareModelConfigProvider;
import com.bidr.llm.provider.ModelConfigProvider;
import com.bidr.platform.service.cache.SysConfigCacheService;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.chat.StreamingChatLanguageModel;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.util.StringUtils;

import java.net.InetSocketAddress;
import java.net.Proxy;

/**
 * 大模型默认装配（开箱即用，业务可覆盖）。
 * <p>
 * 以 Spring Boot 自动配置形式注册（见
 * {@code META-INF/spring/...AutoConfiguration.imports}），
 * 在用户 Bean 之后处理，配合 {@link ConditionalOnMissingBean} 保证"业务自定义即覆盖默认"的顺序可靠。
 * 三个默认 Bean：
 * <ul>
 * <li>{@link ModelConfigProvider}：{@code llm.*} yaml 与数据库系统参数（{@code LlmParam}，
 * sys_config）合一读取——数据库有效值优先、yaml 回落，业务未自定义 Provider 时装配；
 * 密钥不再要求配置于 yaml（数据库或 yaml 任一有有效值即可，见
 * {@link DbAwareModelConfigProvider}）；</li>
 * <li>{@link ChatLanguageModel} / {@link StreamingChatLanguageModel}：基于上述
 * Provider 构建的
 * 可热刷新模型，业务未自定义同类型 Bean 时装配。</li>
 * </ul>
 * 默认不做用户隔离（{@code userIdSupplier = () -> null}），因为 core 层不依赖 authorization；
 * 业务如需按用户区分 Key，自行注册 {@link ModelConfigProvider} 或模型 Bean 覆盖即可。
 * </p>
 *
 * @author Sharp
 */
@Slf4j
@AutoConfiguration
@ConditionalOnClass(name = {
        "dev.langchain4j.model.chat.StreamingChatLanguageModel",
        "dev.langchain4j.model.openai.OpenAiChatModel" })
public class LlmDefaultAutoConfiguration {

    /**
     * 默认用途标识：单一模型配置，不按用途区分
     */
    private static final String PURPOSE_DEFAULT = "DEFAULT";

    @Value("${llm.base-url:}")
    private String baseUrl;
    @Value("${llm.api-key:}")
    private String apiKey;
    @Value("${llm.model-name:}")
    private String modelName;
    @Value("${llm.timeout-seconds:120}")
    private long timeoutSeconds;
    @Value("${llm.chat.max-attempts:1}")
    private int maxAttempts;
    @Value("${llm.proxy.enable:false}")
    private boolean proxyEnable;
    @Value("${llm.proxy.host:}")
    private String proxyHost;
    @Value("${llm.proxy.port:0}")
    private int proxyPort;

    /**
     * 默认模型配置提供者：数据库系统参数（{@code LlmParam}）有效值优先，回落 {@code llm.*} yaml；
     * 未配置密钥不阻断装配，首次调用时由 {@link DbAwareModelConfigProvider} 抛出操作指引
     */
    @Bean
    @ConditionalOnMissingBean(ModelConfigProvider.class)
    public ModelConfigProvider defaultModelConfigProvider(ObjectProvider<SysConfigCacheService> sysConfigProvider) {
        log.info("装配默认 ModelConfigProvider（数据库系统参数优先，回落 llm.* yaml；yaml baseUrl={}, modelName={}），业务未自定义时生效",
                baseUrl, modelName);
        return new DbAwareModelConfigProvider(baseUrl, apiKey, modelName, timeoutSeconds, sysConfigProvider);
    }

    /**
     * 默认同步模型：存在 {@link ModelConfigProvider} 且业务未自定义 {@link ChatLanguageModel} 时生效
     */
    @Bean
    @ConditionalOnBean(ModelConfigProvider.class)
    @ConditionalOnMissingBean(ChatLanguageModel.class)
    public ChatLanguageModel defaultChatLanguageModel(ModelConfigProvider provider) {
        return new RefreshableChatModel(provider, PURPOSE_DEFAULT, maxAttempts, buildProxy(), () -> null);
    }

    /**
     * 默认流式模型：存在 {@link ModelConfigProvider} 且业务未自定义
     * {@link StreamingChatLanguageModel} 时生效
     */
    @Bean
    @ConditionalOnBean(ModelConfigProvider.class)
    @ConditionalOnMissingBean(StreamingChatLanguageModel.class)
    public StreamingChatLanguageModel defaultStreamingChatLanguageModel(ModelConfigProvider provider) {
        return new RefreshableStreamingChatModel(provider, PURPOSE_DEFAULT, buildProxy(), () -> null);
    }

    /**
     * 构建大模型调用的 HTTP 代理（未启用或配置不全时返回 null）
     */
    private Proxy buildProxy() {
        if (!proxyEnable || !StringUtils.hasText(proxyHost) || proxyPort <= 0) {
            return null;
        }
        return new Proxy(Proxy.Type.HTTP, new InetSocketAddress(proxyHost, proxyPort));
    }
}
