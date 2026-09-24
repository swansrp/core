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

    /**
     * 工具调用结构化上报（与 {@link #log} 并行，不替代）：id 取模型给的 tool_call_id，
     * argumentsJson 为参数原文（JSON 文本，由消费侧决定解析口径）。
     * 默认空实现：轻链路（如维护问数）零改动即向后兼容；会话实现据此落
     * {@code AgentEvent.TOOL_CALL}，供前端统一过程树渲染。
     */
    default void onToolCall(String toolCallId, String toolName, String argumentsJson) {
    }

    /**
     * 工具返回结构化上报：resultText 为结果全文（失败亦走此口，文本含失败原因）。
     * 默认空实现，兼容性同上；会话实现落 {@code AgentEvent.TOOL_RESULT}。
     */
    default void onToolResult(String toolCallId, String toolName, String resultText) {
    }

    /**
     * 是否支持按 tool_call_id 回捞此前工具结果的原文全文——卸载机制的入场前置闸（I5：
     * 无回捞通道不卸载，宁可不省不可丢信息）。默认 false：轻链路（票据链/NONE）零改动即向后兼容；
     * 会话链 {@code AgentSessionContext#loopListener()} 覆写为 true（事件流持全文，按 id 只读回捞）
     */
    default boolean supportsToolResultRecall() {
        return false;
    }

    /**
     * 按 tool_call_id 回捞工具结果原文全文（与 {@link #supportsToolResultRecall()} 配套，
     * 内建 recallToolResult 工具的取数出口）：只读，不得向事件流写入任何内容（I13）。
     * 默认返回 null=未找到；会话实现顺序扫事件流 TOOL_RESULT 按 id 命中返回 output 全文
     */
    default String recallToolResult(String toolCallId) {
        return null;
    }
}
