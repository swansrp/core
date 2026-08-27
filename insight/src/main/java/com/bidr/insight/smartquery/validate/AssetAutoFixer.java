package com.bidr.insight.smartquery.validate;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Title: AssetAutoFixer
 * Description: 发布校验错误一键自动修复（与 AssetConsistencyValidator 同口径）：对「悬空引用」
 * 类错误做确定性修复——删失效资产/清理悬空引用/去重名，修复本身不会引入新的不一致。
 * 安全设施类（sensitive-fields/row-policies）绝不自动删（删敏感声明=数据外泄、删行权限=
 * 权限泄漏，必须管理员手工裁决）；实体清单为空/JSON 损坏等无解项同样留给人工。
 * 全程 JsonNode 树操作（不强类型反序列化回写），不丢草稿里的额外字段（如指标的
 * aliases/description/datatype）
 *
 * @author Sharp
 * @since 2026/8/25
 */
public class AssetAutoFixer {

    private static final ObjectMapper OM = new ObjectMapper()
            .setSerializationInclusion(JsonInclude.Include.NON_NULL);

    /** 三段式列引用 db.tbl.col（与校验器同构） */
    private static final Pattern COL_REF = Pattern.compile("([A-Za-z_]\\w*)\\.([A-Za-z_]\\w*)\\.([A-Za-z_]\\w*)");

    /** 修复结果：仅含「有变更」的资产类型（修复后全文），逐条摘要供前端展示 */
    public static class Result {
        private final Map<String, String> fixedContents = new LinkedHashMap<>();
        private final List<String> summary = new ArrayList<>();

        void put(String assetType, JsonNode node) {
            try {
                fixedContents.put(assetType, OM.writeValueAsString(node));
            } catch (Exception e) {
                throw new IllegalStateException("资产 '" + assetType + "' 修复后序列化失败", e);
            }
        }

        public void add(String line) {
            summary.add(line);
        }

        public Map<String, String> getFixedContents() {
            return fixedContents;
        }

        public List<String> getSummary() {
            return summary;
        }

        public boolean hasChanges() {
            return !fixedContents.isEmpty();
        }
    }

    /**
     * 修复入口：contents 键为资产类型、值为草稿 JSON 全文（与校验器同参）。
     * 修复顺序有依赖：先 dimensions（指标/概念的维度引用以修复后的维度名为准），
     * 再 metrics（depends_on 以修复后的指标名为准），随后 relations → concepts → entities 域引用
     */
    public static Result fix(Map<String, String> contents) {
        Result r = new Result();
        ArrayNode entities = readArray(contents, "entities");
        if (entities == null || entities.isEmpty()) {
            return r; // 无实体骨架无从锚定修复（实体清单为空属无解项，留给人工）
        }
        // 实体索引：table → 字段名集合；name → 字段名集合（与校验器 putIfAbsent 首见口径一致）
        Map<String, Set<String>> fieldsByTable = new HashMap<>();
        Map<String, Set<String>> fieldsByName = new HashMap<>();
        for (JsonNode e : entities) {
            Set<String> fs = fieldNames(e);
            String table = e.path("table").asText("");
            if (!table.isEmpty()) {
                fieldsByTable.putIfAbsent(table, fs);
            }
            String name = e.path("name").asText("");
            if (!name.isEmpty()) {
                fieldsByName.putIfAbsent(name, fs);
            }
        }
        Set<String> domainKeys = new HashSet<>();
        JsonNode domainsRoot = readTree(contents, "value-domains");
        if (domainsRoot != null) {
            domainsRoot.path("domains").fieldNames().forEachRemaining(domainKeys::add);
        }

        Set<String> validDims = fixDimensions(contents, fieldsByTable, r);
        Set<String> validMetrics = fixMetrics(contents, fieldsByTable, validDims, r);
        fixRelations(contents, fieldsByName, r);
        fixConcepts(contents, fieldsByName, validDims, r);
        fixEntityDomainRefs(entities, contents, domainKeys, r);
        return r;
    }

    /** 维度：删无名/缺表达式/表达式列悬空项 + 去重；返回修复后合法维度名集合 */
    private static Set<String> fixDimensions(Map<String, String> contents,
                                             Map<String, Set<String>> fieldsByTable, Result r) {
        ArrayNode dims = readArray(contents, "dimensions");
        if (dims == null || dims.isEmpty()) {
            return collectNames(dims);
        }
        int removed = 0;
        int dup = 0;
        Set<String> valid = new HashSet<>();
        for (int i = dims.size() - 1; i >= 0; i--) {
            JsonNode d = dims.get(i);
            String name = d.path("name").asText("");
            String expr = d.path("expression").asText("");
            boolean invalid = name.isEmpty() || expr.isEmpty() || !colRefValid(expr, fieldsByTable);
            if (invalid || !valid.add(name)) {
                dims.remove(i);
                if (invalid) {
                    removed++;
                } else {
                    dup++;
                }
            }
        }
        if (removed + dup > 0) {
            r.put("dimensions", dims);
            r.add("维度：删除 " + removed + " 条（表达式列不在实体骨架）" + (dup > 0 ? "，去重 " + dup + " 条" : ""));
        }
        return valid;
    }

    /** 指标：源表/公式列悬空删整条，supported_dimensions/depends_on 悬空清理，重名去后 */
    private static Set<String> fixMetrics(Map<String, String> contents,
                                          Map<String, Set<String>> fieldsByTable,
                                          Set<String> validDims, Result r) {
        ArrayNode metrics = readArray(contents, "metrics");
        if (metrics == null || metrics.isEmpty()) {
            return collectNames(metrics);
        }
        int dropped = 0;
        int dup = 0;
        int dimRefCleaned = 0;
        int depCleaned = 0;
        Set<String> valid = new HashSet<>();
        // 第一遍：删整条失效指标与重名（倒序索引安全）
        for (int i = metrics.size() - 1; i >= 0; i--) {
            JsonNode m = metrics.get(i);
            String name = m.path("name").asText("");
            boolean isDup = !name.isEmpty() && !valid.add(name);
            boolean invalid = name.isEmpty()
                    || dropByColumnRef(m.path("formula").asText(""), fieldsByTable);
            String st = m.path("source_table").asText("");
            if (!st.isEmpty() && !fieldsByTable.containsKey(st)) {
                invalid = true;
            }
            if (isDup || invalid) {
                metrics.remove(i);
                if (isDup) {
                    dup++;
                } else {
                    dropped++;
                }
            }
        }
        // 第二遍：清理悬空维度引用与依赖（基于修复后的合法集合）
        for (JsonNode m : metrics) {
            JsonNode sds = m.path("supported_dimensions");
            if (sds.isArray()) {
                ArrayNode arr = (ArrayNode) sds;
                for (int i = arr.size() - 1; i >= 0; i--) {
                    if (!validDims.contains(arr.get(i).asText(""))) {
                        arr.remove(i);
                        dimRefCleaned++;
                    }
                }
            }
            JsonNode deps = m.path("depends_on");
            if (deps.isArray()) {
                ArrayNode arr = (ArrayNode) deps;
                for (int i = arr.size() - 1; i >= 0; i--) {
                    if (!valid.contains(arr.get(i).asText(""))) {
                        arr.remove(i);
                        depCleaned++;
                    }
                }
            }
        }
        if (dropped + dup + dimRefCleaned + depCleaned > 0) {
            r.put("metrics", metrics);
            StringBuilder sb = new StringBuilder("指标：删除 ").append(dropped).append(" 条（源表/公式列已不在实体清单）");
            if (dup > 0) {
                sb.append("，去重 ").append(dup).append(" 条");
            }
            if (dimRefCleaned > 0) {
                sb.append("，清理 ").append(dimRefCleaned).append(" 处失效维度引用");
            }
            if (depCleaned > 0) {
                sb.append("，清理 ").append(depCleaned).append(" 处失效指标依赖");
            }
            r.add(sb.toString());
        }
        return valid;
    }

    /** 关系：两端实体不存在删整条；失效 join 键剔除（剔空则删整条） */
    private static void fixRelations(Map<String, String> contents,
                                     Map<String, Set<String>> fieldsByName, Result r) {
        ArrayNode relations = readArray(contents, "relations");
        if (relations == null || relations.isEmpty()) {
            return;
        }
        int dropped = 0;
        int joinCleaned = 0;
        for (int i = relations.size() - 1; i >= 0; i--) {
            JsonNode rel = relations.get(i);
            String from = rel.path("from_entity").asText("");
            String to = rel.path("to_entity").asText("");
            Set<String> fromFields = fieldsByName.get(from);
            Set<String> toFields = fieldsByName.get(to);
            if (fromFields == null || toFields == null) {
                relations.remove(i);
                dropped++;
                continue;
            }
            JsonNode join = rel.path("join");
            if (join.isArray()) {
                ArrayNode arr = (ArrayNode) join;
                for (int j = arr.size() - 1; j >= 0; j--) {
                    JsonNode jk = arr.get(j);
                    if (!fromFields.contains(jk.path("left").asText(""))
                            || !toFields.contains(jk.path("right").asText(""))) {
                        arr.remove(j);
                        joinCleaned++;
                    }
                }
                if (arr.isEmpty()) {
                    relations.remove(i);
                    dropped++;
                }
            }
        }
        if (dropped + joinCleaned > 0) {
            r.put("relations", relations);
            r.add("关系：删除 " + dropped + " 条（两端实体已不存在）"
                    + (joinCleaned > 0 ? "，剔除 " + joinCleaned + " 个失效 join 键" : ""));
        }
    }

    /** 业务概念：归属实体/展开维度悬空删该概念（hierarchy 等其余字段原样保留） */
    private static void fixConcepts(Map<String, String> contents,
                                    Map<String, Set<String>> fieldsByName,
                                    Set<String> validDims, Result r) {
        JsonNode root = readTree(contents, "concepts");
        if (!(root instanceof ObjectNode) || !root.has("concepts") || !root.get("concepts").isArray()) {
            return;
        }
        ArrayNode concepts = (ArrayNode) root.get("concepts");
        int dropped = 0;
        for (int i = concepts.size() - 1; i >= 0; i--) {
            JsonNode c = concepts.get(i);
            String entity = c.path("entity").asText("");
            String dim = c.path("expands_to").path("dimension").asText("");
            if ((!entity.isEmpty() && !fieldsByName.containsKey(entity))
                    || (!dim.isEmpty() && !validDims.contains(dim))) {
                concepts.remove(i);
                dropped++;
            }
        }
        if (dropped > 0) {
            r.put("concepts", root);
            r.add("业务概念：删除 " + dropped + " 个（归属实体/展开维度已不存在）");
        }
    }

    /** 实体字段：清除指向不存在码值域的 value_domain 引用（仅解绑，域缺失本就是校验错误） */
    private static void fixEntityDomainRefs(ArrayNode entities, Map<String, String> contents,
                                            Set<String> domainKeys, Result r) {
        if (!contents.containsKey("entities")) {
            return;
        }
        int cleaned = 0;
        for (JsonNode e : entities) {
            for (JsonNode f : e.path("fields")) {
                String vd = f.path("value_domain").asText("");
                if (!vd.isEmpty() && !domainKeys.contains(vd) && f instanceof ObjectNode) {
                    ((ObjectNode) f).remove("value_domain");
                    cleaned++;
                }
            }
        }
        if (cleaned > 0) {
            r.put("entities", entities);
            r.add("实体：清除 " + cleaned + " 处指向不存在码值域的字段引用");
        }
    }

    /** 公式中任一段式列引用悬空（表不在实体清单/列不在实体字段）→ 指标不可用，整体删除 */
    private static boolean dropByColumnRef(String formula, Map<String, Set<String>> fieldsByTable) {
        if (formula == null || formula.isEmpty()) {
            return false;
        }
        Matcher m = COL_REF.matcher(formula);
        while (m.find()) {
            Set<String> fs = fieldsByTable.get(m.group(1) + "." + m.group(2));
            if (fs == null || !fs.contains(m.group(3))) {
                return true;
            }
        }
        return false;
    }

    /** 维度表达式唯一列引用须落在实体骨架内（无列引用的表达式视为合法，与校验器同口径） */
    private static boolean colRefValid(String expression, Map<String, Set<String>> fieldsByTable) {
        Matcher m = COL_REF.matcher(expression);
        if (!m.find()) {
            return true;
        }
        Set<String> fs = fieldsByTable.get(m.group(1) + "." + m.group(2));
        return fs != null && fs.contains(m.group(3));
    }

    private static Set<String> fieldNames(JsonNode entity) {
        Set<String> names = new HashSet<>();
        for (JsonNode f : entity.path("fields")) {
            String n = f.path("name").asText("");
            if (!n.isEmpty()) {
                names.add(n);
            }
        }
        return names;
    }

    private static Set<String> collectNames(ArrayNode arr) {
        Set<String> names = new HashSet<>();
        if (arr != null) {
            for (JsonNode n : arr) {
                String name = n.path("name").asText("");
                if (!name.isEmpty()) {
                    names.add(name);
                }
            }
        }
        return names;
    }

    private static ArrayNode readArray(Map<String, String> contents, String assetType) {
        JsonNode node = readTree(contents, assetType);
        return node instanceof ArrayNode ? (ArrayNode) node : null;
    }

    private static JsonNode readTree(Map<String, String> contents, String assetType) {
        String content = contents.get(assetType);
        if (content == null || content.trim().isEmpty()) {
            return null;
        }
        try {
            return OM.readTree(content);
        } catch (Exception e) {
            return null; // JSON 损坏不可自动修复，留给校验器报给人工
        }
    }
}
