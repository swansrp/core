package com.bidr.agent.runtime.config;

import com.bidr.agent.runtime.constant.param.AgentRuntimeParam;
import com.bidr.kernel.exception.ServiceException;
import com.bidr.platform.service.cache.SysConfigCacheService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.ObjectProvider;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Title: AgentRuntimeConfigProviderTest
 * Description: 配置取值口径：系统参数优先 / 应用配置回落 / 占位符视为未填写 / 非法数字回落。
 *
 * @author sharp
 * @since 2026/9/22
 */
class AgentRuntimeConfigProviderTest {

    private AgentRuntimeProperties yaml(String baseUrl, String apiKey) {
        AgentRuntimeProperties properties = new AgentRuntimeProperties();
        properties.setBaseUrl(baseUrl);
        properties.setApiKey(apiKey);
        return properties;
    }

    @SuppressWarnings("unchecked")
    private ObjectProvider<SysConfigCacheService> platform(SysConfigCacheService service) {
        ObjectProvider<SysConfigCacheService> provider = Mockito.mock(ObjectProvider.class);
        Mockito.when(provider.getIfAvailable()).thenReturn(service);
        return provider;
    }

    @Test
    @DisplayName("platform 不可用时：全部回落应用配置")
    void fallsBackToYamlWhenPlatformAbsent() {
        AgentRuntimeConfigProvider provider =
                new AgentRuntimeConfigProvider(yaml("http://127.0.0.1:8100", "sk-rt-abcdefghijklmnop"), null);

        assertEquals("http://127.0.0.1:8100", provider.getBaseUrl());
        assertEquals("sk-rt-abcdefghijklmnop", provider.getApiKey());
        assertEquals("/open/v1", provider.getApiPrefix());
        assertEquals(120000, provider.getIdleTimeoutMs());
        assertTrue(provider.isConfigured());
    }

    @Test
    @DisplayName("密钥占位符 sk-****：视为未填写，不冒充已配置")
    void placeholderApiKeyTreatedAsMissing() {
        AgentRuntimeConfigProvider provider =
                new AgentRuntimeConfigProvider(yaml("http://127.0.0.1:8100", "sk-****"), null);

        assertEquals("", provider.getApiKey());
        assertFalse(provider.isConfigured());
        assertEquals("(未配置)", provider.maskedApiKey());
        assertThrows(ServiceException.class, provider::requireApiKey);
    }

    @Test
    @DisplayName("地址缺失：拒绝并给出配置指引")
    void requireBaseUrlRejectsBlank() {
        AgentRuntimeConfigProvider provider = new AgentRuntimeConfigProvider(yaml("", "sk-rt-x"), null);

        assertThrows(ServiceException.class, provider::requireBaseUrl);
    }

    @Test
    @DisplayName("系统参数优先于应用配置")
    void systemParamWinsOverYaml() {
        SysConfigCacheService service = Mockito.mock(SysConfigCacheService.class);
        Mockito.when(service.getSysConfigValue(AgentRuntimeParam.AGENT_RUNTIME_BASE_URL))
                .thenReturn("http://param-host:8100");
        Mockito.when(service.getSysConfigValue(AgentRuntimeParam.AGENT_RUNTIME_API_KEY))
                .thenReturn("sk-rt-param-key");
        Mockito.when(service.getSysConfigValue(AgentRuntimeParam.AGENT_RUNTIME_RELAY_THREADS))
                .thenReturn("64");

        AgentRuntimeConfigProvider provider = new AgentRuntimeConfigProvider(
                yaml("http://127.0.0.1:8100", "sk-rt-yaml-key"), platform(service));

        assertEquals("http://param-host:8100", provider.getBaseUrl());
        assertEquals("sk-rt-param-key", provider.getApiKey());
        assertEquals(64, provider.getRelayThreads());
    }

    @Test
    @DisplayName("系统参数为占位符/非法值时：回落应用配置")
    void invalidParamFallsBackToYaml() {
        SysConfigCacheService service = Mockito.mock(SysConfigCacheService.class);
        Mockito.when(service.getSysConfigValue(AgentRuntimeParam.AGENT_RUNTIME_API_KEY)).thenReturn("sk-****");
        Mockito.when(service.getSysConfigValue(AgentRuntimeParam.AGENT_RUNTIME_RELAY_THREADS)).thenReturn("not-a-number");
        Mockito.when(service.getSysConfigValue(AgentRuntimeParam.AGENT_RUNTIME_HEARTBEAT_SECONDS)).thenReturn("-5");

        AgentRuntimeConfigProvider provider = new AgentRuntimeConfigProvider(
                yaml("http://127.0.0.1:8100", "sk-rt-yaml-key"), platform(service));

        assertEquals("sk-rt-yaml-key", provider.getApiKey());
        assertEquals(32, provider.getRelayThreads());
        assertEquals(15, provider.getHeartbeatSeconds());
    }

    @Test
    @DisplayName("掩码不泄露完整密钥")
    void maskedApiKeyNeverLeaksFullKey() {
        AgentRuntimeConfigProvider provider =
                new AgentRuntimeConfigProvider(yaml("http://127.0.0.1:8100", "sk-rt-1234567890abcdef"), null);

        assertEquals("sk-rt-12****", provider.maskedApiKey());
    }

    // ==================== 引擎 A SDK 直连键位（AGENT_SDK_* / my.agent.runtime.sdk.*） ====================

    private AgentRuntimeProperties sdkYaml(String baseUrl, String apiKey, String apiPrefix) {
        AgentRuntimeProperties properties = new AgentRuntimeProperties();
        properties.getSdk().setBaseUrl(baseUrl);
        properties.getSdk().setApiKey(apiKey);
        properties.getSdk().setApiPrefix(apiPrefix);
        return properties;
    }

    @Test
    @DisplayName("SDK 键位：platform 不可用时回落 my.agent.runtime.sdk.*，前缀空回落缺省")
    void sdkFallsBackToYamlWhenPlatformAbsent() {
        AgentRuntimeConfigProvider provider =
                new AgentRuntimeConfigProvider(sdkYaml("http://10.3.6.60:8100", "sk-rt-8e4c", ""), null);

        assertEquals("http://10.3.6.60:8100", provider.getSdkBaseUrl());
        assertEquals("sk-rt-8e4c", provider.getSdkApiKey());
        assertEquals("/open/v1", provider.getSdkApiPrefix());
        assertTrue(provider.isSdkConfigured());
    }

    @Test
    @DisplayName("SDK 键位：系统参数 AGENT_SDK_* 优先于应用配置")
    void sdkSystemParamWinsOverYaml() {
        SysConfigCacheService service = Mockito.mock(SysConfigCacheService.class);
        Mockito.when(service.getSysConfigValue(AgentRuntimeParam.AGENT_SDK_BASE_URL))
                .thenReturn("http://param-host:8100");
        Mockito.when(service.getSysConfigValue(AgentRuntimeParam.AGENT_SDK_API_KEY))
                .thenReturn("sk-rt-param-key");
        Mockito.when(service.getSysConfigValue(AgentRuntimeParam.AGENT_SDK_API_PREFIX))
                .thenReturn("/open/v2");

        AgentRuntimeConfigProvider provider = new AgentRuntimeConfigProvider(
                sdkYaml("http://yaml-host:8100", "sk-rt-yaml-key", "/open/v1"), platform(service));

        assertEquals("http://param-host:8100", provider.getSdkBaseUrl());
        assertEquals("sk-rt-param-key", provider.getSdkApiKey());
        assertEquals("/open/v2", provider.getSdkApiPrefix());
    }

    @Test
    @DisplayName("SDK 键位：占位符视为未填写——db/yml 占位均回落空，不冒充已配置")
    void sdkPlaceholderTreatedAsMissing() {
        SysConfigCacheService service = Mockito.mock(SysConfigCacheService.class);
        Mockito.when(service.getSysConfigValue(AgentRuntimeParam.AGENT_SDK_API_KEY)).thenReturn("sk-****");

        AgentRuntimeConfigProvider provider = new AgentRuntimeConfigProvider(
                sdkYaml("http://10.3.6.60:8100", "sk-****", "/open/v1"), platform(service));

        assertEquals("", provider.getSdkApiKey());
        assertFalse(provider.isSdkConfigured());
        assertThrows(ServiceException.class, provider::requireSdkApiKey);
        // 地址有值也不算已接线（isSdkConfigured 要求地址+密钥均有效）
        assertEquals("http://10.3.6.60:8100", provider.getSdkBaseUrl());
    }

    @Test
    @DisplayName("SDK 键位：与 OpenHands 键位互不串线（AGENT_RUNTIME_* 不影响 SDK 口）")
    void sdkKeysAreIndependentFromOpenHandsKeys() {
        SysConfigCacheService service = Mockito.mock(SysConfigCacheService.class);
        Mockito.when(service.getSysConfigValue(AgentRuntimeParam.AGENT_RUNTIME_BASE_URL))
                .thenReturn("http://openhands-host:13100");
        Mockito.when(service.getSysConfigValue(AgentRuntimeParam.AGENT_RUNTIME_API_KEY))
                .thenReturn("openhands-session-key");

        AgentRuntimeConfigProvider provider = new AgentRuntimeConfigProvider(
                sdkYaml("http://sdk-host:8100", "sk-rt-sdk-key", "/open/v1"), platform(service));

        assertEquals("http://sdk-host:8100", provider.getSdkBaseUrl());
        assertEquals("sk-rt-sdk-key", provider.getSdkApiKey());
        assertEquals("http://openhands-host:13100", provider.getBaseUrl());
        assertEquals("openhands-session-key", provider.getApiKey());
    }

    @Test
    @DisplayName("SDK 键位：地址缺失拒绝并给出 AGENT_SDK_BASE_URL 指引")
    void sdkRequireBaseUrlRejectsBlank() {
        AgentRuntimeConfigProvider provider =
                new AgentRuntimeConfigProvider(sdkYaml("", "sk-rt-8e4c", "/open/v1"), null);

        ServiceException ex = assertThrows(ServiceException.class, provider::requireSdkBaseUrl);
        assertTrue(String.valueOf(ex.getMessage()).contains("AGENT_SDK_BASE_URL"),
                "异常须带 AGENT_SDK_BASE_URL 指引：" + ex.getMessage());
    }

    @Test
    @DisplayName("SDK 键位：密钥缺失拒绝并给出 AGENT_SDK_API_KEY 指引")
    void sdkRequireApiKeyRejectsBlank() {
        AgentRuntimeConfigProvider provider =
                new AgentRuntimeConfigProvider(sdkYaml("http://10.3.6.60:8100", "", "/open/v1"), null);

        ServiceException ex = assertThrows(ServiceException.class, provider::requireSdkApiKey);
        assertTrue(String.valueOf(ex.getMessage()).contains("AGENT_SDK_API_KEY"),
                "异常须带 AGENT_SDK_API_KEY 指引：" + ex.getMessage());
    }
}