package com.bidr.llm.agent.session;

import lombok.Data;

import java.io.Serializable;

/**
 * Title: AgentSessionState
 * Description: 自主 agent 会话状态快照——轮询端点的数据主体：
 * RUNNING/PAUSED 期间心跳持续刷新（20s），查询侧发现心跳超时即判定执行实例失联转 STOPPED；
 * 终态（FINISHED/FAILED/STOPPED）后随 store TTL 过期（默认 24h）
 *
 * @author Sharp
 * @since 2026/8/20
 */
@Data
public class AgentSessionState implements Serializable {

    /** 执行中（含阻塞中的暂停前间隙） */
    public static final String RUNNING = "RUNNING";

    /** 已暂停（run 线程阻塞在暂停检查点，等待恢复） */
    public static final String PAUSED = "PAUSED";

    /** 正常收口 */
    public static final String FINISHED = "FINISHED";

    /** 异常终止（error 携带原因） */
    public static final String FAILED = "FAILED";

    /** 被用户停止 / 执行实例失联 */
    public static final String STOPPED = "STOPPED";

    /**
     * 会话标识（start 返回，控制与查询端点的路径参数）
     */
    private String sessionId;

    /**
     * agent 注册标识（AutonomousAgentDefinition#agentKey）
     */
    private String agentKey;

    /**
     * 归属 skill（SkillWorkbench 编组用）
     */
    private String skillCode;

    /**
     * agent 显示名（前端标题）
     */
    private String displayName;

    /**
     * 发起人（为空回落 anonymous）
     */
    private String operator;

    /**
     * 会话状态（本类常量）
     */
    private String status;

    /**
     * 会话开始时间
     */
    private long startedAt;

    /**
     * 会话结束时间（终态有值）
     */
    private Long endedAt;

    /**
     * 心跳时间（run 线程周期刷新；查询侧超时判失联）
     */
    private long heartbeat;

    /**
     * 会话作用对象标识（定义经 sessionSubject 提供，如资产生成的业务 agentCode）：
     * 活跃会话列表按它做业务维度过滤，支持刷新后定向重连；无则 null
     */
    private String subject;

    /**
     * 本会话生效的断开策略（DetachPolicy 枚举名）：发起时解析落定——发起方可覆盖、
     * 未指定回落定义层默认，同一 agent 在不同页面场景可差异化；心跳任务按本字段执行（不实时查定义）
     */
    private String detachPolicy;

    /**
     * 结论摘要（业务经 AgentSessionContext#setSummary 写入，FINISHED 时展示）
     */
    private String summary;

    /**
     * 终止/停止原因（FAILED/STOPPED 时展示）
     */
    private String error;

    /**
     * 关联执行轨迹标识（flow 型会话预留，与 FlowTrace 归并）
     */
    private String traceId;

    /**
     * LLM 流式实时内容（替换式：思考/应答阶段状态行+累积全文，每秒覆盖，与问数链 live 同口径）；
     * 区别于追加式事件流：不进事件列表不膨胀存储，轮询每次拿到的都是最新一帧；
     * 轮末思考全文另经【LLM 思考归档】LOG 事件留痕，终态时清空（前端不再展示）
     */
    private String live;

    /**
     * 执行阶段清单（业务经 AgentSessionContext#defineStages 声明并随执行推进更新；
     * 阶段状态组件全量渲染数据源，未声明为空列表）
     */
    private java.util.List<AgentStage> stages = new java.util.ArrayList<>();

    /**
     * 计划待办清单（LLM 开局经 submit_plan 提交、done_plan_item 逐条挑勾；
     * 前端清单勾选渲染数据源，未提交为空列表）
     */
    private java.util.List<AgentPlanItem> plan = new java.util.ArrayList<>();

    /**
     * 用户决策问题清单（LLM 经 ask_user 提问、用户经作答端点选择/输入/跳过；
     * waiting 条目渲染为可交互问题卡片，其余按状态留痕，未提问为空列表）
     */
    private java.util.List<AgentQuestion> questions = new java.util.ArrayList<>();

    /**
     * 待确认口径清单（LLM 自决口径经业务登记工具登记；终态后确认页逐条展示，
     * pending 条目可一键确认或改口径重算，未登记为空列表）
     */
    private java.util.List<AgentConfirmation> confirmations = new java.util.ArrayList<>();

    public boolean isTerminal() {
        return FINISHED.equals(status) || FAILED.equals(status) || STOPPED.equals(status);
    }
}
