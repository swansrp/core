package com.bidr.llm.usage;

/**
 * Title: ModelCallContexts
 * Description: {@link ModelCallContext} 的线程持有器（M14 棒5）：会话身份只在发起线程上存在，
 * 模型包装层要读它就得同线程或由发起方显式把快照递过来。
 *
 * <p><b>为什么不直接用登录上下文</b>：模型调用跑在 {@code llm-call-interruptible} 子线程与
 * okhttp 回调线程上，ThreadLocal 的登录/空间上下文到不了那里（{@code AgentThreadContext} 同问题），
 * 装配单当初也是把身份当参数显式交出去的。本类沿用同一口径：显式 set、显式清。</p>
 *
 * <p><b>纪律</b>：set 与 clear 必须成对（线程池会复用线程，漏 clear 就是把上一场的身份写给下一场）。
 * 框架侧唯一设定点是 {@code AbstractToolLoopAgent.start}，跨线程传递点在 {@code ToolAgentRunner}；
 * 业务侧一般不需要自己 set。</p>
 *
 * @author Sharp
 * @since 2026-10-13
 */
public final class ModelCallContexts {

    private static final ThreadLocal<ModelCallContext> CURRENT = new ThreadLocal<>();

    private ModelCallContexts() {
    }

    /** 当前线程的身份快照，无则 null（调用方按"有则填"处理，不编造） */
    public static ModelCallContext current() {
        return CURRENT.get();
    }

    /** 设定当前线程身份；null 或三栏全空等同清除 */
    public static void set(ModelCallContext context) {
        if (context == null || context.isEmpty()) {
            CURRENT.remove();
            return;
        }
        CURRENT.set(context);
    }

    /** 清除当前线程身份（务必放在 finally） */
    public static void clear() {
        CURRENT.remove();
    }
}
