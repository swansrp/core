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

    /**
     * 多模态（视觉）模型用途标识：扫描件/图片转 Markdown 时使用，
     * 配置按 VISION_* 系统参数 → llm.vision.* yaml → 默认模型配置逐级回落
     */
    public static final String PURPOSE_VISION = "VISION";

    /**
     * Agent 长任务用途标识：维护问数 / 自主生成等长编排使用，
     * 仅超时独立配置（AGENT_TIMEOUT_SECONDS → llm.agent.timeout-seconds），地址/密钥/模型回落默认
     */
    public static final String PURPOSE_AGENT = "AGENT";

    private final String yamlBaseUrl;
    private final String yamlApiKey;
    private final String yamlModelName;
    private final long yamlTimeoutSeconds;
    private final String yamlVisionBaseUrl;
    private final String yamlVisionApiKey;
    private final String yamlVisionModelName;
    private final long yamlVisionTimeoutSeconds;
    private final long yamlAgentTimeoutSeconds;

    /**
     * platform 可选依赖：服务不可用（未引入 core/platform）时 getIfAvailable 返回 null，全部回落 yaml
     */
    private final ObjectProvider<SysConfigCacheService> sysConfigProvider;

    public DbAwareModelConfigProvider(String yamlBaseUrl, String yamlApiKey, String yamlModelName,
                                      long yamlTimeoutSeconds,
                                      String yamlVisionBaseUrl, String yamlVisionApiKey,
                                      String yamlVisionModelName, long yamlVisionTimeoutSeconds,
                                      long yamlAgentTimeoutSeconds,
                                      ObjectProvider<SysConfigCacheService> sysConfigProvider) {
        this.yamlBaseUrl = yamlBaseUrl;
        this.yamlApiKey = yamlApiKey;
        this.yamlModelName = yamlModelName;
        this.yamlTimeoutSeconds = yamlTimeoutSeconds;
        this.yamlVisionBaseUrl = yamlVisionBaseUrl;
        this.yamlVisionApiKey = yamlVisionApiKey;
        this.yamlVisionModelName = yamlVisionModelName;
        this.yamlVisionTimeoutSeconds = yamlVisionTimeoutSeconds;
        this.yamlAgentTimeoutSeconds = yamlAgentTimeoutSeconds;
        this.sysConfigProvider = sysConfigProvider;
    }

    @Override
    public String getBaseUrl(String purposeType) {
        if (PURPOSE_VISION.equals(purposeType)) {
            String dbValue = dbValue(LlmParam.VISION_BASE_URL);
            if (StringUtils.hasText(dbValue)) {
                return dbValue;
            }
            if (StringUtils.hasText(yamlVisionBaseUrl)) {
                return yamlVisionBaseUrl;
            }
        }
        String dbValue = dbValue(LlmParam.BASE_URL);
        return StringUtils.hasText(dbValue) ? dbValue : yamlBaseUrl;
    }

    @Override
    public String getApiKey(String purposeType, Long userId) {
        if (PURPOSE_VISION.equals(purposeType)) {
            String visionKey = dbValue(LlmParam.VISION_API_KEY);
            if (isRealKey(visionKey)) {
                return visionKey;
            }
            if (isRealKey(yamlVisionApiKey)) {
                return yamlVisionApiKey;
            }
            // 多模态未单独配密钥时回落默认模型密钥（同网关场景）
        }
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
        if (PURPOSE_VISION.equals(purposeType)) {
            String dbValue = dbValue(LlmParam.VISION_MODEL_NAME);
            if (StringUtils.hasText(dbValue)) {
                return dbValue;
            }
            if (StringUtils.hasText(yamlVisionModelName)) {
                return yamlVisionModelName;
            }
        }
        String dbValue = dbValue(LlmParam.MODEL_NAME);
        return StringUtils.hasText(dbValue) ? dbValue : yamlModelName;
    }

    @Override
    public long getTimeoutSeconds(String purposeType) {
        if (PURPOSE_VISION.equals(purposeType)) {
            Long visionTimeout = parsePositiveLong(dbValue(LlmParam.VISION_TIMEOUT_SECONDS));
            if (visionTimeout != null) {
                return visionTimeout;
            }
            return yamlVisionTimeoutSeconds;
        }
        if (PURPOSE_AGENT.equals(purposeType)) {
            Long agentTimeout = parsePositiveLong(dbValue(LlmParam.AGENT_TIMEOUT_SECONDS));
            if (agentTimeout != null) {
                return agentTimeout;
            }
            return yamlAgentTimeoutSeconds;
        }
        Long timeout = parsePositiveLong(dbValue(LlmParam.TIMEOUT_SECONDS));
        return timeout != null ? timeout : yamlTimeoutSeconds;
    }

    @Override
    public String getConfigSignatureWithoutKey(String purposeType) {
        // 动态取当前生效值拼接：数据库参数变化时签名随之变化，触发底层模型重建；含 purpose 区分用途
        return purposeType + "|" + getBaseUrl(purposeType) + "|" + getModelName(purposeType)
                + "|" + getTimeoutSeconds(purposeType);
    }

    /**
     * 解析正整数（去空白）；非法或非正值返回 null 由调用方回落 yaml
     */
    private Long parsePositiveLong(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        try {
            long parsed = Long.parseLong(value.trim());
            return parsed > 0 ? parsed : null;
        } catch (NumberFormatException e) {
            return null;
        }
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
