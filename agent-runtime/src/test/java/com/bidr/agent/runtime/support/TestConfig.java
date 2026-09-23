package com.bidr.agent.runtime.support;

import com.bidr.agent.runtime.config.AgentRuntimeConfigProvider;
import com.bidr.agent.runtime.config.AgentRuntimeProperties;

/**
 * 单测共用配置：指向 {@link UpstreamStub} 的地址 + 固定测试密钥（不入库、无真实凭据）
 *
 * @author sharp
 * @since 2026/9/22
 */
public final class TestConfig {

    private TestConfig() {
    }

    public static AgentRuntimeConfigProvider provider(String baseUrl) {
        AgentRuntimeProperties properties = new AgentRuntimeProperties();
        properties.setBaseUrl(baseUrl);
        properties.setApiKey("sk-rt-unit-test");
        properties.setConnectTimeoutMs(3000);
        properties.setIdleTimeoutMs(8000);
        properties.setHeartbeatSeconds(30);
        properties.setRelayThreads(4);
        return new AgentRuntimeConfigProvider(properties, null);
    }
}
