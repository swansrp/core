package com.bidr.llm.agent;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Title: RunScopedRecallBuffer
 * Description: 框架自带的 run 作用域回捞通道——回捞的兜底数据源，暂存「模型已经看不到的工具结果原文」
 * （入场被卸载为指针的、被窗口驱逐压进摘要的），供内建 {@link AgentToolRecall} 按句柄取回。
 * 有了它，卸载/句柄/回捞不再依赖链路是否接了会话事件流：任何 {@code ToolAgentRunner.run()} 一行代码不加即得。
 * <p>不变式 I16：
 * ① 只存对模型不可见者——窗内可见原文、收口路径保原文（I10）一律不入本表，故占用不随窗内容量重复；
 * ② 生命周期严格等于一次 {@code run()}（随 RunStats 局部变量释放），绝不跨 run 引用、绝不被业务侧持有；
 * ③ 只读回捞，绝不写会话事件流（不破 I13 单写者）。
 * <p>容量：上限字（AGENT_TOOL_RECALL_BUFFER_CHARS）按插入序 FIFO 挤出最旧条目——长 run 累积的不可见原文
 * 可远超上下文窗口，不设上限即无界堆占用；至少保留一条（防单条即超限时缓冲全空）。被挤出的句柄回捞
 * 返回「未找到」指令文本（与幻觉句柄同一条路径，不击穿循环），该最坏情形由本类 {@link #dropped()} 透出。
 * <p>与会话事件流的关系是**优先级的叠加**而非替代：取数先走事件流（跨 run、跨实例可捞），
 * miss 或链路本无通道时才落到本表 ⇒ 会话链行为与引入本类之前逐字一致。
 *
 * @author Sharp
 * @since 2026/9/25
 */
final class RunScopedRecallBuffer {

    private final Map<String, String> originals = new LinkedHashMap<>();
    private final int maxChars;
    private long bufferedChars;
    private int dropped;

    RunScopedRecallBuffer() {
        this(AgentContextBudget.recallBufferChars());
    }

    RunScopedRecallBuffer(int maxChars) {
        this.maxChars = Math.max(maxChars, 0);
    }

    /** 归档一条对模型不可见的原文；同 id 重复归档以先到者为准（cachedTools 重调同 id 文本同文）；
     *  上限 0（运维填非法值经 resolveFloor 已回落，此为防御纵深）= 关闭归档，本表恒空 */
    void archive(String toolCallId, String text) {
        if (toolCallId == null || text == null || maxChars <= 0 || originals.containsKey(toolCallId)) {
            return;
        }
        originals.put(toolCallId, text);
        bufferedChars += text.length();
        // 至少保留一条：单条即超上限时不自我清空，否则最后一条原文刚归档就不可捞
        while (bufferedChars > maxChars && originals.size() > 1) {
            String eldest = originals.keySet().iterator().next();
            bufferedChars -= originals.remove(eldest).length();
            dropped++;
        }
    }

    /** 按句柄取原文；未命中（含被 FIFO 挤出、幻觉句柄）返回 null 由工具侧回指令式错误文本 */
    String get(String toolCallId) {
        return toolCallId == null ? null : originals.get(toolCallId);
    }

    /** 本表当前可回捞句柄（插入序），供「未找到」时给模型的清单 */
    List<String> handles() {
        return new ArrayList<>(originals.keySet());
    }

    int size() {
        return originals.size();
    }

    /** 因容量上限被挤出的条数（计量与告警用，>0 即存在「句柄在但原文已被挤出」的有界退化） */
    int dropped() {
        return dropped;
    }
}
