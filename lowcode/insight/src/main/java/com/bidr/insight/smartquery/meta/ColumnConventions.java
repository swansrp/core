package com.bidr.insight.smartquery.meta;

import com.bidr.insight.smartquery.layer.EntityDef;
import com.bidr.kernel.utils.FuncUtil;
import lombok.extern.slf4j.Slf4j;

import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Title: ColumnConventions
 * Description: 数仓命名/类型约定（纯静态工具，从 SmartAgentMetaService 拆出）：
 * 表名后缀快照语义（dyf/dyi/dmf/dmi…）、时间分区列粒度（dy/dm/dd）、技术时间戳模式、
 * 编码↔名称同词干配对、垃圾维度判定、维度/实体去重命名、类型映射与表名拆分——
 * 骨架前置与生成落盘共用的确定性规则，全量扫仓实证（2026-08-23）
 *
 * @author Sharp
 * @since 2026/8/23
 */
@Slf4j
public final class ColumnConventions {

    /** 编码类字段尾缀（确定性配对的 code 侧，同词干匹配名称类字段） */
    public static final List<String> CODE_SUFFIXES = Arrays.asList("_code", "_no");

    /** 名称类字段尾缀（确定性配对的 label 侧） */
    public static final List<String> LABEL_SUFFIXES = Arrays.asList("_name", "_nm", "_mc", "_label", "_desc");

    /** 技术时间戳列名模式（create/update/etl 等 + 时间后缀）：对问数无分组/过滤意义 */
    private static final Pattern TECH_TIME_COL = Pattern.compile(
            "^gmt_.+$|.*(create|created|update|updated|modify|modified|insert|etl|load|sync).*(time|date|dt|ts)$");

    /** 表名后缀 → 快照类型（数仓治理层约定，2026-08-23 全量扫仓实证）：全量/增量语义决定跨期查询是否累加，
     *  标签自带用法说明随实体元信息注入提示词；加新后缀在此加一条目 */
    private static final Map<String, String> SNAPSHOT_SUFFIX_DICT = buildSnapshotSuffixDict();

    private ColumnConventions() {
    }

    private static Map<String, String> buildSnapshotSuffixDict() {
        Map<String, String> m = new LinkedHashMap<>();
        m.put("dyf", "年全量，直接取目标期");
        m.put("dyi", "年增量，跨年需累加");
        m.put("dyidm", "年增月粒度，跨月跨年需累加");
        m.put("dysdm", "年快照月粒度，取目标月分区");
        m.put("dmf", "月全量，直接取目标月");
        m.put("dmi", "月增量，跨月需累加");
        m.put("no", "无时间粒度");
        return m;
    }

    /** 快照类型识别（静态可测）：取表名最后一段（大小写不敏感）查字典，未命中约定返回 null */
    public static String snapshotTypeOf(String tbl) {
        if (FuncUtil.isEmpty(tbl)) {
            return null;
        }
        String last = tbl.toLowerCase();
        int idx = last.lastIndexOf('_');
        if (idx >= 0) {
            last = last.substring(idx + 1);
        }
        return SNAPSHOT_SUFFIX_DICT.get(last);
    }

    /** 数仓时间分区列约定（dy=年快照/dm=月快照/dd=日快照）：列本身就是时间取值，
     *  角色预选为维度并同步钉死粒度（人工确认页可见可改）；非时间分区列返回 null */
    public static String timePartGranularity(String col) {
        if (col == null) {
            return null;
        }
        String lower = col.toLowerCase();
        if ("dy".equals(lower) || lower.endsWith("_dy")) {
            return "year";
        }
        if ("dm".equals(lower) || lower.endsWith("_dm")) {
            return "month";
        }
        if ("dd".equals(lower) || lower.endsWith("_dd")) {
            return "day";
        }
        return null;
    }

    /** 确定性编码↔名称配对（同词干，大小写不敏感），名称侧由 isJunkDimension 瘦身不建维度：
     *  ① 尾缀编码列优先（phase_code ↔ phase_name），显式后缀语义最强；
     *  ② 无尾缀裸词干列兜底（dept ↔ dept_name/_label，数仓常见不写 _code 后缀的配套写法），
     *  词干已被尾缀列配走的不重复配 */
    public static Map<String, String> findCodeLabelPairs(List<EntityDef.EntityFieldDef> fields) {
        // 小写词干 → 名称类实际列名（先占先得）
        Map<String, String> labelByStem = new LinkedHashMap<>();
        for (EntityDef.EntityFieldDef field : fields) {
            String stem = stemOf(field.getName(), LABEL_SUFFIXES);
            if (stem != null) {
                labelByStem.putIfAbsent(stem, field.getName());
            }
        }
        Map<String, String> pairs = new LinkedHashMap<>();
        // 第一遍：尾缀编码列（_code/_no）优先
        for (EntityDef.EntityFieldDef field : fields) {
            String stem = stemOf(field.getName(), CODE_SUFFIXES);
            String label = stem == null ? null : labelByStem.get(stem);
            if (label != null && !label.equals(field.getName())) {
                pairs.put(field.getName(), label);
            }
        }
        // 第二遍：裸词干编码列兜底（dept ↔ dept_name）——同词干已有尾缀配对的跳过；
        // 自身是名称/尾缀列的不参与（避免名称列互配）
        Set<String> pairedStems = new HashSet<>();
        for (String code : pairs.keySet()) {
            pairedStems.add(stemOf(code, CODE_SUFFIXES));
        }
        for (EntityDef.EntityFieldDef field : fields) {
            String name = field.getName();
            String lower = name.toLowerCase();
            if (pairs.containsKey(name) || pairedStems.contains(lower)
                    || stemOf(name, LABEL_SUFFIXES) != null || stemOf(name, CODE_SUFFIXES) != null) {
                continue;
            }
            String label = labelByStem.get(lower);
            if (label != null && !label.equals(name)) {
                pairs.put(name, label);
            }
        }
        return pairs;
    }

    /** 字段名去掉指定尾缀（大小写不敏感）返回小写词干，不匹配返回 null */
    public static String stemOf(String col, List<String> suffixes) {
        String lower = col.toLowerCase();
        for (String suffix : suffixes) {
            if (lower.endsWith(suffix) && lower.length() > suffix.length()) {
                return lower.substring(0, lower.length() - suffix.length());
            }
        }
        return null;
    }

    /** 不宜建维度的技术列（维度瘦身）：① code-name 配对的名称侧列（仅保留 code 侧维度挂值域，
     *  filters 输名称自动转码、分组输出自动码转名，名称列冗余）；② 物理 id 主/外键；
     *  ③ 未配对的 _no 单号/工号列（无码值域支撑，分组/过滤无意义）；④ 技术时间戳 */
    public static boolean isJunkDimension(String col, Set<String> labelCols, Map<String, String> codeLabelPairs) {
        String lower = col.toLowerCase();
        if (labelCols.contains(lower)) {
            return true;
        }
        if ("id".equals(lower) || lower.endsWith("_id")) {
            return true;
        }
        if (lower.endsWith("_no") && !codeLabelPairs.containsKey(col)) {
            return true;
        }
        return TECH_TIME_COL.matcher(lower).matches();
    }

    /** 维度名去重：同名冲突优先表名前缀语义命名（替代 _2/_3 数字后缀噪声） */
    public static String uniqueDimName(String col, String tbl, Set<String> used) {
        if (used.add(col)) {
            return col;
        }
        String candidate = tbl + "_" + col;
        int i = 2;
        while (!used.add(candidate)) {
            candidate = tbl + "_" + col + "_" + i++;
        }
        return candidate;
    }

    /** 名称去重：冲突时追加序号 */
    public static String uniqueName(String name, Set<String> used) {
        String candidate = name;
        int i = 2;
        while (!used.add(candidate)) {
            candidate = name + "_" + i++;
        }
        return candidate;
    }

    /** 数仓类型 → 语义层类型（String/Integer/Decimal/Double/Date，其余原样保留） */
    public static String mapType(String dataType) {
        if (dataType == null) {
            return "String";
        }
        String t = dataType.toLowerCase();
        if (t.contains("char") || t.contains("text") || t.equals("string")) {
            return "String";
        }
        if (t.contains("int")) {
            return "Integer";
        }
        if (t.contains("decimal") || t.contains("numeric")) {
            return "Decimal";
        }
        if (t.contains("double") || t.contains("float")) {
            return "Double";
        }
        if (t.contains("date") || t.contains("time")) {
            return "Date";
        }
        return dataType;
    }

    /** db.tbl 拆分（容忍只给表名的情况：schema 为空时无法定位，跳过） */
    public static String[] splitTableName(String tableName) {
        if (FuncUtil.isEmpty(tableName)) {
            return null;
        }
        int idx = tableName.indexOf('.');
        if (idx <= 0 || idx == tableName.length() - 1) {
            log.warn("表名须为 db.tbl 形式，跳过: {}", tableName);
            return null;
        }
        return new String[]{tableName.substring(0, idx), tableName.substring(idx + 1)};
    }
}
