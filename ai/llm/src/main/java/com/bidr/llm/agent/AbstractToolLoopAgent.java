package com.bidr.llm.agent;

import com.bidr.llm.agent.session.AgentSessionContext;
import com.bidr.llm.usage.ModelCallContexts;
import dev.langchain4j.model.chat.ChatLanguageModel;

import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * Title: AbstractToolLoopAgent
 * Description: 简单工具循环 agent 的模板基类（泛型任务状态 S）——新业务只填钩子不写
 * {@link AutonomousAgentDefinition#start}：{@link #prepare} 做校验/抢锁/备任务态，
 * model/systemPrompt/userPrompt/tools 四要素各一钩，基类统一驱动 {@link ToolAgentRunner#run}
 * 并强制收口契约（停止→InterruptedException→会话 STOPPED、终态写 {@link AgentSessionContext#setSummary}、
 * loopListener 接线、{@link #release} 异常路径也保证资源释放），想写错都难。
 * <p>选型口径：单次 LLM 工具循环 → 继承本类；多阶段/桥接存量任务体（进度记录/心跳等复合形态）
 * → 直接实现 {@link AutonomousAgentDefinition}。定义 Bean 为 Spring 单例：钩子间禁放会话级字段，
 * 会话状态一律走 S 经参传递。
 *
 * @param <S> 会话级任务状态类型（prepare 产物，如连接/上下文封装；无状态需求以 Void 实例化并返回 null）
 * @author Sharp
 * @since 2026/8/20
 */
public abstract class AbstractToolLoopAgent<S> implements AutonomousAgentDefinition {

    /** 通用工具循环引擎（无状态，与业务侧同口径直接实例化，不走 Bean 装配） */
    private final ToolAgentRunner runner = new ToolAgentRunner();

    /**
     * 循环前置准备：payload 校验（缺参直接抛出，会话层收口 FAILED）、抢锁、开连接、构建任务态。
     * 默认无状态（null）；异常向外抛出由会话层收口
     */
    protected S prepare(AgentSessionContext ctx, Map<String, Object> payload) throws Exception {
        return null;
    }

    /** 本会话使用的业务模型 */
    protected abstract ChatLanguageModel model(S state, AgentSessionContext ctx, Map<String, Object> payload);

    /** 系统提示词（角色与约束） */
    protected abstract String systemPrompt(S state, AgentSessionContext ctx, Map<String, Object> payload);

    /** 用户提示词（任务目标 + 上下文，可运行期拼装） */
    protected abstract String userPrompt(S state, AgentSessionContext ctx, Map<String, Object> payload);

    /** 工具对象清单（@Tool 注解方法，任务作用域实例，可持有 state 内的连接/上下文等状态） */
    protected abstract List<Object> tools(S state, AgentSessionContext ctx, Map<String, Object> payload);

    /**
     * 函数式工具声明清单（默认空＝与改造前逐字节一致）。
     * <p>覆写它即可把工具声明成"名字＋说明＋参数 schema＋一个处理器"，不必是 {@code @Tool} 反射方法；
     * 三件声明字段都是可序列化的值，所以将来能从台账行里读出来直接喂进来（执行仍在进程内）。
     * 与反射面的合并、收窄、同名裁决都在 {@link ToolAgentRunner} 一处收口，本钩子不做任何裁决。
     */
    protected List<FunctionalTool> functionalTools(S state, AgentSessionContext ctx,
                                                   Map<String, Object> payload) {
        return Collections.emptyList();
    }

    /** 轮次/滑窗参数（默认 30/60，见 {@link AgentLoopOptions}） */
    protected AgentLoopOptions loopOptions() {
        return new AgentLoopOptions();
    }

    /** 结论摘要（FINISHED 时前端展示）：默认取循环最终文本，可覆写定制 */
    protected String summary(AgentLoopResult result, S state, AgentSessionContext ctx, Map<String, Object> payload) {
        return result.getText();
    }

    /** 资源释放（prepare 开连接/抢锁等时覆写；异常路径也保证执行）；默认空 */
    protected void release(S state) {
    }

    /** 模板执行体：final 禁覆写——停止映射/摘要写入/钩子接线/资源释放等收口契约由基类保证 */
    @Override
    public final void start(AgentSessionContext ctx, Map<String, Object> payload) throws Exception {
        S state = prepare(ctx, payload);
        // 用量计量的身份锚点（M14 棒5）：整场循环期间本线程带会话身份，模型层收口时现读；
        // 循环里的同步调用发生在 llm-call 子线程，那一跳由 ToolAgentRunner 显式把快照带过去。
        // 线程池会复用线程，clear 必须在 finally——漏一次就是把上一场的身份写给下一场
        ModelCallContexts.set(ctx.usageIdentity());
        try {
            AgentLoopResult result = runner.run(model(state, ctx, payload), systemPrompt(state, ctx, payload),
                    userPrompt(state, ctx, payload), tools(state, ctx, payload),
                    functionalTools(state, ctx, payload), loopOptions(), ctx.loopListener());
            if (result.isStopped()) {
                // 与直接实现接口同口径：会话层捕获 InterruptedException 收口 STOPPED
                throw new InterruptedException("用户停止");
            }
            ctx.setSummary(summary(result, state, ctx, payload));
        } finally {
            ModelCallContexts.clear();
            release(state);
        }
    }
}
