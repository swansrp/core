package com.bidr.llm.config;

import com.bidr.document.DocumentAutoConfiguration;
import com.bidr.document.VisionConfigProvider;
import com.bidr.document.VisionTranscriber;
import com.bidr.llm.document.LlmVisionConfigProvider;
import com.bidr.llm.document.LlmVisionTranscriber;
import com.bidr.llm.model.LiveModelFactory;
import com.bidr.llm.model.RefreshableChatModel;
import com.bidr.llm.model.RefreshableStreamingChatModel;
import com.bidr.llm.provider.DbAwareModelConfigProvider;
import com.bidr.llm.provider.ModelConfigProvider;
import com.bidr.platform.service.cache.SysConfigCacheService;
import dev.langchain4j.http.client.jdk.JdkHttpClient;
import dev.langchain4j.http.client.jdk.JdkHttpClientBuilder;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.StreamingChatModel;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigureBefore;
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
 * <li>{@link ChatModel} / {@link StreamingChatModel}：基于上述
 * Provider 构建的
 * 可热刷新模型，业务未自定义同类型 Bean 时装配（langchain4j 1.23 起接口由
 * {@code ChatLanguageModel}/{@code StreamingChatLanguageModel} 更名而来）。</li>
 * </ul>
 * 默认不做用户隔离（{@code userIdSupplier = () -> null}），因为 core 层不依赖 authorization；
 * 业务如需按用户区分 Key，自行注册 {@link ModelConfigProvider} 或模型 Bean 覆盖即可。
 * 另装配 {@link LiveModelFactory}：流式进度模型（自建 SSE 客户端+同步回落）装配工厂，
 * 长链路业务（问数/资产生成/AI 补全等）经它统一取流式模型，代理/重试口径随 Bean 固化。
 * <p>
 * 🟢 <b>依赖方向（2026-11-05 SPI 反转后）</b>：llm → document 单向——本装配类为
 * {@code tool/document} 的 {@code VisionConfigProvider} SPI 注册默认适配器（桥接 sys_config/
 * {@code llm.vision.*}），document 已零 llm 编译依赖、可独立以 Java 8 工具链构建。
 * 文件 → Markdown 入口（{@code FileMarkdownService}）仍在 {@code tool/document}；
 * 多模态端点配置仍由本包 {@link ModelConfigProvider}（purpose=VISION）供给。
 * </p>
 * <p>
 * 🟢 <b>C′ 迁移（2026-11-07，lc4j 触点归边）</b>：langchain4j 1.x 全家 Java17 字节码，
 * document 模块留 JDK8 必须 zero-lc4j——多模态转录真实现 {@link LlmVisionTranscriber}
 * 的 lc4j 触点迁入本模块装配；本类整体
 * {@code @AutoConfigureBefore(DocumentAutoConfiguration)} 保证真身先于 document 占位评估。
 * read_file 工具 {@code FileMarkdownTool} 的默认 Bean 依赖 document 装配的
 * {@code FileMarkdownService}，住 {@link LlmDocumentToolsAutoConfiguration}
 * （{@code @AutoConfigureAfter(DocumentAutoConfiguration)}，本类之后评估）。
 * </p>
 *
 * @author Sharp
 */
@Slf4j
@AutoConfiguration
@AutoConfigureBefore(DocumentAutoConfiguration.class)
@ConditionalOnClass(name = {
        "dev.langchain4j.model.chat.ChatModel",
        "dev.langchain4j.model.chat.StreamingChatModel",
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
    @Value("${llm.vision.base-url:}")
    private String visionBaseUrl;
    @Value("${llm.vision.api-key:}")
    private String visionApiKey;
    @Value("${llm.vision.model-name:}")
    private String visionModelName;
    @Value("${llm.vision.timeout-seconds:180}")
    private long visionTimeoutSeconds;
    @Value("${llm.agent.timeout-seconds:600}")
    private long agentTimeoutSeconds;
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
        return new DbAwareModelConfigProvider(baseUrl, apiKey, modelName, timeoutSeconds,
                visionBaseUrl, visionApiKey, visionModelName, visionTimeoutSeconds,
                agentTimeoutSeconds, sysConfigProvider);
    }

    /**
     * 默认同步模型：存在 {@link ModelConfigProvider} 且业务未自定义 {@link ChatModel} 时生效
     */
    @Bean
    @ConditionalOnBean(ModelConfigProvider.class)
    @ConditionalOnMissingBean(ChatModel.class)
    public ChatModel defaultChatModel(ModelConfigProvider provider) {
        return new RefreshableChatModel(provider, PURPOSE_DEFAULT, maxAttempts, buildProxy(), () -> null);
    }

    /**
     * 默认流式模型：存在 {@link ModelConfigProvider} 且业务未自定义
     * {@link StreamingChatModel} 时生效
     */
    @Bean
    @ConditionalOnBean(ModelConfigProvider.class)
    @ConditionalOnMissingBean(StreamingChatModel.class)
    public StreamingChatModel defaultStreamingChatModel(ModelConfigProvider provider) {
        return new RefreshableStreamingChatModel(provider, PURPOSE_DEFAULT, buildProxy(), () -> null);
    }

    /**
     * 流式进度模型工厂：「自建 SSE 流式客户端 + 同步回落」双通道模型的统一装配点
     * （原先散落在问数维护/资产生成两处业务服务的同构 buildLiveModel 归一）；
     * 代理与重试口径随 Bean 构造固化（llm.proxy.* / llm.chat.max-attempts 同源），
     * Provider 缺失时由调用方懒回落同步模型（无流式进度）。
     * live 帧率经 {@code llm.agent-session.live-frame-millis} 注入（缺省 300ms＝
     * agent 会话 SSE 推送通道口径，M18 棒2；工厂 build 内 clamp [200,500]）
     */
    @Bean
    @ConditionalOnMissingBean
    public LiveModelFactory liveModelFactory(ObjectProvider<ModelConfigProvider> configProvider,
            @Value("${llm.agent-session.live-frame-millis:300}") long liveFrameMillis) {
        return new LiveModelFactory(configProvider.getIfAvailable(),
                proxyEnable, proxyHost, proxyPort, maxAttempts, liveFrameMillis);
    }

    /**
     * document 视觉配置默认适配器：桥接 {@code tool/document} 的 {@code VisionConfigProvider} SPI
     * （purpose=VISION：数据库系统参数优先、{@code llm.vision.*} yaml 回落），容器无
     * {@code ModelConfigProvider} 时解析返回 null＝多模态只能调用时显式传入，不抛。
     * 业务注册自己的 {@code VisionConfigProvider} Bean 即覆盖（本装配让位）。
     */
    @Bean
    @ConditionalOnMissingBean(VisionConfigProvider.class)
    public VisionConfigProvider llmVisionConfigProvider(ObjectProvider<ModelConfigProvider> configProvider) {
        return new LlmVisionConfigProvider(configProvider.getIfAvailable(), buildProxy());
    }

    /**
     * 多模态转录口<b>真实现</b>（C′ 迁移 2026-11-07，原 {@code tool/document} 内置视觉腿）：
     * 扫描页/图片交 OpenAI 协议多模态模型转录。lc4j 1.x 全家 Java17 字节码，实现体只能住
     * 本模块（17 字节码）；document 侧零 lc4j 依赖、只留 {@code NoopVisionTranscriber} 占位。
     * 🔴 装配顺序＝本自动配置整体 {@code @AutoConfigureBefore(DocumentAutoConfiguration)}：
     * 本 Bean 先评估先注册，document 占位的 {@code @ConditionalOnMissingBean(VisionTranscriber.class)}
     * 见真身即让位——顺序反了占位会抢注、视觉腿静默变指引异常。业务注册自己的
     * {@code VisionTranscriber} Bean（先于全部自动配置）同样让位本 Bean。
     */
    @Bean
    @ConditionalOnMissingBean(VisionTranscriber.class)
    public VisionTranscriber llmVisionTranscriber(ObjectProvider<VisionConfigProvider> configProvider) {
        return new LlmVisionTranscriber(configProvider.getIfUnique());
    }

    /**
     * 构建大模型调用的 HTTP 代理（未启用或配置不全时返回 null）：java.net.Proxy 形态，
     * 供自建 OkHttp 客户端（RawSse/RawSync）与 document 视觉腿（{@link LlmVisionConfigProvider}）
     * 复用同口径 {@code llm.proxy.enable/host/port} 配置；静态版供业务自建模型实例（如 Agent
     * 长任务用途流式模型）复用
     */
    public static Proxy buildProxy(boolean proxyEnable, String proxyHost, int proxyPort) {
        if (!proxyEnable || !StringUtils.hasText(proxyHost) || proxyPort <= 0) {
            return null;
        }
        return new Proxy(Proxy.Type.HTTP, new InetSocketAddress(proxyHost, proxyPort));
    }

    /**
     * java.net.Proxy → langchain4j 1.23 OpenAi 模型代理腿：1.23 起
     * {@code OpenAiChatModel.Builder.proxy(Proxy)} 不复存在，代理经 JDK HttpClient 的
     * {@code ProxySelector} 挂到 {@link JdkHttpClientBuilder} 上（以 javap 实测方法面为准：
     * {@code JdkHttpClientBuilder.httpClientBuilder(java.net.http.HttpClient.Builder)}）。
     * null/非 HTTP 型/无地址时返回 null＝不设置代理，与无代理配置时的 0.33 行为完全一致
     */
    public static JdkHttpClientBuilder buildLc4jHttpClientBuilder(Proxy proxy) {
        if (proxy == null || proxy.type() != Proxy.Type.HTTP || !(proxy.address() instanceof InetSocketAddress)) {
            return null;
        }
        return JdkHttpClient.builder()
                .httpClientBuilder(java.net.http.HttpClient.newBuilder()
                        .proxy(java.net.ProxySelector.of((InetSocketAddress) proxy.address())));
    }

    private Proxy buildProxy() {
        return buildProxy(proxyEnable, proxyHost, proxyPort);
    }
}
