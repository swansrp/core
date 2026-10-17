package com.bidr.llm.agent;

import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.ToolExecutionResultMessage;
import dev.langchain4j.data.message.UserMessage;

import java.util.List;

/**
 * Title: ContextTokenEstimator
 * Description: 上下文 token 估算器（纯函数，无状态）——内网无真 tokenizer，估算式一律偏保守
 * （高估，真实 inputTokenCount ≤ 估算值，方向性纪律见 I8）：
 * <pre>
 * estTokens(text)     = ceil( CJK字数 × 1.0 + 其他字符 × 0.3 )
 * estTokens(messages) = Σ estTokens(每条正文 + 工具调用 arguments JSON) + 4 × 条数
 * estTokens(specs)    = ceil( Σ estTokens(name + description + 参数schema序列化) × 1.2 )
 * </pre>
 * 误差方向取高不取低的理由：高估 → 早裁，代价是个别旧轮次提前进摘要（digest 兜底 + 句柄可回捞，
 * 信息不丢）；低估 → 撑爆端点，代价是整条会话 400 失败无结论。两向代价完全不对称。
 * 真实误差由计量日志（估算）与端点 TokenUsage.inputTokenCount（实测）配对校准收敛。
 * 引用不变式：I8（估算只高不低）
 *
 * @author Sharp
 * @since 2026/9/25
 */
public final class ContextTokenEstimator {

    /** 每条消息固定结构开销（role/分隔/tool-call 结构标记）估算补偿（token/条） */
    static final int TOKENS_PER_MESSAGE = 4;
    /** CJK 字符权重：1 字 = 1 token（真实 qwen 约 0.6~1.0 字/token，取 1.0 恒不低估） */
    static final double WEIGHT_CJK = 1.0;
    /** 非 CJK 字符权重：1 字符 = 0.3 token（≈3.3 字符/token，比英文真实 4 字符/token 保守约 33%） */
    static final double WEIGHT_OTHER = 0.3;
    /** 工具定义安全系数（百分比）：specs 每轮全量重发，×1.2 兜 schema 序列化开销 */
    static final int SPEC_SAFETY_PERCENT = 120;

    private ContextTokenEstimator() {
    }

    /**
     * 单段文本估算 token（I8：误差方向一律偏保守）。null/空串返回 0 不抛。
     * <p>纪律说明（有意的例外）：本类权重常量<b>刻意不进 Param 体系</b>——估算式与其常量是一个整体，
     * 半改（只改权重不改偏移/系数）会造成「改了权重没改偏移」的误导；且它是机制内部校准量而非运营可调项，
     * 运营灰度出入口在 LlmParam 的预算/阈值五键（见 AgentContextBudget）。这是「阈值归 Param」纪律的一处有意例外。
     */
    public static int estimateText(String text) {
        if (text == null || text.isEmpty()) {
            return 0;
        }
        long cjk = 0;
        long other = 0;
        for (int i = 0; i < text.length(); i++) {
            if (isCjk(text.charAt(i))) {
                cjk++;
            } else {
                other++;
            }
        }
        return (int) Math.ceil(cjk * WEIGHT_CJK + other * WEIGHT_OTHER);
    }

    /** 单条消息估算：正文（Ai 消息含每个工具调用 arguments JSON）+ 固定结构开销 */
    public static int estimateMessage(ChatMessage m) {
        if (m == null) {
            return TOKENS_PER_MESSAGE;
        }
        int est = estimateText(textOf(m)) + TOKENS_PER_MESSAGE;
        if (m instanceof AiMessage) {
            AiMessage ai = (AiMessage) m;
            if (ai.hasToolExecutionRequests()) {
                for (ToolExecutionRequest r : ai.toolExecutionRequests()) {
                    est += estimateText(r.arguments());
                }
            }
        }
        return est;
    }

    /** 消息清单估算：Σ 每条（正文 + 工具参数）+ 4 × 条数（I8）。null/空返回 0 */
    public static int estimateMessages(List<ChatMessage> messages) {
        if (messages == null || messages.isEmpty()) {
            return 0;
        }
        int est = 0;
        for (ChatMessage m : messages) {
            est += estimateMessage(m);
        }
        return est;
    }

    /** 工具定义清单估算：name+description+参数 schema 序列化，整体 ×1.2（I8）。null/空返回 0 */
    public static int estimateSpecs(List<ToolSpecification> specs) {
        if (specs == null || specs.isEmpty()) {
            return 0;
        }
        long raw = 0;
        for (ToolSpecification s : specs) {
            if (s == null) {
                continue;
            }
            StringBuilder sbuf = new StringBuilder();
            if (s.name() != null) {
                sbuf.append(s.name());
            }
            if (s.description() != null) {
                sbuf.append(' ').append(s.description());
            }
            if (s.parameters() != null) {
                sbuf.append(' ').append(s.parameters().toString());
            }
            raw += estimateText(sbuf.toString());
        }
        return (int) Math.ceil(raw * SPEC_SAFETY_PERCENT / 100.0);
    }

    /** 消息清单字符数（正文 + 工具参数 JSON，不含结构开销）——计量日志与对照表口径 */
    public static int countChars(List<ChatMessage> messages) {
        if (messages == null || messages.isEmpty()) {
            return 0;
        }
        long chars = 0;
        for (ChatMessage m : messages) {
            chars += textOf(m).length();
            if (m instanceof AiMessage && ((AiMessage) m).hasToolExecutionRequests()) {
                for (ToolExecutionRequest r : ((AiMessage) m).toolExecutionRequests()) {
                    chars += r.arguments() == null ? 0 : r.arguments().length();
                }
            }
        }
        return (int) Math.min(chars, Integer.MAX_VALUE);
    }

    /** 消息正文提取：System/User/工具结果/Ai 取文本；其余类型 toString 兜底（宁高不低） */
    private static String textOf(ChatMessage m) {
        if (m instanceof SystemMessage) {
            return ((SystemMessage) m).text();
        }
        if (m instanceof UserMessage) {
            UserMessage um = (UserMessage) m;
            // 多模态（文本+图片）消息 singleText() 会抛 IllegalArgumentException 击穿估算/循环，先判后回退 toString（宁高不低）
            return um.hasSingleText() ? um.singleText() : String.valueOf(um);
        }
        if (m instanceof AiMessage) {
            String t = ((AiMessage) m).text();
            return t == null ? "" : t;
        }
        if (m instanceof ToolExecutionResultMessage) {
            return ((ToolExecutionResultMessage) m).text();
        }
        return String.valueOf(m);
    }

    /** CJK 判定（含全角标点/假名/谚文/半角片假名区）：计入宁高不低的一侧 */
    private static boolean isCjk(char c) {
        return (c >= '\u3000' && c <= '\u303F')
                || (c >= '\u3400' && c <= '\u4DBF')
                || (c >= '\u4E00' && c <= '\u9FFF')
                || (c >= '\u3040' && c <= '\u30FF')
                || (c >= '\uAC00' && c <= '\uD7AF')
                || (c >= '\uFF00' && c <= '\uFFEF');
    }
}
