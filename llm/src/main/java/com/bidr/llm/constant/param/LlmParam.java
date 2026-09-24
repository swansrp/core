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
     * Agent 长任务超时（秒）：维护问数 / 自主生成等长编排单次 LLM 调用可达数十秒至数分钟，
     * 流式模型同样受 callTimeout 全调用上限截断（langchain4j 0.33），故默认高于默认模型超时
     */
    AGENT_TIMEOUT_SECONDS("Agent长任务超时(秒)", "600",
            "维护问数/自主生成等长任务 LLM 调用超时（秒）；非法值回落应用配置 llm.agent.timeout-seconds"),

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
            "多模态模型调用超时时间（秒）；非法值回落应用配置 llm.vision.timeout-seconds"),

    /**
     * 工具结果入场卸载阈值（字符）：L1 上下文预算治理灰度开关之一，0=关闭（I9 默认零行为变化）
     */
    AGENT_TOOL_RESULT_OFFLOAD_CHARS("工具结果卸载阈值(字)", "0",
            "0=关闭不卸载；正值（建议4000）超过该字符数的工具结果入场替换为预览+句柄，原文经会话事件流按句柄回捞"),

    /**
     * 指针文本保留的头尾预览合计字数（头 8 成尾 2 成）
     */
    AGENT_TOOL_RESULT_PREVIEW_CHARS("工具结果卸载预览(字)", "1000",
            "指针文本保留的头尾预览合计字数；填 0 或非法值回落默认值 1000；整体关闭卸载与回捞请置 AGENT_TOOL_RESULT_OFFLOAD_CHARS=0"),

    /**
     * 上下文 token 预算（估算 token）：L1 上下文预算治理灰度开关之二，0=关闭仅按条数裁窗（I9）
     */
    AGENT_CONTEXT_TOKEN_BUDGET("Agent上下文token预算", "0",
            "0=仅按条数裁窗（既有行为）；正值（建议24000）为进入模型的消息估算token上限，与条数取更严者；估算偏保守（宁可早裁）"),

    /**
     * 单会话回捞次数上限：防模型反复捞致轮次爆炸（超限只提示收口）
     */
    AGENT_TOOL_RECALL_MAX_PER_RUN("Agent结果回捞次数上限", "3",
            "单会话 recallToolResult 调用次数上限，超限只提示收口，防轮次爆炸；填 0 或非法值回落默认值 3；整体关闭卸载与回捞请置 AGENT_TOOL_RESULT_OFFLOAD_CHARS=0"),

    /**
     * 单次回捞回填模型的原文字符上限（超出截断，全文仍可在前端过程树查看）
     */
    AGENT_TOOL_RECALL_MAX_CHARS("Agent回捞返回上限(字)", "20000",
            "单次回捞回填模型的原文字符上限，超出截断并提示前端过程树可看全文；填 0 或非法值回落默认值 20000；整体关闭卸载与回捞请置 AGENT_TOOL_RESULT_OFFLOAD_CHARS=0");

    private final String title;
    private final String defaultValue;
    private final String remark;
}
