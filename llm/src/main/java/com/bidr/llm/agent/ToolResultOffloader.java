package com.bidr.llm.agent;

import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Title: ToolResultOffloader
 * Description: 工具结果入场卸载纯函数（无状态）：超长工具结果在 messages.add 之前替换为
 * 「预览 + 句柄」指针文本，原文全文仍经 onToolResult 进会话事件流（I4），模型按句柄经内建
 * recallToolResult 工具回捞。指针格式（首行整体 &lt;100 字符以扛住 digest brief(…,100) 截断，
 * tool_call_id 落在前 60 字符内，I7）：
 * <pre>
 * 【已卸载 tool_call_id=call-1 原 12345 字/约 4300 token｜要原文调 recallToolResult("call-1")】
 * &lt;头预览 previewChars×0.8 字&gt;
 * ……（中间 N 字已省略）……
 * &lt;尾预览 previewChars×0.2 字&gt;
 * 提示：重复同参调用本工具只会再得一个指针；预览不足以定论时必须回捞，禁止凭预览猜测。
 * </pre>
 * 引用不变式：I2（只改文本不改拓扑——本类只产文本）、I5（A10 起由框架两级通道保证卸载必有取回路径）、
 * I6（neverOffload 命中不卸载）、I7（句柄前置可摘要）、I9（offloadChars=0 一律不卸载）
 *
 * @author Sharp
 * @since 2026/9/25
 */
public final class ToolResultOffloader {

    /** 指针首行固定前缀（isPointer 识别自身产物的判据） */
    static final String POINTER_PREFIX = "【已卸载 tool_call_id=";

    /** 句柄反解：从指针（或其经 brief 截断后的头部残片）提取 tool_call_id，I7 的下游保障 */
    private static final Pattern HANDLE = Pattern.compile("tool_call_id=(\\S+?)[\"'\\s】）)、,，.。;；:：]");
    /** 兜底：残片在 id 中部被截断不可能的（id 恒在前 60 字符内），贪婪到行尾兜非标准 id */
    private static final Pattern HANDLE_TAIL = Pattern.compile("tool_call_id=(\\S+)");

    private ToolResultOffloader() {
    }

    /**
     * 入场卸载判定（I6/I9）：阈值正值 且 结果长度严格超过阈值 且 工具不在永不卸载集。
     * 长度==阈值不卸载（边界语义：超过才卸载）。
     * <p>A10 起不再要求链路提供回捞通道：{@code ToolAgentRunner} 自带 run 作用域缓冲兜底（I16），
     * 卸载必有取回路径 ⇒ I5 从"无外部通道不卸载"升级为"框架两级通道恒在"
     *
     * @param toolName     工具名
     * @param resultText   结果原文
     * @param offloadChars 卸载阈值（0=关闭）
     * @param neverOffload 永不卸载集（pinnedTools ∪ askUser ∪ recallToolResult）
     */
    public static boolean shouldOffload(String toolName, String resultText, int offloadChars,
                                        Set<String> neverOffload) {
        if (offloadChars <= 0 || resultText == null) {
            return false;
        }
        if (toolName != null && neverOffload != null && neverOffload.contains(toolName)) {
            return false;
        }
        return resultText.length() > offloadChars;
    }

    /**
     * 生成指针文本（格式契约见类注释）。
     * 前置硬校验：组装结果不短于原文时放弃卸载返回 null（小结果 + 超长 id 的病态情形，防反向膨胀）；
     * 首行超长（超长 id 破坏 I7 句柄前置）同样放弃卸载。
     *
     * @param toolCallId      模型侧 tool_call_id 原文（回捞唯一句柄，I7 保证截断存活）
     * @param toolName        工具名（仅提示语参考，不参与豁免判定——判定在 shouldOffload）
     * @param originalChars   原文字符数
     * @param estimatedTokens 原文估算 token（ContextTokenEstimator 口径，I8）
     * @param resultText      原文（取头尾预览）
     * @param previewChars    头尾预览合计字数（头 8 成尾 2 成）
     */
    public static String pointerOf(String toolCallId, String toolName, int originalChars,
                                   int estimatedTokens, String resultText, int previewChars) {
        if (resultText == null || toolCallId == null) {
            return null;
        }
        int head = Math.max(1, previewChars * 8 / 10);
        int tail = Math.max(1, previewChars / 5);
        String first = POINTER_PREFIX + toolCallId + " 原 " + originalChars + " 字/约 " + estimatedTokens
                + " token｜要原文调 recallToolResult(\"" + toolCallId + "\")】";
        StringBuilder sb = new StringBuilder(first).append('\n');
        int omitted;
        if (resultText.length() <= head + tail) {
            sb.append(resultText);
            omitted = 0;
        } else {
            omitted = resultText.length() - head - tail;
            sb.append(resultText, 0, head)
                    .append("\n……（中间 ").append(omitted).append(" 字已省略）……\n")
                    .append(resultText, resultText.length() - tail, resultText.length());
        }
        sb.append("\n提示：重复同参调用 ").append(toolName == null ? "本工具" : toolName)
                .append(" 只会再得一个指针；预览不足以定论时必须调 recallToolResult 取全文，禁止凭预览猜测。");
        String pointer = sb.toString();
        // 硬校验一：指针不比原文短 → 卸载无收益反膨胀，放弃（小结果 + 长 id 病态情形）
        if (pointer.length() >= resultText.length()) {
            return null;
        }
        // 硬校验二（I7）：首行 tool_call_id 必须落在前 60 字符内且首行整体 <100 字符，
        // 保证经 brief(…,100)（digest）/brief(…,300)（日志）任意头部截断后句柄仍完整可反解
        int idPos = first.indexOf(toolCallId);
        if (idPos < 0 || idPos + toolCallId.length() > 60 || first.length() >= 100) {
            return null;
        }
        return pointer;
    }

    /** 识别自身产物（回捞结果不二次卸载、收口路径核验等场景的判据） */
    public static boolean isPointer(String text) {
        return text != null && text.startsWith(POINTER_PREFIX);
    }

    /**
     * 从指针文本（或其 brief 截断残片）反解 tool_call_id；非指针/无法识别返回 null。
     * 兼容截断形态：`tool_call_id=call-1 原…`（id 后随空白）与 `tool_call_id=call-1】`（异常短指针）
     */
    public static String handleOf(String pointerText) {
        if (pointerText == null) {
            return null;
        }
        Matcher m = HANDLE.matcher(pointerText);
        if (m.find()) {
            return m.group(1);
        }
        m = HANDLE_TAIL.matcher(pointerText);
        return m.find() ? m.group(1) : null;
    }
}
