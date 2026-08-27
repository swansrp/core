package com.bidr.llm.agent.registry;

/**
 * Title: AgentKind
 * Description: 统一注册中心的 agent 类型划分——三类执行形态在一个清单里归组展示：
 * <ul>
 *     <li>FLOW：确定流程型（DAG 编排，FlowEngine 执行，如 chatbi ask/route、资产生成 pipeline）</li>
 *     <li>AUTONOMOUS：AI 自主型（自主规划会话，ToolAgentRunner 工具循环执行）</li>
 *     <li>DYNAMIC：动态注册型（业务数据驱动，如 smartquery 按 InsightAgent 表逐行注册）</li>
 * </ul>
 *
 * @author Sharp
 * @since 2026/8/22
 */
public enum AgentKind {
    FLOW,
    AUTONOMOUS,
    DYNAMIC
}
