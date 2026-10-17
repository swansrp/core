package com.bidr.llm.agent;

import dev.langchain4j.agent.tool.ToolSpecification;

import java.util.List;

/**
 * Title: AgentToolSurfaceFilter
 * Description: 工具面收窄过滤器（M14 棒6 的框架侧唯一挂点）。
 *
 * <p><b>为什么必须有这一层，而 feature 面已经存在</b>：feature 档的代码工具面是
 * <b>结构性上限</b>（读=父／写=子／孙=自造的类型继承表达，模型手里没有那支笔才是真闸门）；
 * 平台侧的"勾选"要能在上限之内<b>再收窄</b>，且收窄必须落在工具层——
 * 写在提示词里求模型"别用那只工具"不是闸门，是一句请求。</p>
 *
 * <p><b>位置</b>：{@link ToolAgentRunner#run} 里 specs 采集完、上报装配单之前。
 * 过滤后的清单同时是三件事的唯一真值：① 发给模型的 schema，② 装配单记录的工具名清单，
 * ③ 执行期的准入名单（模型即便幻觉出一个已被收窄的工具名，也执行不到）。
 * 所以"装配单里不存在那只工具"与"会话手里真的没有那只工具"是同一份数据，不是两判据。</p>
 *
 * <p><b>可选装配（与 {@link AgentToolSurfaceSink} 同族纪律）</b>：本接口框架内无实现，
 * 由能力台账模块（core/agent-console）按需实现；经 {@code BeanUtil} 取，
 * <b>取不到 Bean＝清单原样返回，工具面逐字符与改前一致</b>。
 * 过滤器抛异常一律被吞并退回<b>未过滤</b>的清单：这一层是"收窄"，它故障时损失的是额外收紧，
 * 结构性上限仍在代码类型上，因此退回原形态比让会话拿不到工具更正确（详见 {@code ModelUsageRecorder}
 * 同族的"运维腿不击穿业务腿"口径）。</p>
 *
 * @author Sharp
 * @since 2026-10-13
 */
public interface AgentToolSurfaceFilter {

    /**
     * 收窄工具清单（实现方<b>只能删、不能加</b>：返回含未入参的工具名＝越权，框架会拒绝整次过滤）。
     *
     * @param agentKey 本次会话的 agent 运行时注册键（无上下文时 null＝实现方应放行为原样）
     * @param resolved 框架已采集完的工具清单（含引擎按上下文预算追加的回捞工具）
     * @return 收窄后的清单；返回 null 视为"不过滤"
     */
    List<ToolSpecification> restrict(String agentKey, List<ToolSpecification> resolved);
}
