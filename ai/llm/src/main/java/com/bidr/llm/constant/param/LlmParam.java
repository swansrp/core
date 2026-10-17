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
 * <p>
 * 🔴 出厂形态按"值跟不跟环境走"分：<b>环境绑定类</b>（{@link #BASE_URL} 地址、{@link #MODEL_NAME} 模型名、
 * {@link #API_KEY} 密钥占位符、VISION 三格）出厂一律<b>留空</b>——因为取用时<b>库值优先于 yml</b>，
 * 出厂值非空＝清库/全新装机那一刻被补种进库、静默顶掉现场 yml/.env 里的正确地址与模型
 * （2026-10-17 zz 实录：出厂阿里网关顶掉现场 ark，拼出错位凭据对，见墨枢 Runbook 坑 58）；
 * <b>数值类</b>（超时/阈值）不跟环境走，出厂给可用值；<b>容量与寿命类</b>
 * （{@link #AGENT_SESSION_TTL_SECONDS}、{@link #AGENT_TASK_GATE_ORPHAN_MILLIS}）出厂留空＝
 * 空值不代表"0"，代表"本键未启用、按应用配置来"。共通不变式：共享件升级时，任何项目 yml 里
 * 显式覆写过的值都不会被库里新种的出厂值静默顶掉。
 * ⚠️ 出厂值只影响"缺行时的补种"——存量库行不回写，升级后要回落仍需手工清一次 config_value。
 *
 * @author Sharp
 * @since 2026/8/16
 */

@Getter
@MetaParam("大模型")
@AllArgsConstructor
public enum LlmParam implements Param {

    /**
     * OpenAI 兼容端点（通常含 /v1）。出厂留空＝环境绑定值，正源在应用配置 llm.base-url
     * （占位符映射环境变量/.env），要临时切地址再回参数页填
     */
    BASE_URL("大模型服务地址", "",
            "【现场必配】任一 OpenAI 兼容端点（通常含 /v1）。🔴 出厂刻意留空：库值优先于 yml，这里一旦有值就会把现场 .env/yml 的地址顶掉；留空＝按应用配置 llm.base-url；填了改后下一次调用即用，不重启"),

    /**
     * 占位符 sk-****：真实密钥须在系统参数管理页手动填写，避免明文入库于代码仓库
     */
    API_KEY("大模型密钥", "sk-****",
            "【现场必配】真实密钥只能在此手工填写（出厂是占位符 sk-****，含 * 视为未填）；⚠️ 敏感值，泄漏请在网关侧重置；改后下一次调用即用"),

    /**
     * 默认模型名。出厂留空＝环境绑定值，正源在应用配置 llm.model-name；
     * 运行期要临时换模型再回参数页填
     */
    MODEL_NAME("大模型默认模型", "",
            "【按需调整】默认模型名；🔴 出厂刻意留空＝按应用配置 llm.model-name（库值优先于 yml，填了才覆盖）；临时换模型改这一格即可，改后下一次调用即用、不用重启"),

    /**
     * 模型调用超时（秒）
     */
    TIMEOUT_SECONDS("大模型超时(秒)", "120",
            "【出厂默认即可】普通模型调用超时（秒），慢模型可调大；非法值回落应用配置 llm.timeout-seconds"),

    /**
     * Agent 长任务超时（秒）：维护问数 / 自主生成等长编排单次 LLM 调用可达数十秒至数分钟，
     * 流式模型同样受 callTimeout 全调用上限截断（langchain4j 0.33），故默认高于默认模型超时
     */
    AGENT_TIMEOUT_SECONDS("Agent长任务超时(秒)", "600",
            "【出厂默认即可】Agent 编排单次调用可达数分钟故高于普通超时；动它前先确认确是长任务超时而非服务故障；非法值回落 llm.agent.timeout-seconds"),

    /**
     * agent 会话快照与事件流的 Redis 键存活秒数。
     * 🔴 出厂留空＝维持应用配置 {@code llm.agent-session.ttl-seconds}（core 默认 86400＝24 小时），
     * 共享件默认行为不许改：任何项目 yml 里显式覆写过这一键，都不该因为升级 core 被库值静默顶掉
     */
    AGENT_SESSION_TTL_SECONDS("Agent会话保留(秒)", "",
            "【按需调整】agent 会话快照与过程事件在 Redis 里存活多久（出厂 24 小时）。留空＝按应用配置 llm.agent-session.ttl-seconds（core 默认 86400）；填正数即改后下一次写入生效、不用重启。⚠️ 只影响之后的写入：已存在的键寿命不变，活跃会话每次写入都续期，所以「寿命」实质是最后一次写入后的静默时长；非正/非数字/超出可表示范围一律回落应用配置"),

    /**
     * 任务闸门判定「属主失联」的心跳超时毫秒数（超阈值即强删锁自愈）
     */
    AGENT_TASK_GATE_ORPHAN_MILLIS("Agent任务失联阈值(毫秒)", "",
            "【按需调整】同一条任务键上属主心跳超过这个毫秒数即判失联、强删锁让下一跑接上。留空＝按应用配置 llm.agent-task-gate.orphan-millis（core 默认 90000＝90 秒）；填正数即下一次判定生效、不用重启。⚠️ 调小于任务心跳间隔会把正常在跑的任务误判失联造成双跑；非正/非数字一律回落应用配置"),

    /**
     * 多模态（视觉）模型服务地址：扫描件/图片转 Markdown 用；留空回落默认模型地址
     */
    VISION_BASE_URL("多模态模型服务地址", "",
            "【未用扫描件解析则不动】视觉模型端点（如 qwen-vl 系列），图片/扫描件转 Markdown 用；留空回落默认模型地址"),

    /**
     * 多模态模型密钥：留空回落默认模型密钥
     */
    VISION_API_KEY("多模态模型密钥", "",
            "【未用扫描件解析则不动】留空回落默认大模型密钥；⚠️ 敏感值"),

    /**
     * 多模态模型名：须支持图片输入（如 qwen-vl-max）；留空回落默认模型
     */
    VISION_MODEL_NAME("多模态模型", "",
            "【按需调整·视觉解析启用时】须支持图片输入的模型名（如 qwen-vl-max）；留空回落默认模型（默认模型多半不支持图片会解析失败）"),

    /**
     * 多模态模型调用超时（秒）：图片转录耗时较长，默认高于文本模型
     */
    VISION_TIMEOUT_SECONDS("多模态超时(秒)", "180",
            "【出厂默认即可】图片转录耗时长故默认高于文本模型；非法值回落 llm.vision.timeout-seconds"),

    /**
     * 工具结果入场卸载阈值（字符）：L1 上下文预算治理灰度开关之一，0=关闭（I9 默认零行为变化）
     */
    AGENT_TOOL_RESULT_OFFLOAD_CHARS("工具结果卸载阈值(字)", "0",
            "【调优出口·默认关闭】0=不卸载；正值（建议4000）起，超长工具结果入场替换为预览+句柄，原文经会话事件流按句柄回捞——上下文治理灰度开关，置 0 即回原行为"),

    /**
     * 指针文本保留的头尾预览合计字数（头 8 成尾 2 成）
     */
    AGENT_TOOL_RESULT_PREVIEW_CHARS("工具结果卸载预览(字)", "1000",
            "【调优出口】指针文本头尾预览合计字数；填 0/非法回落 1000；整体关闭请把卸载阈值置 0"),

    /**
     * 上下文 token 预算（估算 token）：L1 上下文预算治理灰度开关之二，0=关闭仅按条数裁窗（I9）
     */
    AGENT_CONTEXT_TOKEN_BUDGET("Agent上下文token预算", "0",
            "【调优出口·默认关闭】0=仅按条数裁窗（既有行为）；正值（建议24000）为进模型消息估算 token 上限，与条数取更严（估算偏保守宁可早裁）"),

    /**
     * 单会话回捞次数上限：防模型反复捞致轮次爆炸（超限只提示收口）
     */
    AGENT_TOOL_RECALL_MAX_PER_RUN("Agent结果回捞次数上限", "3",
            "【调优出口】单会话 recallToolResult 次数上限，超限只提示收口、防轮次爆炸；填 0/非法回落 3；整体关闭请把卸载阈值置 0"),

    /**
     * 单次回捞回填模型的原文字符上限（超出截断，全文仍可在前端过程树查看）
     */
    AGENT_TOOL_RECALL_MAX_CHARS("Agent回捞返回上限(字)", "20000",
            "【调优出口】单次回捞回填模型的原文字符上限，超出截断（全文仍在前端过程树）；填 0/非法回落 20000；整体关闭请把卸载阈值置 0"),

    /**
     * run 作用域回捞缓冲容量（字符）：框架自带兜底通道（I16）暂存「模型已看不到的工具结果原文」
     * （被卸载为指针的 + 被窗口驱逐进摘要的），按插入序 FIFO 挤出最旧条目
     */
    AGENT_TOOL_RECALL_BUFFER_CHARS("Agent回捞缓冲容量(字)", "120000",
            "【调优出口】run 作用域可回捞原文总字数上限，超出按插入序挤掉最旧句柄（被挤出的回捞返回未找到）；填 0/非法回落 120000");

    private final String title;
    private final String defaultValue;
    private final String remark;
}
