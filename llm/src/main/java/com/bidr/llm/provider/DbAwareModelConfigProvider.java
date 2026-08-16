package com.bidr.llm.provider;

import com.bidr.llm.constant.param.LlmParam;
import com.bidr.platform.service.cache.SysConfigCacheService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.util.StringUtils;

/**
 * 数据库优先的模型配置提供者：{@link LlmParam 系统参数}（sys_config）存在有效值时优先，
 * 否则回落应用配置 {@code llm.*}（yaml）。
 * <p>
 * 由 {@code com.bidr.llm.config.LlmDefaultAutoConfiguration} 在业务未自定义
 * {@link ModelConfigProvider} 时装配。platform 模块（SysConfigCacheService）在类路径且服务可用时
 * 才读取数据库参数，未接入 platform 的应用自动回落纯 yaml（与 core/redis 可选能力同一模式）。
 * 各 getter 每次实时计算，管理页改参后 {@code getConfigSignatureWithoutKey}/{@code getApiKey}
 * 随之变化，可刷新模型包装器据此自动重建底层连接——热生效无需重启。
 * </p>
 * API_KEY 占位约定：数据库或 yaml 任一来源的密钥含 {@code *}（如默认 sk-****）即视为未填写；
 * 两侧均无有效密钥时 {@link #getApiKey} 抛出带操作指引的异常，避免拿着占位符发起必然失败的调用。
 *
 * @author Sharp
 * @since 2026/8/16
 */
@Slf4j
public class DbAwareModelConfigProvider implements ModelConfigProvider {

    private final String yamlBaseUrl;
    private final String yamlApiKey;
    private final String yamlModelName;
    private final long yamlTimeoutSeconds;

    /**
     * platform 可选依赖：服务不可用（未引入 core/platform）时 getIfAvailable 返回 null，全部回落 yaml
     */
    private final ObjectProvider<SysConfigCacheService> sysConfigProvider;

    public DbAwareModelConfigProvider(String yamlBaseUrl, String yamlApiKey, String yamlModelName,
                                      long yamlTimeoutSeconds,
                                      ObjectProvider<SysConfigCacheService> sysConfigProvider) {
        this.yamlBaseUrl = yamlBaseUrl;
        this.yamlApiKey = yamlApiKey;
        this.yamlModelName = yamlModelName;
        this.yamlTimeoutSeconds = yamlTimeoutSeconds;
        this.sysConfigProvider = sysConfigProvider;
    }

    @Override
    public String getBaseUrl(String purposeType) {
        String dbValue = dbValue(LlmParam.BASE_URL);
        return StringUtils.hasText(dbValue) ? dbValue : yamlBaseUrl;
    }

    @Override
    public String getApiKey(String purposeType, Long userId) {
        String dbKey = dbValue(LlmParam.API_KEY);
        if (isRealKey(dbKey)) {
            return dbKey;
        }
        if (isRealKey(yamlApiKey)) {
            return yamlApiKey;
        }
        throw new IllegalStateException("大模型密钥未配置：请在系统参数管理页填写「大模型密钥」(API_KEY)，"
                + "或在应用配置 llm.api-key 设置（当前数据库与应用配置均为占位符或空）");
    }

    @Override
    public String getModelName(String purposeType) {
        String dbValue = dbValue(LlmParam.MODEL_NAME);
        return StringUtils.hasText(dbValue) ? dbValue : yamlModelName;
    }

    @Override
    public long getTimeoutSeconds(String purposeType) {
        String dbValue = dbValue(LlmParam.TIMEOUT_SECONDS);
        if (StringUtils.hasText(dbValue)) {
            try {
                long parsed = Long.parseLong(dbValue);
                if (parsed > 0) {
                    return parsed;
                }
            } catch (NumberFormatException ignored) {
                // 非法值回落 yaml
            }
        }
        return yamlTimeoutSeconds;
    }

    @Override
    public String getConfigSignatureWithoutKey(String purposeType) {
        // 动态取当前生效值拼接：数据库参数变化时签名随之变化，触发底层模型重建
        return getBaseUrl(purposeType) + "|" + getModelName(purposeType) + "|" + getTimeoutSeconds(purposeType);
    }

    /**
     * 密钥有效性：非空且不含占位符 *（默认 sk-**** 视为未填写）
     */
    private boolean isRealKey(String apiKey) {
        return StringUtils.hasText(apiKey) && !apiKey.contains("*");
    }

    /**
     * 读取数据库参数（去空白）；服务未接入或读取异常（参数尚未入库等）时返回 null 回落 yaml
     */
    private String dbValue(LlmParam param) {
        SysConfigCacheService service = sysConfigProvider == null ? null : sysConfigProvider.getIfAvailable();
        if (service == null) {
            return null;
        }
        try {
            String value = service.getSysConfigValue(param);
            return value == null ? null : value.trim();
        } catch (Exception e) {
            log.warn("读取系统参数 {} 失败，回落应用配置 llm.*：{}", param.name(), e.getMessage());
            return null;
        }
    }
}
