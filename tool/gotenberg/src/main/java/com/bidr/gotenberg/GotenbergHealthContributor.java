package com.bidr.gotenberg;

import com.bidr.document.spi.AgentHealthContributor;

/**
 * Title: GotenbergHealthContributor
 * Description: Gotenberg 文档转换服务的<b>健康自报</b>（AgentHealthContributor 腿实现，
 * 2026-11-18 探活架构改造——各腿自报、中央只收集）：探测口径与 {@link GotenbergConvertClient}
 * 同模块内聚——{@code GET {tool.gotenberg.base-url}/health}（<b>仅 200＝可达</b>，其余状态码带码
 * 入原因）。bean 注册在 {@link GotenbergAutoConfiguration}（类级 {@link GotenbergBaseUrlCondition}
 * ＝base-url 非空才装配）——底座没接线＝contributor bean 不存在＝中央收集自然缺席（用户拍板
 * 「没有这个子模块就不收集」），不会以 unconfigured 形态误现。
 *
 * @author Sharp
 * @since 2026/11/18
 */
public class GotenbergHealthContributor implements AgentHealthContributor {

    /** 能力位名（快照键，与 llm AgentFileCapabilityResolver.ARTIFACTS_DOC 同字面） */
    static final String CAPABILITY_ARTIFACTS_DOC = "artifacts.doc";

    private final GotenbergProperties properties;

    public GotenbergHealthContributor(GotenbergProperties properties) {
        this.properties = properties;
    }

    @Override
    public String capability() {
        return CAPABILITY_ARTIFACTS_DOC;
    }

    @Override
    public String probeName() {
        return "gotenberg";
    }

    @Override
    public int order() {
        return 200;
    }

    @Override
    public HealthReport probe() {
        String baseUrl = properties.getBaseUrl();
        if (baseUrl == null || baseUrl.trim().isEmpty()) {
            // 类级 Condition 保证到场即有值；此分支为编程式构造/条件变更后的兜底
            return HealthReport.notConfigured();
        }
        String target = baseUrl.trim();
        if (target.endsWith("/")) {
            target = target.substring(0, target.length() - 1);
        }
        // 代理与业务链 GotenbergConvertClient 同口径（enable+host+port 齐才走）——探活同步走
        // 才不会「业务通、探活 timeout」误报（2026-11-14 mineru 代理镜像同款教训）
        GotenbergProperties.Proxy proxy = properties.getProxy();
        java.net.Proxy httpProxy = proxy != null && proxy.isEnable()
                && !proxy.getHost().isBlank() && proxy.getPort() > 0
                ? new java.net.Proxy(java.net.Proxy.Type.HTTP,
                        new java.net.InetSocketAddress(proxy.getHost(), proxy.getPort()))
                : null;
        return AgentHealthContributor.httpGet(target + "/health", httpProxy, code -> code == 200);
    }
}
