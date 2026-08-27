package com.bidr.llm.agent;

/**
 * Title: AgentLoopResult
 * Description: 工具循环会话结果：最终文本 + 实际轮次 + 运行模式。
 * STOPPED 表示会话被停止信号/线程中断收口，text 为空，调用方应走停止收口而非解析产出
 *
 * @author Sharp
 * @since 2026/8/20
 */
public class AgentLoopResult {

    /** 运行模式：工具循环完成 / 模型不支持工具回落直连 / 被停止信号收口 */
    public enum Mode {
        TOOL_LOOP, DIRECT_FALLBACK, STOPPED
    }

    private final String text;
    private final int rounds;
    private final Mode mode;

    private AgentLoopResult(String text, int rounds, Mode mode) {
        this.text = text;
        this.rounds = rounds;
        this.mode = mode;
    }

    public static AgentLoopResult toolLoop(String text, int rounds) {
        return new AgentLoopResult(text, rounds, Mode.TOOL_LOOP);
    }

    public static AgentLoopResult directFallback(String text) {
        return new AgentLoopResult(text, 0, Mode.DIRECT_FALLBACK);
    }

    public static AgentLoopResult stopped(int rounds) {
        return new AgentLoopResult(null, rounds, Mode.STOPPED);
    }

    /** 最终文本（STOPPED 时为 null） */
    public String getText() {
        return text;
    }

    /** 实际消耗轮次（直连回落为 0） */
    public int getRounds() {
        return rounds;
    }

    public Mode getMode() {
        return mode;
    }

    public boolean isStopped() {
        return mode == Mode.STOPPED;
    }
}
