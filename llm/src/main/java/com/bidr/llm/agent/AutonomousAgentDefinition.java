package com.bidr.llm.agent;

import com.bidr.llm.agent.session.AgentSessionContext;

import java.util.Map;

/**
 * Title: AutonomousAgentDefinition
 * Description: 自主规划型 agent 的业务注册接口——llm 提供会话生命周期（存储/事件流/暂停/停止/心跳），
 * 业务实现本接口注入执行体：组装提示词、构造工具对象、调 {@link ToolAgentRunner#run}，
 * 过程经 {@link AgentSessionContext#emit} 上报事件（前端思考过程与结论渲染的数据源）。
 * 注册为 Spring Bean 即被 {@link com.bidr.llm.agent.session.AgentSessionService} 纳入注册表
 * （agentKey 封闭集，重复注册启动时抛错）。
 * <p>选型口径：单次 LLM 工具循环会话优先继承 {@link AbstractToolLoopAgent}（模板基类
 * 包办停止映射/摘要写入/钩子接线，只填 prepare/model/提示词/tools 钩子）；仅多阶段/
 * 桥接存量任务体等复合形态才直接实现本接口
 *
 * @author Sharp
 * @since 2026/8/20
 */
public interface AutonomousAgentDefinition {

    /** agent 注册标识（会话 start 的 agentKey；全局唯一，建议 skill-业务 形式如 asset-gen-autonomous） */
    String agentKey();

    /** 显示名（前端会话标题与注册表） */
    String displayName();

    /** 归属 skill（SkillWorkbench 编组；会话状态快照带回前端） */
    String skillCode();

    /**
     * 会话执行体（run 线程内调用）：业务自组提示词/工具并驱动工具循环。
     * 暂停/停止经 ctx 提供的原语感知（ctx.loopListener() 组合钩子直连 ToolAgentRunner）；
     * 结论摘要经 ctx.setSummary 写入（FINISHED 时前端展示）；异常直接抛出由会话层收口 FAILED
     *
     * @param ctx     会话上下文（事件上报/控制原语/启动参数）
     * @param payload 启动参数（Controller 透传，业务自定义结构）
     */
    void start(AgentSessionContext ctx, Map<String, Object> payload) throws Exception;

    /**
     * 会话收口回调（FINISHED/FAILED/STOPPED 后调用，业务补记录：如落库兜底、日志收尾）。
     * 回调异常只记日志不影响会话状态；默认空实现
     *
     * @param ctx   会话上下文
     * @param status 终态（AgentSessionState 常量）
     * @param error 终止原因（FAILED/STOPPED 时非空）
     */
    default void onFinish(AgentSessionContext ctx, String status, String error) {
    }

    /**
     * 前端断开后的会话策略（默认 {@link DetachPolicy#STOP_ON_DETACH} 断开即停省 token，用户场景）；
     * 功能型任务（如配置/资产生成，刷新后需重连继续跟进）覆写为 {@link DetachPolicy#KEEP_RUNNING}
     */
    default DetachPolicy detachPolicy() {
        return DetachPolicy.STOP_ON_DETACH;
    }

    /**
     * 会话作用对象标识（默认 null）：存快照供前端按业务维度过滤活跃会话重连（如资产生成传业务 agentCode）
     *
     * @param payload 启动参数（与 {@link #start} 同源）
     */
    default String sessionSubject(Map<String, Object> payload) {
        return null;
    }

    /**
     * 历史对话问题展示（通用对话落盘钩子）：返回非空时，会话收口后由会话层自动向通用历史对话
     * （{@link com.bidr.llm.agent.conversation.AgentConversationService}）写单轮对话——
     * user 消息=本方法返回值，assistant 消息=结论摘要/失败原因，agentCode 经
     * {@link #conversationAgentCode} 解析（默认 agentKey）。默认 null=不落对话（纯后台型会话）。
     * 业务按启动参数组装问题文本（如提问原文/选表说明）
     *
     * @param payload 启动参数（与 {@link #start} 同源）
     */
    default String conversationQuestion(Map<String, Object> payload) {
        return null;
    }

    /**
     * 历史对话归属 agentCode（默认 agentKey）。业务需把会话对话归入动态命名空间码
     * （如 smartquery:{code}，与同业务票据链/注册中心同构、前端可按页面所选 agent 过滤历史）时覆写
     *
     * @param payload 启动参数（与 {@link #start} 同源）
     */
    default String conversationAgentCode(Map<String, Object> payload) {
        return agentKey();
    }
}
