package com.bidr.llm.agent.session;

import lombok.Data;

import java.io.Serializable;

/**
 * Title: AgentEvent
 * Description: 自主 agent 会话过程事件——会话内 seq 单调递增，前端按 sinceSeq 增量轮询渲染：
 * 思考类事件（round_start/tool_call/tool_result/llm_output/log）折叠为「思考过程」组，
 * 结论与状态事件（finish/error/stopped/paused/resumed/guidance）平铺展示。
 * payload 为任意 JSON 结构（String 或 Map/List），由事件类型约定
 *
 * @author Sharp
 * @since 2026/8/20
 */
@Data
public class AgentEvent implements Serializable {

    /** 会话开始（payload：agentKey + 启动参数摘要） */
    public static final String RUN_START = "run_start";

    /** 工具循环新一轮开始（payload：轮次号） */
    public static final String ROUND_START = "round_start";

    /** LLM 决定调用工具（payload：工具名 + 参数摘要） */
    public static final String TOOL_CALL = "tool_call";

    /** 工具执行返回（payload：工具名 + 结果摘要） */
    public static final String TOOL_RESULT = "tool_result";

    /** LLM 输出文本片段/结论（payload：文本） */
    public static final String LLM_OUTPUT = "llm_output";

    /** 过程日志（payload：一行日志文本） */
    public static final String LOG = "log";

    /** 阶段推进（payload：AgentStage 快照——key/title/status/detail，AgentStages 实时跳动数据源） */
    public static final String STAGE = "stage";

    /** 会话被暂停（payload：暂停说明，可为空） */
    public static final String PAUSED = "paused";

    /** 会话已恢复（payload：恢复操作说明） */
    public static final String RESUMED = "resumed";

    /** 恢复时用户补充的指导语（payload：指导语文本，引擎注入下一轮上下文） */
    public static final String GUIDANCE = "guidance";

    /** LLM 向用户提问等待决策（payload：AgentQuestion 摘要——id/question/options，问题卡片数据以状态快照为准） */
    public static final String QUESTION = "question";

    /** 用户对 LLM 提问作答（payload：答案文本；跳过/超时由工具侧另行上报） */
    public static final String ANSWERED = "answered";

    /** 登记待确认口径（payload：AgentConfirmation 摘要——id/question/adopted，确认页数据以状态快照为准） */
    public static final String CONFIRMATION = "confirmation";

    /** 会话正常收口（payload：结论摘要） */
    public static final String FINISH = "finish";

    /** 会话异常终止（payload：原因） */
    public static final String ERROR = "error";

    /** 会话被停止（payload：已完成情况说明） */
    public static final String STOPPED = "stopped";

    /**
     * 会话内序号（store 分配，1 起单调递增；前端 events(sinceSeq) 据此增量拉取）
     */
    private long seq;

    /**
     * 事件类型（TYPE_* 常量）
     */
    private String type;

    /**
     * 事件载荷（String/Map/List，JSON 序列化随事件流存储）
     */
    private Object payload;

    /**
     * 事件时间（System.currentTimeMillis()）
     */
    private long time;
}
