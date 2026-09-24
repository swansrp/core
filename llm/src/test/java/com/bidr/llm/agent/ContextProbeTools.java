package com.bidr.llm.agent;

import dev.langchain4j.agent.tool.Tool;

/**
 * Title: ContextProbeTools
 * Description: L1 治理层 1 合成夹具（计划 §7.1 断言层）：bigReport(topic) 返回中文为主的定长
 * 「解析报告」，原文深处（默认 60% 偏移处，指针头尾预览不可见、回捞上限内可达）藏唯一探针
 * {@code KEY-<topic>-8888}——回捞零损失的可验证探针。同 topic 结果确定性一致（cachedTools
 * 场景可用）；调用次数透出供缓存命中核验。零外部依赖，供 §6.4/§6.5 与对照数字表共用
 *
 * @author sharp
 * @since 2026/9/25
 */
public class ContextProbeTools {

    /** 计划 §7.1 标准报告长度（中文为主约 30k 字符） */
    public static final int DEFAULT_REPORT_CHARS = 30000;

    /** 探针相对偏移（0~1）：0.6 → 30k 报告时落在 18000 字处（>预览头 800、>尾 200、<回捞上限 20000） */
    private static final double KEY_RATIO = 0.6;

    private final int reportChars;
    private int bigReportCalls;

    public ContextProbeTools() {
        this(DEFAULT_REPORT_CHARS);
    }

    public ContextProbeTools(int reportChars) {
        this.reportChars = reportChars;
    }

    public int bigReportCalls() {
        return bigReportCalls;
    }

    /** 同 topic 结果确定性：正文按主题字派生 + 唯一探针 KEY-<topic>-8888 只存在于原文 */
    public static String report(int totalChars, String topic) {
        String key = "KEY-" + topic + "-8888";
        StringBuilder sb = new StringBuilder(totalChars);
        sb.append("【").append(topic).append(" 主题解析报告】以下为逐段分析正文。");
        char seed = (char) ('一' + (topic.isEmpty() ? 0 : topic.charAt(0) % 100));
        int fill = totalChars - key.length();
        int at = sb.length();
        while (at < fill) {
            sb.append("第").append(at / 100 + 1).append("段").append(seed).append('，');
            at = sb.length();
        }
        sb.setLength(fill);
        sb.insert((int) (fill * KEY_RATIO), key);
        return sb.toString();
    }

    @Tool("按主题生成一份完整的中文解析报告（全文含主题专属校验标记）")
    public String bigReport(String topic) {
        bigReportCalls++;
        String t = topic == null || topic.trim().isEmpty() ? "X" : topic.trim();
        return report(reportChars, t);
    }
}
