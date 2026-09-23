package com.bidr.agent.runtime.config;

import com.bidr.agent.runtime.constant.param.AgentRuntimeParam;
import com.bidr.kernel.constant.err.ErrCodeSys;
import com.bidr.kernel.exception.ServiceException;
import com.bidr.platform.service.cache.SysConfigCacheService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.util.StringUtils;

/**
 * Title: AgentRuntimeConfigProvider
 * Description: Agent Runtime 接入配置的**唯一读取出口**——系统参数（sys_config，{@link AgentRuntimeParam}）
 * 有有效值时优先，否则回落应用配置 {@code my.agent.runtime.*}（{@link AgentRuntimeProperties}）。
 * <p>
 * 各 getter 每次实时计算：管理页改参后无需重启即生效（与 core/llm 的 Param 优先/yaml 回落同一模式）。
 * platform 模块（SysConfigCacheService）在类路径且服务可用时才读数据库参数，否则纯 yaml。
 * <p>
 * 密钥占位约定：任一来源的密钥含 {@code *}（如默认 sk-****）即视为未填写；两侧均无有效值时不返回
 * 空串冒充配置，{@link #requireApiKey()} 直接抛出带操作指引的异常——避免拿着占位符发起必然失败的调用。
 * <p>
 * 由 {@link AgentRuntimeAutoConfiguration} 装配（业务可注册同类型 Bean 覆盖）。
 *
 * @author sharp
 * @since 2026/9/22
 */
@Slf4j
public class AgentRuntimeConfigProvider {

    /**
     * 未配置时的默认接口前缀
     */
    public static final String DEFAULT_API_PREFIX = "/open/v1";

    /**
     * 占位符标记：密钥含此字符即视为未填写
     */
    private static final String PLACEHOLDER_MARK = "*";

    private final AgentRuntimeProperties properties;

    /**
     * platform 为可选依赖：服务不可用时 {@code getIfAvailable()} 返回 null，全部回落应用配置
     */
    private final ObjectProvider<SysConfigCacheService> sysConfigProvider;

    public AgentRuntimeConfigProvider(AgentRuntimeProperties properties,
                                      ObjectProvider<SysConfigCacheService> sysConfigProvider) {
        this.properties = properties;
        this.sysConfigProvider = sysConfigProvider;
    }

    /**
     * 平台开放面地址（无有效值返回空串；调用方用 {@link #isConfigured()} 判定接线状态）
     */
    public String getBaseUrl() {
        return text(dbValue(AgentRuntimeParam.AGENT_RUNTIME_BASE_URL), properties.getBaseUrl());
    }

    /**
     * 平台开放面密钥（未填写返回空串，绝不返回占位符）
     */
    public String getApiKey() {
        String dbValue = dbValue(AgentRuntimeParam.AGENT_RUNTIME_API_KEY);
        if (StringUtils.hasText(dbValue) && !isPlaceholder(dbValue)) {
            return dbValue;
        }
        String yamlValue = properties.getApiKey();
        return StringUtils.hasText(yamlValue) && !isPlaceholder(yamlValue) ? yamlValue.trim() : "";
    }

    public String getApiPrefix() {
        String value = text(dbValue(AgentRuntimeParam.AGENT_RUNTIME_API_PREFIX), properties.getApiPrefix());
        return StringUtils.hasText(value) ? value : DEFAULT_API_PREFIX;
    }

    public int getConnectTimeoutMs() {
        return positiveInt(dbValue(AgentRuntimeParam.AGENT_RUNTIME_CONNECT_TIMEOUT_MS),
                properties.getConnectTimeoutMs());
    }

    /**
     * 上游单轮流空闲读超时：超时即视为断流（平台 15 秒心跳，勿配得过短）
     */
    public int getIdleTimeoutMs() {
        return positiveInt(dbValue(AgentRuntimeParam.AGENT_RUNTIME_IDLE_TIMEOUT_MS),
                properties.getIdleTimeoutMs());
    }

    public int getHeartbeatSeconds() {
        return positiveInt(dbValue(AgentRuntimeParam.AGENT_RUNTIME_HEARTBEAT_SECONDS),
                properties.getHeartbeatSeconds());
    }

    public int getRelayThreads() {
        return positiveInt(dbValue(AgentRuntimeParam.AGENT_RUNTIME_RELAY_THREADS),
                properties.getRelayThreads());
    }

    /**
     * 接线状态：地址与密钥均有效才算已接线（未接线时不发起任何上游请求）
     */
    public boolean isConfigured() {
        return StringUtils.hasText(getBaseUrl()) && StringUtils.hasText(getApiKey());
    }

    /**
     * 取地址（未配置即拒绝）
     */
    public String requireBaseUrl() {
        String value = getBaseUrl();
        if (!StringUtils.hasText(value)) {
            throw new ServiceException(ErrCodeSys.SYS_CONFIG_NOT_EXIST,
                    "Agent平台服务地址（系统参数 AGENT_RUNTIME_BASE_URL 或应用配置 my.agent.runtime.base-url）");
        }
        return value;
    }

    /**
     * 取密钥（未配置即拒绝）
     */
    public String requireApiKey() {
        String value = getApiKey();
        if (!StringUtils.hasText(value)) {
            throw new ServiceException(ErrCodeSys.SYS_CONFIG_NOT_EXIST,
                    "Agent平台密钥（系统参数 AGENT_RUNTIME_API_KEY 或应用配置 my.agent.runtime.api-key）");
        }
        return value;
    }

    /**
     * 日志用掩码（只留前缀，绝不打印完整密钥）
     */
    public String maskedApiKey() {
        String value = getApiKey();
        if (!StringUtils.hasText(value)) {
            return "(未配置)";
        }
        return value.length() <= 8 ? "****" : value.substring(0, 8) + "****";
    }

    /**
     * 取库值（platform 不可用或读取异常一律回落 null，不阻断链路）
     */
    private String dbValue(AgentRuntimeParam param) {
        SysConfigCacheService service = sysConfigProvider == null ? null : sysConfigProvider.getIfAvailable();
        if (service == null) {
            return null;
        }
        try {
            String value = service.getSysConfigValue(param);
            return value == null ? null : value.trim();
        } catch (Exception e) {
            log.warn("读取系统参数 {} 失败，回落应用配置 my.agent.runtime.*：{}", param.name(), e.getMessage());
            return null;
        }
    }

    private String text(String dbValue, String yamlValue) {
        if (StringUtils.hasText(dbValue)) {
            return dbValue.trim();
        }
        return yamlValue == null ? "" : yamlValue.trim();
    }

    /**
     * 解析正整数（去空白）；非法或非正值一律回落应用配置
     */
    private int positiveInt(String dbValue, int yamlValue) {
        if (StringUtils.hasText(dbValue)) {
            try {
                int parsed = Integer.parseInt(dbValue.trim());
                if (parsed > 0) {
                    return parsed;
                }
            } catch (NumberFormatException ignored) {
                log.warn("系统参数值非法（{}），回落应用配置", dbValue);
            }
        }
        return yamlValue;
    }

    private boolean isPlaceholder(String value) {
        return value.contains(PLACEHOLDER_MARK);
    }
}