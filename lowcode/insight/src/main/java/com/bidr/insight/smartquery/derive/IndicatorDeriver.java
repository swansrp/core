package com.bidr.insight.smartquery.derive;

import com.bidr.insight.smartquery.layer.DimensionDef;
import com.bidr.insight.smartquery.layer.MetricDef;
import com.bidr.insight.smartquery.layer.ValueDomainDef;
import com.bidr.insight.smartquery.model.FilterNode;
import com.bidr.insight.smartquery.model.SemanticQuery;
import com.bidr.insight.smartquery.semantic.SemanticLayerRegistry;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Title: IndicatorDeriver
 * Description: semantic_query → ChartCard 完整 indicator 配置推导器（§46.2/46.3）。
 * 产物与卡片配置生成器/自造编译（specMerge.forgeChartItem）同构：
 * filterConditions（语义筛选回译为 portal 条件树）+ 一/二级维度组（值域条目→维度项，
 * 无值域留空由数据 extras 驱动 X 轴）+ dataMetrics（dataName=指标显示名，与
 * statistic 响应 children.metric 一致驱动系列渲染）。取数侧语义全在 queryContext，
 * 本配置只承载渲染真源，statistic 端点对条件 JSON 做白名单校验后合并。
 * 推导优先级：请求显式 chartMode > 规则推断（inferChartMode）
 *
 * @author Sharp
 * @since 2026/8/18
 */
@Component
@RequiredArgsConstructor
public class IndicatorDeriver {

    /** portal 条件关系码（与前端 FILTER_TYPE / PortalConditionDict 一致） */
    private static final int REL_EQUAL = 1;
    private static final int REL_NOT_EQUAL = 2;
    private static final int REL_GREATER = 3;
    private static final int REL_GREATER_EQUAL = 4;
    private static final int REL_LESS = 5;
    private static final int REL_LESS_EQUAL = 6;
    private static final int REL_NULL = 7;
    private static final int REL_IN = 11;
    private static final int REL_NOT_IN = 12;
    private static final int REL_BETWEEN = 13;

    /** 确定性调色板（与前端自造编译 FORGE_PALETTE 一致，多次渲染颜色稳定） */
    private static final String[] PALETTE = {"#5B8FF9", "#61DDAA", "#F6BD16", "#7262FD", "#78D3F8", "#9661BC"};

    private final SemanticLayerRegistry layers;

    /**
     * chartMode 规则推断（§46.3）：
     * limit + order_by + 单维度 → rankingBar；无维度多指标 → metricsPie；其余 → bar
     */
    public String inferChartMode(SemanticQuery sq) {
        List<String> dims = orEmpty(sq.getDimensions());
        List<String> metrics = orEmpty(sq.getMetrics());
        boolean hasOrder = sq.getOrderBy() != null && !sq.getOrderBy().isEmpty();
        if (sq.getLimit() != null && hasOrder && dims.size() == 1) {
            return "rankingBar";
        }
        if (dims.isEmpty() && metrics.size() >= 2) {
            return "metricsPie";
        }
        return "bar";
    }

    /** semantic_query + chartMode → ChartCard 可直接渲染的完整 indicator 配置 */
    public Map<String, Object> derive(SemanticQuery sq, String chartMode, String title) {
        Map<String, Object> cfg = new LinkedHashMap<>();
        cfg.put("filterConditions", buildFilterConditions(sq.getFilters()));
        List<String> dims = orEmpty(sq.getDimensions());

        if ("rankingBar".equals(chartMode) && !dims.isEmpty()) {
            // 排行榜：无维度，分组字段触发 GROUP BY，topN/sortOrder 落到指标行
            cfg.put("firstDimension", null);
            cfg.put("secondDimension", null);
            DimensionDef dim = layers.current().dimensionMap().get(dims.get(0));
            Map<String, Object> row = metricRow(chartMode, firstMetricName(sq), firstMetricField(sq), 0);
            row.put("groupByField", dims.get(0));
            row.put("groupByLabel", dim != null && dim.getDisplayName() != null ? dim.getDisplayName() : dims.get(0));
            row.put("topN", sq.getLimit() != null ? sq.getLimit() : 10);
            // UI 语义 0=正序(ASC)/1=倒序(DESC)；蓝图缺省 desc
            boolean hasOrder = sq.getOrderBy() != null && !sq.getOrderBy().isEmpty();
            String dir = hasOrder ? sq.getOrderBy().get(0).directionOrDefault("desc") : "desc";
            row.put("sortOrder", "asc".equals(dir) ? 0 : 1);
            cfg.put("dataMetrics", Collections.singletonList(row));
            return cfg;
        }

        if ("metricsPie".equals(chartMode)) {
            // 指标饼图：无维度，扇区=指标（dataName 与响应 children.metric 一致）
            cfg.put("firstDimension", null);
            cfg.put("secondDimension", null);
            cfg.put("dataMetrics", metricRows(chartMode, sq));
            return cfg;
        }

        // bar/line/ptLine/pie：值域条目编为维度项（顺序/X轴口径），无值域留空（X 轴跟随
        // statistic 响应 extras）；无维度（纯合计）打 allowNoDimension 标记放宽前端配置门禁
        if (dims.isEmpty()) {
            cfg.put("allowNoDimension", true);
        }
        cfg.put("firstDimension", dims.isEmpty() ? null : dimensionGroup(dims.get(0)));
        cfg.put("secondDimension", dims.size() > 1 ? dimensionGroup(dims.get(1)) : null);
        cfg.put("dataMetrics", metricRows(chartMode, sq));
        return cfg;
    }

    // ── 指标行 ─────────────────────────────────────────────

    /** dataMetrics 行：dataName=指标显示名（与 statistic 响应 children.metric 一致） */
    private List<Map<String, Object>> metricRows(String chartType, SemanticQuery sq) {
        List<Map<String, Object>> rows = new ArrayList<>();
        List<String> names = orEmpty(sq.getMetrics());
        for (int i = 0; i < names.size(); i++) {
            rows.add(metricRow(chartType, displayName(names.get(i)), names.get(i), i));
        }
        return rows;
    }

    private Map<String, Object> metricRow(String chartType, String dataName, String dataField, int idx) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("dataName", dataName);
        row.put("dataField", dataField);
        row.put("chartType", chartType);
        row.put("color", PALETTE[idx % PALETTE.length]);
        row.put("yAxisPosition", "left");
        row.put("stackGroup", "noStack");
        Map<String, Object> fmt = new LinkedHashMap<>();
        fmt.put("fix", 0);
        fmt.put("unitDivisor", 1);
        row.put("formatConfig", fmt);
        row.put("itemColors", new LinkedHashMap<String, String>());
        return row;
    }

    private String firstMetricName(SemanticQuery sq) {
        List<String> names = orEmpty(sq.getMetrics());
        return names.isEmpty() ? "数量" : displayName(names.get(0));
    }

    private String firstMetricField(SemanticQuery sq) {
        List<String> names = orEmpty(sq.getMetrics());
        return names.isEmpty() ? "" : names.get(0);
    }

    /** 指标英文名 → 显示名（语义层中文 label，与 SqlGenerator 列 display 同源） */
    private String displayName(String metricName) {
        MetricDef def = layers.current().metricMap().get(metricName);
        return def != null && def.getDisplayName() != null ? def.getDisplayName() : metricName;
    }

    // ── 维度组 ─────────────────────────────────────────────

    /** 维度组编译：值域条目 → 维度项（itemName=label，itemValue=存储值，叶子等值条件）；无值域空维度项 */
    private Map<String, Object> dimensionGroup(String dimName) {
        DimensionDef dim = layers.current().dimensionMap().get(dimName);
        String label = dim != null && dim.getDisplayName() != null ? dim.getDisplayName() : dimName;
        Map<String, Object> group = new LinkedHashMap<>();
        group.put("groupName", label);
        group.put("groupValue", dimName);
        List<Map<String, Object>> items = new ArrayList<>();
        ValueDomainDef domain = safeDomain(dimName);
        if (domain != null) {
            boolean storedAsCode = "code".equals(domain.getStoredAs());
            for (ValueDomainDef.DomainValue v : nz(domain.getValues())) {
                String itemValue = storedAsCode ? v.getCode() : v.getLabel();
                Map<String, Object> leaf = new LinkedHashMap<>();
                leaf.put("property", dimName);
                leaf.put("relation", REL_EQUAL);
                leaf.put("value", Collections.singletonList(itemValue));
                leaf.put("conditionList", new ArrayList<>());
                leaf.put("andOr", "0");
                Map<String, Object> qc = new LinkedHashMap<>();
                qc.put("conditionList", Collections.singletonList(leaf));
                qc.put("andOr", "0");
                Map<String, Object> item = new LinkedHashMap<>();
                item.put("itemName", v.getLabel());
                item.put("itemValue", itemValue);
                item.put("queryConditions", qc);
                items.add(item);
            }
        }
        group.put("indicatorItems", items);
        return group;
    }

    private ValueDomainDef safeDomain(String dimName) {
        try {
            return layers.current().domainOfDim(dimName);
        } catch (Exception e) {
            // 表达式非法等异常场景按无值域处理（维度已在 validate 阶段把关，此处防御）
            return null;
        }
    }

    // ── 筛选条件回译（FilterNode → portal 条件树）─────────────

    /** 语义筛选 → 卡片级 filterConditions（取数时随条件 JSON 带回，白名单校验后合并） */
    private Map<String, Object> buildFilterConditions(FilterNode filters) {
        Map<String, Object> fc = new LinkedHashMap<>();
        if (filters == null) {
            fc.put("conditionList", new ArrayList<>());
            fc.put("andOr", "0");
            return fc;
        }
        Object tree = toConditionNode(filters);
        if (tree == null) {
            fc.put("conditionList", new ArrayList<>());
            fc.put("andOr", "0");
            return fc;
        }
        if (tree instanceof List) {
            fc.put("conditionList", tree);
            fc.put("andOr", "0");
        } else {
            fc.put("conditionList", Collections.singletonList(tree));
            fc.put("andOr", "0");
        }
        return fc;
    }

    /**
     * FilterNode → portal 条件节点：组节点返回叶子列表（仅支持 AND，OR 组跳过——
     * InteractionMerger 回合并时不支持的关系会拒绝，不产出无法往返的条件）；
     * 叶子节点返回单条件 Map
     */
    private Object toConditionNode(FilterNode node) {
        if (node.isGroup()) {
            if (!"AND".equalsIgnoreCase(node.operatorOrDefault())) {
                return null;
            }
            List<Object> leaves = new ArrayList<>();
            for (FilterNode c : nz(node.getConditions())) {
                Object sub = toConditionNode(c);
                if (sub instanceof List) {
                    leaves.addAll((List<?>) sub);
                } else if (sub != null) {
                    leaves.add(sub);
                }
            }
            return leaves;
        }
        String dim = node.getDimension();
        if (dim == null || dim.isEmpty()) {
            return null;
        }
        Integer relation = relationOf(node.getOperator(), node.getValue());
        if (relation == null) {
            return null;
        }
        List<Object> values;
        if (REL_NULL == relation) {
            values = new ArrayList<>();
        } else if (node.getValue() instanceof List) {
            values = new ArrayList<>((List<?>) node.getValue());
        } else if (node.getValue() == null) {
            return null;
        } else {
            values = Collections.singletonList(node.getValue());
        }
        Map<String, Object> leaf = new LinkedHashMap<>();
        leaf.put("property", dim);
        leaf.put("relation", relation);
        leaf.put("value", values);
        leaf.put("conditionList", new ArrayList<>());
        leaf.put("andOr", "0");
        return leaf;
    }

    /** 引擎操作符 → portal 关系码；多值语义按值形态归并为 in/between */
    private Integer relationOf(String op, Object value) {
        if (op == null) {
            return null;
        }
        boolean multi = value instanceof List;
        switch (op) {
            case "=":
                return REL_EQUAL;
            case "!=":
                return REL_NOT_EQUAL;
            case ">":
                return REL_GREATER;
            case ">=":
                return REL_GREATER_EQUAL;
            case "<":
                return REL_LESS;
            case "<=":
                return REL_LESS_EQUAL;
            case "is_null":
                return REL_NULL;
            case "in":
                return REL_IN;
            case "not_in":
                return REL_NOT_IN;
            case "between":
                return REL_BETWEEN;
            default:
                // like 等不支持的操作符不回译（不降级猜测；取数侧语义仍由 queryContext 保证）
                return null;
        }
    }

    private static <T> List<T> orEmpty(List<T> list) {
        return list == null ? new ArrayList<T>() : list;
    }

    private static <T> List<T> nz(List<T> list) {
        return list == null ? new ArrayList<T>() : list;
    }
}
