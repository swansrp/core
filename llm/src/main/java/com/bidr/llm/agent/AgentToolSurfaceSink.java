package com.bidr.llm.agent;

/**
 * Title: AgentToolSurfaceSink
 * Description: 工具面定形回调（M14 棒2 装配单的框架侧唯一出口）。
 *
 * <p><b>为什么挂在这里</b>：一次会话"手里有哪些工具"的唯一收敛点是
 * {@link ToolAgentRunner#run} 里 {@code ToolSpecifications} 采集完的那一刻——业务侧的
 * {@code tools(...)} 钩子只交出对象，引擎还会按上下文预算条件追加回捞工具，
 * 所以在业务侧重算清单必然失真。框架把这一刻的名字清单交出去，其余一概不管。</p>
 *
 * <p><b>可选装配</b>：本接口无框架内实现，由能力台账模块（core/agent-console）按需实现；
 * 会话层经 {@code BeanUtil} 取，取不到即静默跳过 ⇒ 不装台账的工程零行为变化。
 * 回调异常一律由调用方吞掉，绝不击穿工具循环</p>
 *
 * @author Sharp
 * @since 2026-10-12
 */
public interface AgentToolSurfaceSink {

    /**
     * 工具面定形（每次工具循环一次，参数为框架侧事实，不含任何业务解读）
     *
     * @param facts 会话身份 + 工具名清单 + 原始 payload
     */
    void onToolsResolved(ToolSurfaceFacts facts);
}
