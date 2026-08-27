package com.bidr.insight.smartquery.derive;

import com.bidr.insight.smartquery.model.FilterNode;
import com.bidr.insight.smartquery.model.OrderByItem;
import com.bidr.insight.smartquery.model.SemanticQuery;
import com.bidr.insight.smartquery.semantic.SemanticLayerRegistry;
import com.bidr.kernel.constant.dict.portal.PortalConditionDict;
import com.bidr.kernel.constant.dict.portal.PortalSortDict;
import com.bidr.kernel.exception.NoticeException;
import com.bidr.kernel.vo.portal.AdvancedQuery;
import com.bidr.kernel.vo.portal.AdvancedQueryReq;
import com.bidr.kernel.vo.portal.SortVO;
import com.bidr.kernel.vo.portal.statistic.AdvancedStatisticReq;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Title: InteractionMerger
 * Description: 交互层 → 查询定义层合并器（§45.4）：把图表交互产生的条件 JSON
 * （AdvancedQuery 树/sortList/limit）作为增量合并进 semantic_query.filters/order_by。
 * 字段白名单校验（维度查语义层 dimensionMap），值做 label→码值翻译，
 * 超纲交互（不支持的 relation/白名单外字段）直接拒绝，不降级猜测
 *
 * @author Sharp
 * @since 2026/8/18
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class InteractionMerger {

    private final SemanticLayerRegistry layers;

    /** 合并条件树 + 排序（statistic/table 端点共用） */
    public void merge(SemanticQuery sq, AdvancedQueryReq req) {
        if (req.getCondition() != null) {
            FilterNode inc = toFilterNode(req.getCondition());
            if (inc != null) {
                sq.setFilters(combine(sq.getFilters(), inc));
            }
        }
        if (req.getSortList() != null && !req.getSortList().isEmpty()) {
            List<OrderByItem> obs = new ArrayList<>();
            for (SortVO s : req.getSortList()) {
                String p = s.getProperty();
                if (p == null || (!layers.current().dimensionMap().containsKey(p) && !layers.current().metricMap().containsKey(p))) {
                    throw new NoticeException("排序字段不在语义层支持范围: " + p);
                }
                OrderByItem ob = new OrderByItem();
                ob.setField(p);
                ob.setDirection(PortalSortDict.DESC.getValue().equals(s.getType()) ? "desc" : "asc");
                obs.add(ob);
            }
            sq.setOrderBy(obs);
        }
    }

    /** statistic 专属增量：limit（重拉不缩小范围：原 limit 优先）+ sort（作用于已有 order_by 方向） */
    public void mergeStatistic(SemanticQuery sq, AdvancedStatisticReq req) {
        merge(sq, req);
        if (sq.getLimit() == null && req.getLimit() != null && req.getLimit() > 0) {
            sq.setLimit(req.getLimit());
        }
        if (req.getSort() != null && sq.getOrderBy() != null) {
            String dir = PortalSortDict.DESC.getValue().equals(req.getSort()) ? "desc" : "asc";
            for (OrderByItem ob : sq.getOrderBy()) {
                ob.setDirection(dir);
            }
        }
    }

    /** AdvancedQuery 树 → FilterNode；空树返回 null */
    private FilterNode toFilterNode(AdvancedQuery q) {
        List<AdvancedQuery> children = q.getConditionList();
        if (children != null && !children.isEmpty()) {
            FilterNode group = new FilterNode();
            group.setOperator(AdvancedQuery.OR.equals(q.getAndOr()) ? "OR" : "AND");
            List<FilterNode> cs = new ArrayList<>();
            for (AdvancedQuery c : children) {
                FilterNode n = toFilterNode(c);
                if (n != null) {
                    cs.add(n);
                }
            }
            if (cs.isEmpty()) {
                return null;
            }
            group.setConditions(cs);
            return group;
        }
        if (q.getProperty() == null || q.getProperty().isEmpty()) {
            return null;
        }
        String dim = q.getProperty();
        if (!layers.current().dimensionMap().containsKey(dim)) {
            throw new NoticeException("筛选字段 '" + dim + "' 不在语义层支持范围");
        }
        String op = mapOperator(q.getRelation());
        FilterNode leaf = new FilterNode();
        leaf.setDimension(dim);
        leaf.setOperator(op);
        if ("is_null".equals(op)) {
            return leaf;
        }
        List<?> values = q.getValue();
        if (values == null || values.isEmpty()) {
            throw new NoticeException("筛选字段 '" + dim + "' 缺少查询值");
        }
        if ("in".equals(op) || "not_in".equals(op) || "between".equals(op)) {
            if ("between".equals(op) && values.size() != 2) {
                throw new NoticeException("筛选字段 '" + dim + "' 的 between 条件需要两个值");
            }
            List<Object> vs = new ArrayList<>();
            for (Object v : values) {
                vs.add(layers.current().resolveValue(dim, normalize(v)));
            }
            leaf.setValue(vs);
        } else {
            leaf.setValue(layers.current().resolveValue(dim, normalize(values.get(0))));
        }
        return leaf;
    }

    /** PortalConditionDict relation 码 → 引擎 FILTER_OPS 操作符；不支持的关系拒绝 */
    private String mapOperator(Integer relation) {
        if (relation == null) {
            throw new NoticeException("筛选条件缺少查询关系");
        }
        if (PortalConditionDict.EQUAL.getValue().equals(relation)) {
            return "=";
        }
        if (PortalConditionDict.NOT_EQUAL.getValue().equals(relation)) {
            return "!=";
        }
        if (PortalConditionDict.GREATER.getValue().equals(relation)) {
            return ">";
        }
        if (PortalConditionDict.GREATER_EQUAL.getValue().equals(relation)) {
            return ">=";
        }
        if (PortalConditionDict.LESS.getValue().equals(relation)) {
            return "<";
        }
        if (PortalConditionDict.LESS_EQUAL.getValue().equals(relation)) {
            return "<=";
        }
        if (PortalConditionDict.NULL.getValue().equals(relation)) {
            return "is_null";
        }
        if (PortalConditionDict.IN.getValue().equals(relation)
                || PortalConditionDict.CONTAIN.getValue().equals(relation)
                || PortalConditionDict.CONTAIN_IN_OR.getValue().equals(relation)) {
            return "in";
        }
        if (PortalConditionDict.NOT_IN.getValue().equals(relation)) {
            return "not_in";
        }
        if (PortalConditionDict.BETWEEN.getValue().equals(relation)) {
            return "between";
        }
        throw new NoticeException("不支持的查询关系: " + relation);
    }

    /** 原 filters 与增量条件 AND 组合 */
    private FilterNode combine(FilterNode existing, FilterNode inc) {
        if (existing == null) {
            return inc;
        }
        FilterNode group = new FilterNode();
        group.setOperator("AND");
        group.setConditions(Arrays.asList(existing, inc));
        return group;
    }

    /** 非字符串标量统一转 String（值域码值/标签均为字符串，避免 1 ≠ "1" 漏配） */
    private Object normalize(Object v) {
        if (v == null || v instanceof String) {
            return v;
        }
        return String.valueOf(v);
    }
}
