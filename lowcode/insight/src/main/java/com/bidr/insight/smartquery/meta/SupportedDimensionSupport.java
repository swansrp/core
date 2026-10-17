package com.bidr.insight.smartquery.meta;

import com.bidr.insight.smartquery.layer.DimensionDef;
import com.bidr.insight.smartquery.layer.EntityDef;
import com.bidr.kernel.utils.FuncUtil;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Title: SupportedDimensionSupport
 * Description: 指标 supported_dimensions 后端确定性展开（纯机械规则，零判断）：
 * 源表骨架维度 + 经 relations 可达实体的骨架维度，排除敏感列。规则握在后端手里，
 * 落库时统一覆盖展开——LLM 只产 formula/source_table 层面，不再逐指标全量复制
 * 维度名清单（省输出 token 与整类枚举思考），漏列导致问数校验失败的风险一并消除
 *
 * @author Sharp
 * @since 2026/8/25
 */
public final class SupportedDimensionSupport {

    private static final ObjectMapper OM = new ObjectMapper();

    private SupportedDimensionSupport() {
    }

    /** 对指标数组逐条覆盖 supported_dimensions（就地改写；非对象节点跳过） */
    public static void expand(ArrayNode metrics, List<EntityDef> entities, List<DimensionDef> dimensions,
                              Set<String> sensitiveKeys, JsonNode relations) {
        if (metrics == null || FuncUtil.isEmpty(entities)) {
            return;
        }
        Map<String, EntityDef> byTable = new HashMap<>();
        Map<String, EntityDef> byName = new HashMap<>();
        for (EntityDef e : entities) {
            if (FuncUtil.isNotEmpty(e.getTable())) {
                byTable.putIfAbsent(e.getTable().toLowerCase(), e);
            }
            if (FuncUtil.isNotEmpty(e.getName())) {
                byName.putIfAbsent(e.getName(), e);
            }
        }
        Map<String, Set<String>> adj = relationAdjacency(relations);
        for (JsonNode m : metrics) {
            if (!(m instanceof ObjectNode)) {
                continue;
            }
            String table = m.path("source_table").asText("").trim().toLowerCase();
            EntityDef source = byTable.get(table);
            if (source == null) {
                continue;   // 守卫已拦非法表，此处仅跳过
            }
            Set<EntityDef> reach = reachable(source, byName, adj);
            ArrayNode sd = OM.createArrayNode();
            Set<String> seen = new LinkedHashSet<>();
            for (DimensionDef d : dimensions == null
                    ? java.util.Collections.<DimensionDef>emptyList() : dimensions) {
                EntityDef owner = ownerOf(d, byTable);
                if (owner == null || !reach.contains(owner)) {
                    continue;
                }
                if (isSensitive(owner.getName(), baseColumn(d.getExpression()), sensitiveKeys)) {
                    continue;
                }
                if (seen.add(d.getName())) {
                    sd.add(d.getName());
                }
            }
            ((ObjectNode) m).set("supported_dimensions", sd);
        }
    }

    /** BFS 全可达（含自身）：关系邻接为实体名无向图 */
    private static Set<EntityDef> reachable(EntityDef source, Map<String, EntityDef> byName,
                                            Map<String, Set<String>> adj) {
        Set<EntityDef> out = new LinkedHashSet<>();
        if (FuncUtil.isEmpty(source.getName())) {
            out.add(source);
            return out;
        }
        Set<String> visited = new HashSet<>();
        Deque<String> queue = new ArrayDeque<>();
        visited.add(source.getName());
        queue.add(source.getName());
        while (!queue.isEmpty()) {
            String cur = queue.poll();
            EntityDef e = byName.get(cur);
            if (e != null) {
                out.add(e);
            }
            for (String nb : adj.getOrDefault(cur, java.util.Collections.<String>emptySet())) {
                if (visited.add(nb)) {
                    queue.add(nb);
                }
            }
        }
        if (out.isEmpty()) {
            out.add(source);
        }
        return out;
    }

    /** 维度归属实体（expression = db.tbl.col 前两段定位表，小写归一）；无法归属返回 null */
    private static EntityDef ownerOf(DimensionDef d, Map<String, EntityDef> byTable) {
        String expr = d.getExpression();
        if (FuncUtil.isEmpty(expr)) {
            return null;
        }
        String[] seg = expr.split("\\.");
        if (seg.length < 3) {
            return null;
        }
        return byTable.get((seg[0] + "." + seg[1]).toLowerCase());
    }

    /** 维度表达式的基础列（末段），敏感排除用 */
    private static String baseColumn(String expr) {
        if (FuncUtil.isEmpty(expr)) {
            return "";
        }
        int i = expr.lastIndexOf('.');
        return i >= 0 ? expr.substring(i + 1) : expr;
    }

    /** 敏感列判定（与生成服务同口径：entity.field 小写键） */
    private static boolean isSensitive(String entityName, String field, Set<String> sensitiveKeys) {
        return FuncUtil.isNotEmpty(field) && sensitiveKeys != null
                && sensitiveKeys.contains((entityName + "." + field).toLowerCase());
    }

    /** 关系草稿 → 实体名无向邻接表（from_entity/to_entity 双向；解析失败按无关系） */
    private static Map<String, Set<String>> relationAdjacency(JsonNode relations) {
        Map<String, Set<String>> adj = new HashMap<>();
        if (relations == null) {
            return adj;
        }
        for (JsonNode r : relations) {
            String from = r.path("from_entity").asText("");
            String to = r.path("to_entity").asText("");
            if (FuncUtil.isEmpty(from) || FuncUtil.isEmpty(to)) {
                continue;
            }
            adj.computeIfAbsent(from, k -> new HashSet<>()).add(to);
            adj.computeIfAbsent(to, k -> new HashSet<>()).add(from);
        }
        return adj;
    }

    /** 合并资产 map 内 metrics supported_dimensions 全量重展开（静态可测）：上下文=map 内 entities/
     *  dimensions/relations/sensitive-fields，覆盖全部 metrics 数组（存量+新增）——补齐「新增维度/关系
     * append 后存量指标清单不跟随」缺口（临时语义层 §6.2.2 误判不走而误入兜底）；
     *  返回是否改写回 map；解析失败返 false 保留原内容（不阻塞作答） */
    public static boolean reexpandMergedMetrics(Map<String, String> merged) {
        if (merged == null) {
            return false;
        }
        try {
            JsonNode metricsNode = OM.readTree(merged.getOrDefault("metrics.json", "[]"));
            if (!(metricsNode instanceof ArrayNode) || metricsNode.size() == 0) {
                return false;
            }
            List<EntityDef> entities = Arrays.asList(OM.readValue(
                    merged.getOrDefault("entities.json", "[]"), EntityDef[].class));
            List<DimensionDef> dims = Arrays.asList(OM.readValue(
                    merged.getOrDefault("dimensions.json", "[]"), DimensionDef[].class));
            Set<String> sensitive = new HashSet<>();
            JsonNode sf = OM.readTree(merged.getOrDefault("sensitive-fields.json", "{}"));
            for (JsonNode t : sf.path("tables")) {
                String entity = t.path("entity").asText("");
                for (JsonNode f : t.path("fields")) {
                    sensitive.add((entity + "." + f.path("field").asText("")).toLowerCase());
                }
            }
            expand((ArrayNode) metricsNode, entities, dims, sensitive,
                    OM.readTree(merged.getOrDefault("relations.json", "[]")));
            merged.put("metrics.json", OM.writeValueAsString(metricsNode));
            return true;
        } catch (Exception e) {
            return false;
        }
    }
}
