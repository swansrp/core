package com.bidr.cube.config;

import com.bidr.document.spi.AgentHealthContributor;

/**
 * Title: CubeHealthContributor
 * Description: Cube 沙箱管理面的<b>健康自报</b>（AgentHealthContributor 腿实现，2026-11-18
 * 探活架构改造——各腿自报、中央只收集）：探测口径＝{@code GET {apiUrl}/health}（管理面无鉴权，
 * <b>仅 200＝可达</b>，其余状态码带码入原因）。bean 注册在 {@link CubeSandboxAutoConfiguration}
 * （类级 {@code my.agent.sandbox.provider=cube} 条件）——Cube 腿没装＝contributor 不存在＝
 * 中央收集自然缺席；与 OpenHands 腿的 contributor 互斥（provider 条件互斥），同一时刻至多一条
 * exec.sandbox 自报（2026-11-18 [U61] 用户拍板「cube 能用＝沙箱能用」的架构化收口）。
 *
 * @author Sharp
 * @since 2026/11/18
 */
public class CubeHealthContributor implements AgentHealthContributor {

    /** 能力位名（快照键，与 llm AgentFileCapabilityResolver.EXEC_SANDBOX 同字面） */
    static final String CAPABILITY_EXEC_SANDBOX = "exec.sandbox";

    private final CubeSandboxProperties properties;

    public CubeHealthContributor(CubeSandboxProperties properties) {
        this.properties = properties;
    }

    @Override
    public String capability() {
        return CAPABILITY_EXEC_SANDBOX;
    }

    @Override
    public String probeName() {
        return "cube";
    }

    @Override
    public int order() {
        return 300;
    }

    @Override
    public HealthReport probe() {
        String apiUrl = properties.getApiUrl();
        if (apiUrl == null || apiUrl.trim().isEmpty()) {
            // provider=cube 但三件套没配齐＝腿未接线（装配层会打「未装配」行并让位）＝探活按未配置上报
            return HealthReport.notConfigured();
        }
        String target = apiUrl.trim();
        if (target.endsWith("/")) {
            target = target.substring(0, target.length() - 1);
        }
        return AgentHealthContributor.httpGet(target + "/health", null, code -> code == 200);
    }
}
