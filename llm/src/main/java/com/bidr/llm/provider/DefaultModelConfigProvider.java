package com.bidr.llm.provider;

/**
 * 默认的模型配置提供者：从 yaml 的 {@code llm.*} 读取的单一模型配置，不区分用途（purpose）与用户。
 * <p>
 * 由 {@code com.bidr.llm.config.LlmDefaultAutoConfiguration} 在业务未自定义 {@link ModelConfigProvider}
 * 时自动装配，实现"引入 llm 依赖 + 配置 key 即开箱即用"。业务如需按用户/用途区分 Key，
 * 自行实现并注册一个 {@link ModelConfigProvider} Bean 即可覆盖本默认实现。
 * </p>
 *
 * @author Sharp
 */
public class DefaultModelConfigProvider implements ModelConfigProvider {

    private final String baseUrl;
    private final String apiKey;
    private final String modelName;
    private final long timeoutSeconds;

    public DefaultModelConfigProvider(String baseUrl, String apiKey, String modelName, long timeoutSeconds) {
        this.baseUrl = baseUrl;
        this.apiKey = apiKey;
        this.modelName = modelName;
        this.timeoutSeconds = timeoutSeconds;
    }

    @Override
    public String getBaseUrl(String purposeType) {
        return baseUrl;
    }

    @Override
    public String getApiKey(String purposeType, Long userId) {
        // 无用户级 Key 概念，统一回退到系统级 Key
        return apiKey;
    }

    @Override
    public String getModelName(String purposeType) {
        return modelName;
    }

    @Override
    public long getTimeoutSeconds(String purposeType) {
        return timeoutSeconds;
    }

    @Override
    public String getConfigSignatureWithoutKey(String purposeType) {
        // 拼接所有影响连接的字段，配置变化时触发底层模型重建
        return baseUrl + "|" + modelName + "|" + timeoutSeconds;
    }
}
