package com.bidr.llm.agent;

import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Title: AgentToolRecall
 * Description: 框架级内建「句柄回捞」工具（风格对齐 AgentAskUserTool）：工具结果被入场卸载为
 * 指针、或被窗口裁切压进【探索记录摘要】（A9）后，模型凭指针首行 tool_call_id=/摘要行内
 * 「句柄=」的原文经本工具取回全文——「卸载即收益、回捞是有界最坏成本」
 * 的混合方案出口（计划岔路 1）。三层硬闸：
 * ① 单 run 次数上限（AGENT_TOOL_RECALL_MAX_PER_RUN，超限只回「回捞预算已用尽请收口」指令文本）；
 * ② 单次返回字符上限（AGENT_TOOL_RECALL_MAX_CHARS，超出截断并提示前端过程树可看全文）；
 * ③ 回捞结果永不二次卸载（工具名在 neverOffload 集，I6——防「捞→又是指针」无意义回路）。
 * 取数只读 listener 回捞通道（会话事件流，I13）；未命中返回指令式错误文本含本 run 已知句柄清单
 * （最多 5 个），一次即把模型拉回真实 id 集，不击穿循环。本类实例为 run 作用域（ToolAgentRunner
 * 按 run 新建并注册），内部计数 AtomicInteger 与 I14 runner 无状态纪律无冲突
 *
 * @author Sharp
 * @since 2026/9/25
 */
public class AgentToolRecall {

    /** 工具名（==@Tool 方法名；neverOffload 集成员，I6） */
    public static final String TOOL_NAME = "recallToolResult";

    /** 未命中时回给模型的可用句柄清单条数上限（防刷屏） */
    private static final int HANDLE_HINT_MAX = 5;

    /** 回捞取数通道（会话 listener，只读 events 按 id 命中，I13） */
    private final AgentLoopListener source;

    /** 单 run 次数上限闸（超限只提示收口；0=禁止回捞，防御纵深只更严不失控） */
    private final int maxPerRun;

    /** 单次返回字符上限闸（超出截断；0=禁止回填，返回明确提示文本而非空串） */
    private final int maxChars;

    /** 本 run 已卸载句柄台账（runner run() 内局部 List 引用，未命中时给模型可用清单） */
    private final List<String> offloadedHandles;

    /** 本 run 已调用次数（run 作用域实例自带，非 runner 字段，I14） */
    private final AtomicInteger used = new AtomicInteger();

    public AgentToolRecall(AgentLoopListener source, int maxPerRun, int maxChars, List<String> offloadedHandles) {
        this.source = source;
        this.maxPerRun = Math.max(maxPerRun, 0);
        this.maxChars = Math.max(maxChars, 0);
        this.offloadedHandles = offloadedHandles;
    }

    /** 计量日志透出用（累计回捞次数） */
    public int used() {
        return used.get();
    }

    @Tool("按 tool_call_id 取回此前工具结果的原文全文。句柄两个来源：上下文形如"
            + "【已卸载 tool_call_id=…】指针首行 tool_call_id= 后的原文，或【探索记录摘要】行内"
            + "「句柄=」后的原文；预览/摘要不足以支撑你的结论时调用本工具取全文，"
            + "一次只回捞一个句柄，禁止用它重复拉取同一句柄")
    public String recallToolResult(@P("tool_call_id 句柄原文：指针首行 tool_call_id= 后或摘要行内「句柄=」后的内容") String toolCallId) {
        if (toolCallId == null || toolCallId.trim().isEmpty()) {
            return "拒绝：tool_call_id 不能为空，须为指针首行 tool_call_id= 后的原文句柄";
        }
        String id = toolCallId.trim();
        if (used.incrementAndGet() > maxPerRun) {
            return "回捞预算已用尽（上限 " + maxPerRun + " 次），请基于已有信息（指针预览与已回捞原文）收口，"
                    + "不要再尝试回捞";
        }
        String text = source.recallToolResult(id);
        if (text == null) {
            // 模型幻觉/改写句柄的对策：指令式错误 + 本 run 真实句柄清单，一次拉回真实 id 集，不击穿循环
            return "未找到句柄 " + id + " 对应的原文（可能句柄有误，或原文未落库）。本 run 已卸载句柄（最多 "
                    + HANDLE_HINT_MAX + " 个）：" + handleHint()
                    + "。请核对指针首行 tool_call_id= 后的原文后最多再试一次；仍找不到则基于预览保守作答并注明不确定，禁止反复重试";
        }
        // maxChars=0 即禁止回填（闸门键经 resolveFloor 已回落保守默认，此为防御纵深）：
        // 返回明确提示而非空串/截断空内容，避免模型见到空结果反复重试
        if (maxChars <= 0) {
            return "（回捞已关闭：单次回填字符上限为 0，请基于指针预览保守作答，不要再尝试回捞）";
        }
        if (text.length() > maxChars) {
            return text.substring(0, maxChars) + "\n（原文 " + text.length() + " 字，本次返回 " + maxChars
                    + " 字，余下可在前端过程树查看全文）";
        }
        return text;
    }

    private String handleHint() {
        if (offloadedHandles == null || offloadedHandles.isEmpty()) {
            return "（本 run 尚无已卸载句柄）";
        }
        int from = Math.max(0, offloadedHandles.size() - HANDLE_HINT_MAX);
        return String.join("、", offloadedHandles.subList(from, offloadedHandles.size()));
    }
}
