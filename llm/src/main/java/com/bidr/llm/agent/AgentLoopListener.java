package com.bidr.llm.agent;

/**
 * Title: AgentLoopListener
 * Description: 工具循环业务钩子：过程日志回调 + 停止检查（每轮循环头调用一次，
 * 返回 true 时引擎收口返回 STOPPED 结果）。业务侧借此接进度窗与分布式停止键
 *
 * @author Sharp
 * @since 2026/8/20
 */
public interface AgentLoopListener {

    /** 空实现适配器：无日志、永不停止、不暂停（维护问数等轻链路使用） */
    AgentLoopListener NONE = new AgentLoopListener() {
        @Override
        public void log(String line) {
        }

        @Override
        public boolean shouldStop() {
            return false;
        }
    };

    /** 过程日志上报（每轮请求/响应/工具调用摘要，前端进度窗可见任务在干活） */
    void log(String line);

    /** 停止检查：引擎每轮循环头轮询，true 即收口 */
    boolean shouldStop();

    /**
     * 暂停检查（引擎每轮循环头调用，shouldStop 之后）：未暂停直接返回 null 继续；
     * 暂停期间阻塞等待（实现方自管醒查周期，等待中 shouldStop 置真须返回 null 让停止检查收口）；
     * 恢复时返回非空指导语则引擎注入为用户补充消息后继续循环。默认不暂停
     */
    default String awaitResumeIfPaused() {
        return null;
    }

    /**
     * 业务终止检查（每轮工具全部执行回填后调用）：业务侧已拿到终结信号（如 finish 工具已收口）
     * 时返回 true，引擎立即结束循环不再发起下一轮（省一轮大上下文回传）；默认不终止。
     * 返回 true 时结论文本取 {@link #terminalText()}
     */
    default boolean shouldTerminate() {
        return false;
    }

    /** 业务终止时的结论文本（与 shouldTerminate 配套；null 时引擎用空文本收口） */
    default String terminalText() {
        return null;
    }
}
