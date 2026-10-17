package com.bidr.insight.smartquery.meta;

import com.bidr.insight.smartquery.layer.ValueDomainDef;
import com.bidr.kernel.utils.FuncUtil;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.HashSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Title: CommentValueParser
 * Description: 列备注语义解析（纯静态工具，从 SmartAgentMetaService 拆出）：
 * 码值映射（0=启用 1=停用 / 1:男 2:女 / 0传统 1创新）、哨兵值（-1代表无，本数仓特有约定，
 * 纯备注驱动不反推）、口语别名（括注/斜杠同义词）三类确定性提取，骨架建域与维度别名共用
 *
 * @author Sharp
 * @since 2026/8/23
 */
public final class CommentValueParser {

    /** 备注码值模式（带分隔符，find 全文抽取）："1:男 / 0=启用 / 2-停用 / 3：作废"，
     *  兼容 "性别(1:男 2:女)" 前缀包裹与 "启用=0 / 男:1" 中文在左侧的写法；
     *  纯中文对（未开工:在建）两端都无字母数字时不匹配——分不清码和名，留给 LLM 备注枚举层 */
    private static final Pattern COMMENT_PAIR_SPLIT = Pattern.compile(
            "([A-Za-z0-9_]+)\\s*[-=～~:：]\\s*([\\u4e00-\\u9fa5][\\u4e00-\\u9fa5A-Za-z0-9/()（）·、&%\\-]*)"
            + "|([\\u4e00-\\u9fa5][\\u4e00-\\u9fa5/()（）·、&%\\-]*)\\s*[-=～~:：]\\s*([A-Za-z0-9_]+)");

    /** 备注码值模式（直连无分隔符，片段级匹配）："0传统 1创新" */
    private static final Pattern COMMENT_PAIR_PLAIN = Pattern.compile(
            "^([A-Za-z0-9_]+)([\\u4e00-\\u9fa5][\\u4e00-\\u9fa5A-Za-z0-9/()（）·、&%\\-]*)$");

    /** 备注哨兵值模式（本数仓特有约定，实证 36 处："合同编码，-1代表无 / 员工号,-1代表外部人员"）：
     *  纯备注驱动——仅当注释明写"-N 代表 X"才收录，绝不反推（正常配置下"无"多为 null 或无映射，
     *  -1 哨兵少见，不得泛化为通用规则）；收录后查"无合同"经码值域 findValue 命中 ='-1'，
     *  独立于"至少两对"枚举门槛 */
    private static final Pattern COMMENT_SENTINEL = Pattern.compile(
            "(-\\d+)\\s*代表\\s*([\\u4e00-\\u9fa5A-Za-z0-9/]+)");

    /** 备注中枚举取值说明段特征（数字码/true-false + 分隔符）：命中即码值语义而非别名 */
    private static final Pattern ALIAS_ENUM_SEGMENT = Pattern.compile("(\\d|true|false).{0,2}[-:：]");

    /** 备注单位词（长词在前防「万元」被「元」抢匹配）；后随中文负前瞻防「元件/元宇宙」类误切 */
    private static final String UNIT_WORDS = "(万元|亿元|百万元|亿|元|%)(?![\\u4e00-\\u9fa5])";

    /** 显式单位声明："单位：万元 / 单位(元) / 单位为元 / 以万元为单位"（无冒号写法要求紧跟单位词） */
    private static final Pattern UNIT_DECLARED = Pattern.compile(
            "单位\\s*[（(]?\\s*[:：为是]\\s*" + UNIT_WORDS + "|以\\s*(万元|亿元|亿|元)\\s*为单位");

    /** 纯单位括注："合同金额（万元）/ 金额(元)"（括内只有单位词，防「(元数据)」类误命中） */
    private static final Pattern UNIT_PAREN = Pattern.compile("[（(]\\s*" + UNIT_WORDS + "\\s*[）)]");

    private CommentValueParser() {
    }

    /** 列备注 → 码值映射（码值域路径①，优先于 GROUP BY 采样）：支持
     * "0=启用 1=停用 / 1:男,2:女 / 性别(1:男 2:女) / 0传统 1创新" 等常见写法。
     * 防误判：至少两对才出映射（单项多半是描述），label 全同（如 "10分 20分"）不算映射 */
    public static List<ValueDomainDef.DomainValue> parseCommentCodeVals(String comment) {
        if (FuncUtil.isEmpty(comment)) {
            return Collections.emptyList();
        }
        Map<String, ValueDomainDef.DomainValue> byCode = new LinkedHashMap<>();
        Matcher m = COMMENT_PAIR_SPLIT.matcher(comment);
        while (m.find()) {
            // 码值域契约 code=物理存储值：左侧命中码在左，否则（中文=码写法）码在右侧
            String code = m.group(1) != null ? m.group(1) : m.group(4);
            String label = m.group(1) != null ? m.group(2) : m.group(3);
            byCode.putIfAbsent(code, domainValue(code, label));
        }
        if (byCode.size() < 2) {
            // 直连写法兜底：按分隔符切片段后逐段匹配
            byCode.clear();
            for (String item : comment.split("[,，;；、\\s]+")) {
                Matcher p = COMMENT_PAIR_PLAIN.matcher(item.trim());
                if (p.matches()) {
                    byCode.putIfAbsent(p.group(1), domainValue(p.group(1), p.group(2)));
                }
            }
        }
        // 哨兵值（-1代表无）：单值也收录（过滤"无 X"语义用），不参与枚举门槛计数
        boolean hasSentinel = false;
        Matcher s = COMMENT_SENTINEL.matcher(comment);
        while (s.find()) {
            byCode.putIfAbsent(s.group(1), domainValue(s.group(1), s.group(2)));
            hasSentinel = true;
        }
        if (byCode.isEmpty()) {
            return Collections.emptyList();
        }
        Set<String> labels = new HashSet<>();
        byCode.values().forEach(v -> labels.add(v.getLabel()));
        // 枚举至少两对不同标签才成立；仅命中哨兵值时也出域（防普通单对备注混入）
        return (labels.size() >= 2 || hasSentinel) ? new ArrayList<>(byCode.values()) : Collections.emptyList();
    }

    /** 码值对构造 */
    public static ValueDomainDef.DomainValue domainValue(String code, String label) {
        ValueDomainDef.DomainValue dv = new ValueDomainDef.DomainValue();
        dv.setCode(code);
        dv.setLabel(label);
        return dv;
    }

    /** 列备注 → 度量单位（确定性提取，配置自查用）：显式声明（单位：万元/以元为单位）优先，
     *  其次纯单位括注（金额(万元)）；无把握写法（裸词干后缀等）不提——宁可漏报不误报，
     *  探测结果只进「疑似清单」供人逐条裁决，不自动改配置 */
    public static String extractUnit(String comment) {
        if (FuncUtil.isEmpty(comment)) {
            return null;
        }
        Matcher m = UNIT_DECLARED.matcher(comment);
        if (m.find()) {
            return m.group(1) != null ? m.group(1) : m.group(2);
        }
        Matcher p = UNIT_PAREN.matcher(comment);
        if (p.find()) {
            return p.group(1);
        }
        return null;
    }

    /** 列备注 → 口语别名（确定性提取，供问数按业务叫法检索命中）：
     *  取第一个分号前的主体段，挖其中的括注内容与斜杠分隔同义词；
     *  枚举取值说明（含数字码/true-false 的段）不提取，码值语义由码值域承载（findValue 按值反查） */
    public static List<String> extractAliases(String comment, String displayName) {
        List<String> out = new ArrayList<>();
        if (FuncUtil.isEmpty(comment)) {
            return out;
        }
        String body = comment.split("[;；]")[0];
        Matcher paren = Pattern.compile("[（(]([^）)]+)[）)]").matcher(body);
        StringBuffer rest = new StringBuffer();
        while (paren.find()) {
            addAlias(out, paren.group(1), displayName);
            paren.appendReplacement(rest, " ");
        }
        paren.appendTail(rest);
        // 去括注后主体的斜杠同义词（无斜杠时不切：单段即展示名本身）
        String[] parts = rest.toString().split("[/／]");
        if (parts.length > 1) {
            for (String p : parts) {
                addAlias(out, p, displayName);
            }
        }
        return out.size() > 6 ? new ArrayList<>(out.subList(0, 6)) : out;
    }

    /** 别名入列：去空白/限长/去重（与展示名相同或枚举说明段不收） */
    private static void addAlias(List<String> out, String raw, String displayName) {
        if (raw == null) {
            return;
        }
        String a = raw.trim();
        if (a.isEmpty() || a.length() > 12 || a.equals(displayName) || out.contains(a)) {
            return;
        }
        if (ALIAS_ENUM_SEGMENT.matcher(a).find()) {
            return;
        }
        out.add(a);
    }
}
