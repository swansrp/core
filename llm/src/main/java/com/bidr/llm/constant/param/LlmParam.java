package com.bidr.llm.constant.param;

import com.bidr.kernel.constant.param.MetaParam;
import com.bidr.kernel.constant.param.Param;
import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * Title: LlmParam
 * Description: 大模型连接系统参数——@MetaParam 由 SysConfigCacheService 启动扫描自动补进 sys_config，
 * 管理页修改后经 ParamService.refresh() 广播生效（{@link com.bidr.llm.provider.DbAwareModelConfigProvider}
 * 每次调用实时读取，配置变化自动重建底层模型，无需重启）。
 * 数据库值优先于应用配置 {@code llm.*}；API_KEY 为占位符（sk-****）时视为未填写，回落 llm.api-key。
 *
 * @author Sharp
 * @since 2026/8/16
 */

@Getter
@MetaParam
@AllArgsConstructor
public enum LlmParam implements Param {

    /**
     * OpenAI 兼容端点（通常含 /v1），llm 结点未单独配置服务地址时的系统默认
     */
    BASE_URL("大模型服务地址", "https://ws-ixw0hux1g604p9yp.cn-beijing.maas.aliyuncs.com/compatible-mode/v1",
            "任一 OpenAI 兼容端点，通常含 /v1；留空回落应用配置 llm.base-url"),

    /**
     * 占位符 sk-****：真实密钥须在系统参数管理页手动填写，避免明文入库于代码仓库
     */
    API_KEY("大模型密钥", "sk-****",
            "手动填写真实密钥；占位符或留空时回落应用配置 llm.api-key"),

    /**
     * 默认模型名，llm 结点未单独配置模型时的系统默认
     */
    MODEL_NAME("大模型默认模型", "qwen3.8-max",
            "llm 结点未单独配置模型名时使用；留空回落应用配置 llm.model-name"),

    /**
     * 模型调用超时（秒）
     */
    TIMEOUT_SECONDS("大模型超时(秒)", "120",
            "模型调用超时时间（秒）；非法值回落应用配置 llm.timeout-seconds"),

    /**
     * 多模态（视觉）模型服务地址：扫描件/图片转 Markdown 用；留空回落默认模型地址
     */
    VISION_BASE_URL("多模态模型服务地址", "",
            "具备视觉理解能力的模型端点（如 qwen-vl 系列），用于扫描件/图片解析；留空回落默认模型地址"),

    /**
     * 多模态模型密钥：留空回落默认模型密钥
     */
    VISION_API_KEY("多模态模型密钥", "",
            "留空回落默认大模型密钥"),

    /**
     * 多模态模型名：须支持图片输入（如 qwen-vl-max）；留空回落默认模型
     */
    VISION_MODEL_NAME("多模态模型", "",
            "须支持图片输入的模型名（如 qwen-vl-max）；留空回落默认模型"),

    /**
     * 多模态模型调用超时（秒）：图片转录耗时较长，默认高于文本模型
     */
    VISION_TIMEOUT_SECONDS("多模态超时(秒)", "180",
            "多模态模型调用超时时间（秒）；非法值回落应用配置 llm.vision.timeout-seconds");

    private final String title;
    private final String defaultValue;
    private final String remark;
}
