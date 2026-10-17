package com.bidr.llm.model;

import com.bidr.llm.config.LlmDefaultAutoConfiguration;
import com.bidr.llm.provider.ModelConfigProvider;
import dev.langchain4j.model.chat.ChatLanguageModel;

import java.net.Proxy;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * Title: LiveModelFactory
 * Description: 流式进度模型装配工厂（llm 框架 Bean，经 {@link LlmDefaultAutoConfiguration} 装配）——
 * 「自建 SSE 流式客户端 + 同步回落」双通道模型的统一装配点，由原先散落在问数维护 / 资产生成
 * 两处业务服务的同构 buildLiveModel 归一而来（机制与配置一起进框架，业务侧只注入 live 回调）：
 * <ul>
 *     <li>Provider 存在：装配 {@link StreamingProgressChatModel}——流式帧经 live 回调推送；
 *     网关不支持 stream/stream+tools 时首应答 token 前失败自动降级同步回落而非整链报错，
 *     同步路径失败抛网关真实 HTTP 错误（两端都不静默）；</li>
 *     <li>Provider 缺失：懒加载调用方回落模型（无流式进度）。回落经 Supplier 保懒求值——
 *     回落侧可能带「未配置模型即抛」语义，不能在 Provider 存在时被提前触发。</li>
 * </ul>
 * 代理/重试次数等装配口径随 Bean 构造固化（llm.proxy.* / llm.chat.max-attempts 同源），
 * 用途（独立超时/密钥口径）由每次 build 传入；AGENT 为当前唯一在用用途。
 * 思考强度旋钮：build 可携 thinkingBudget（思考 token 上限）——null/非正=最强（不携带参数），
 * 框架只暴露旋钮不内置语义，业务侧按 Agent 配置传值（机制进框架、语义留业务）。
 *
 * @author Sharp
 * @since 2026/8/23
 */
public class LiveModelFactory {

    private final ModelConfigProvider provider;
    private final boolean proxyEnable;
    private final String proxyHost;
    private final int proxyPort;
    private final int syncMaxAttempts;

    public LiveModelFactory(ModelConfigProvider provider, boolean proxyEnable, String proxyHost,
            int proxyPort, int syncMaxAttempts) {
        this.provider = provider;
        this.proxyEnable = proxyEnable;
        this.proxyHost = proxyHost;
        this.proxyPort = proxyPort;
        this.syncMaxAttempts = syncMaxAttempts;
    }

    /**
     * 装配流式进度模型（思考强度最强：不携带 thinking_budget）
     *
     * @param purposeType        模型用途（如 DbAwareModelConfigProvider.PURPOSE_AGENT，独立超时口径）
     * @param live               流式帧回调（替换式全文/token 增量，语义由调用方定义）
     * @param noProviderFallback Provider 缺失时的同步模型回落（懒求值）
     */
    public ChatLanguageModel build(String purposeType, Consumer<String> live,
            Supplier<ChatLanguageModel> noProviderFallback) {
        return build(purposeType, live, noProviderFallback, null);
    }

    /**
     * 装配流式进度模型（携思考强度旋钮）
     *
     * @param thinkingBudget 思考 token 上限：null/非正=最强（模型默认全功率思考）；
     *                       正值写入请求体 thinking_budget 截断思考长尾。同步回落路径
     *                       （langchain4j 0.33 不支持自定义参数）不生效，仅流式主路径生效
     */
    public ChatLanguageModel build(String purposeType, Consumer<String> live,
            Supplier<ChatLanguageModel> noProviderFallback, Integer thinkingBudget) {
        if (provider == null) {
            return noProviderFallback.get();
        }
        Proxy proxy = LlmDefaultAutoConfiguration.buildProxy(proxyEnable, proxyHost, proxyPort);
        RawSseStreamingChatModel streaming = new RawSseStreamingChatModel(provider, purposeType, proxy,
                thinkingBudget);
        ChatLanguageModel syncFallback = new RefreshableChatModel(
                provider, purposeType, syncMaxAttempts, proxy, () -> null);
        return new StreamingProgressChatModel(streaming, live,
                provider.getTimeoutSeconds(purposeType), syncFallback);
    }
}
