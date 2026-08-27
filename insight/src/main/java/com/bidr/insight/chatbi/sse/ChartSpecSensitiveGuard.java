package com.bidr.insight.chatbi.sse;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.util.StringUtils;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Title: ChartSpecSensitiveGuard
 * Description: chart-spec 出向校验——语义目录侧已清空敏感列值域（模型正常拿不到批量取值），
 * 本类是最后防线：模型仍幻觉引用敏感列时，spec 下发前剔除违规引用，防批量取值外泄或编造。
 * 规则与提示词【敏感字段约定】段对齐：
 * <ul>
 *     <li>条件类（tables[].conditions / charts[].config.filters）：relation 为 1-等于、9-模糊匹配 时放行
 *         （用户指名查询单条记录，值取自用户原话），其余（11-在列表内、15~17 包含等批量场景）剔除；</li>
 *     <li>骨架类（config 的 dimensionField/secondDimensionField/groupByField/treeField/dateField/metrics[].field）：
 *         命中即移除整个自造图表项——维度展开即批量值外泄，且骨架字段剔除会致图表残缺，整体丢弃最稳。</li>
 * </ul>
 *
 * @author Sharp
 * @since 2026/8/16
 */
@Slf4j
public final class ChartSpecSensitiveGuard {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    /**
     * 允许敏感列出现的条件关系：1-等于、9-模糊匹配（用户指名单条查询）
     */
    private static final Set<Integer> POINT_RELATIONS = new HashSet<>(Arrays.asList(1, 9));

    /**
     * 自造图表的骨架字段（命中任一即批量值外泄，整个图表项丢弃）
     */
    private static final String[] SKELETON_FIELDS = {
            "dimensionField", "secondDimensionField", "groupByField", "treeField", "dateField"};

    private ChartSpecSensitiveGuard() {
    }

    /**
     * 剔除 specJson 中的敏感列违规引用；无敏感配置或无命中时原串返回（保持字节一致，避免轨迹/前端 diff 噪音）
     *
     * @param specJson  extract 结点提取出的合法 chart-spec JSON
     * @param sensitive 敏感 property → 配对替换 property 映射（空 Map 即无敏感配置）
     * @return 校验后的 JSON 串（有剔除时重新序列化，否则与入参同串）
     */
    public static String stripSensitiveReferences(String specJson, Map<String, String> sensitive) {
        if (sensitive.isEmpty() || !StringUtils.hasText(specJson)) {
            return specJson;
        }
        try {
            JsonNode root = OBJECT_MAPPER.readTree(specJson);
            boolean changed = stripTables(root.path("tables"), sensitive);
            changed |= stripCharts(root.path("charts"), sensitive);
            return changed ? OBJECT_MAPPER.writeValueAsString(root) : specJson;
        } catch (Exception e) {
            // 提取阶段已校验过 JSON 合法性，这里异常属意外（防御兜底：宁可放行不可断链）
            log.warn("chart-spec 敏感出向校验解析失败，原样下发: {}", e.getMessage());
            return specJson;
        }
    }

    /**
     * tables[].conditions：非指名关系的敏感条件剔除；conditions 剔空保留空数组（表格本身仍有效）
     */
    private static boolean stripTables(JsonNode tables, Map<String, String> sensitive) {
        boolean changed = false;
        for (JsonNode table : tables) {
            changed |= stripConditions(table.path("conditions"), sensitive, "tables.conditions");
        }
        return changed;
    }

    /**
     * charts[]：自造图表（config）骨架字段命中 → 整项移除；filters 同条件规则
     */
    private static boolean stripCharts(JsonNode charts, Map<String, String> sensitive) {
        boolean changed = false;
        for (int i = charts.size() - 1; i >= 0; i--) {
            JsonNode config = charts.get(i).path("config");
            if (!config.isObject()) {
                continue;
            }
            if (hitsSkeletonField(config, sensitive)) {
                ((ArrayNode) charts).remove(i);
                changed = true;
                continue;
            }
            changed |= stripConditions(config.path("filters"), sensitive, "charts.config.filters");
        }
        return changed;
    }

    /**
     * 条件数组过滤：property 命中敏感集且 relation 不属于指名关系（1/9）的条件项剔除
     */
    private static boolean stripConditions(JsonNode conditions, Map<String, String> sensitive, String where) {
        if (!conditions.isArray() || conditions.isEmpty()) {
            return false;
        }
        boolean changed = false;
        for (int i = conditions.size() - 1; i >= 0; i--) {
            JsonNode condition = conditions.get(i);
            String property = condition.path("property").asText(null);
            if (property == null || !sensitive.containsKey(property)) {
                continue;
            }
            int relation = condition.path("relation").asInt(-1);
            if (!POINT_RELATIONS.contains(relation)) {
                ((ArrayNode) conditions).remove(i);
                changed = true;
                log.warn("chart-spec 敏感条件剔除 [{}] property={}, relation={}", where, property, relation);
            }
        }
        return changed;
    }

    /**
     * 自造图表骨架字段是否命中敏感列（metrics[].field 一并检查）
     */
    private static boolean hitsSkeletonField(JsonNode config, Map<String, String> sensitive) {
        for (String fieldName : SKELETON_FIELDS) {
            if (sensitive.containsKey(config.path(fieldName).asText(null))) {
                return true;
            }
        }
        for (JsonNode metric : config.path("metrics")) {
            if (sensitive.containsKey(metric.path("field").asText(null))) {
                return true;
            }
        }
        return false;
    }
}
