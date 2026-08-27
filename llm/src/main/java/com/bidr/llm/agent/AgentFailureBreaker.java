package com.bidr.llm.agent;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Title: AgentFailureBreaker
 * Description: 工具循环失败分类熔断器（任务级实例，随 AgentLoopOptions 传入）：
 * 工具返回失败文本时按规则集分类计数，同类失败累计达阈值且从未触发过 →
 * 返回该规则的 advice 全文，由引擎注入一条用户消息拉直模型方向（防同类
 * 失败上死磕空转——「列不存在连猜 6 次」这类模式在收口前就被打断）。
 * 机制在本类（计数/触发/一次性），语义在业务注入（正则规则与建议文案）：
 * 分类规则随链路配置（问数列校验类/资产生成协议类各配各的），框架不内置业务文案。
 * 线程模型：单会话单线程顺序调用（工具循环串行），无并发防护必要
 *
 * @author Sharp
 * @since 2026/8/23
 */
public class AgentFailureBreaker {

    /** 失败规则：name 供日志/触发文案，pattern 匹配工具返回文本（失败判定含框架异常文案
     *  与工具自返 error JSON 两种形态），threshold 累计命中次数，advice 触发后注入的指令 */
    public static final class Rule {
        final String name;
        final Pattern pattern;
        final int threshold;
        final String advice;

        public Rule(String name, String regex, int threshold, String advice) {
            this.name = name;
            this.pattern = Pattern.compile(regex, Pattern.CASE_INSENSITIVE);
            this.threshold = threshold;
            this.advice = advice;
        }

        public static Rule of(String name, String regex, int threshold, String advice) {
            return new Rule(name, regex, threshold, advice);
        }
    }

    private final List<Rule> rules;
    /** 规则名 → 累计命中次数（成功结果不计入也不清零：累计语义，一旦模式确立即提示一次） */
    private final Map<String, Integer> counts = new HashMap<>();

    public AgentFailureBreaker(List<Rule> rules) {
        this.rules = rules == null ? java.util.Collections.emptyList() : rules;
    }

    /**
     * 工具结果分类计数：失败文本（引擎异常文案「【工具调用失败】」或工具自返 error JSON）
     * 且命中某规则 → 该规则计数 +1；达阈值且首次 → 返回 advice（调用方注入用户消息）。
     * 返回 null = 无需动作。多条规则同轮触发时只取首条（文本通常只属一类）
     */
    public String onToolResult(String result) {
        if (rules.isEmpty() || result == null || result.isEmpty()) {
            return null;
        }
        boolean failure = result.contains("【工具调用失败】") || result.contains("\"error\"");
        if (!failure) {
            return null;
        }
        for (Rule r : rules) {
            if (r.pattern.matcher(result).find()) {
                int n = counts.merge(r.name, 1, Integer::sum);
                if (n == r.threshold) {
                    return "【失败模式提醒】同类失败（" + r.name + "）已累计 " + n + " 次。" + r.advice;
                }
                if (n > r.threshold) {
                    // 已触发过：不再重复注入（一次性语义），但继续计数供调用方日志观测
                    return null;
                }
                return null;
            }
        }
        return null;
    }
}
