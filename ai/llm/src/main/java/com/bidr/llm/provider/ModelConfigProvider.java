package com.bidr.llm.provider;

/**
 * 模型配置提供者接口。
 * <p>
 * 为可刷新模型包装器提供按用途（purpose）区分的模型配置查询能力，
 * 具体实现可以基于数据库、配置文件或远端配置中心。
 * </p>
 *
 * @author Sharp
 */
public interface ModelConfigProvider {

    /**
     * 获取指定用途的 API 基础 URL
     *
     * @param purposeType 用途类型
     * @return API 基础 URL
     */
    String getBaseUrl(String purposeType);

    /**
     * 获取指定用途下某用户可用的 API Key
     *
     * @param purposeType 用途类型
     * @param userId      用户ID，可为 null（由实现决定回退策略）
     * @return API Key 字符串
     */
    String getApiKey(String purposeType, Long userId);

    /**
     * 获取指定用途的模型代码
     *
     * @param purposeType 用途类型
     * @return 模型代码
     */
    String getModelName(String purposeType);

    /**
     * 获取指定用途的超时时间（秒）
     *
     * @param purposeType 用途类型
     * @return 超时秒数
     */
    long getTimeoutSeconds(String purposeType);

    /**
     * 获取指定用途的配置签名（不含 API Key），用于热刷新时判断配置是否变化
     *
     * @param purposeType 用途类型
     * @return 配置签名
     */
    String getConfigSignatureWithoutKey(String purposeType);
}
