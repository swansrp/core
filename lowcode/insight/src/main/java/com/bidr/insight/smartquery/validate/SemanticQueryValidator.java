package com.bidr.insight.smartquery.validate;

import com.bidr.insight.smartquery.layer.DimensionDef;
import com.bidr.insight.smartquery.layer.EntityDef;
import com.bidr.insight.smartquery.layer.MetricDef;
import com.bidr.insight.smartquery.layer.ValueDomainDef;
import com.bidr.insight.smartquery.model.FilterNode;
import com.bidr.insight.smartquery.model.OrderByItem;
import com.bidr.insight.smartquery.model.ScopeFilter;
import com.bidr.insight.smartquery.model.SemanticQuery;
import com.bidr.insight.smartquery.model.TimeSpec;
import com.bidr.insight.smartquery.model.ValidationResult;
import com.bidr.insight.smartquery.model.ValidationResult.Issue;
import com.bidr.insight.smartquery.model.WindowSpec;
import com.bidr.insight.smartquery.semantic.SemanticLayerRegistry;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

/**
 * Title: SemanticQueryValidator
 * Description: semantic_query 对本体的合法性校验（validate_query.py 的 1:1 移植，SKILL.md §6 安全阀）。
 * 全部规则为确定性比对，不依赖 LLM；errors 阻断执行，warnings 放行但需在回答中标注。
 * 规则编号与 SKILL.md §6.1~§6.7 + §9.4/§9.5 一一对应
 *
 * @author Sharp
 * @since 2026/8/18
 */
@Component
@RequiredArgsConstructor
public class SemanticQueryValidator {

    private static final Set<String> FIXED_PRESETS = new HashSet<>(Arrays.asList(
            "today", "yesterday", "last_7_days", "last_30_days",
            "this_month", "last_month", "this_year"));
    private static final Set<String> PARAM_PRESETS = new HashSet<>(Arrays.asList(
            "last_n_days", "last_n_weeks", "last_n_months"));

    private static final Set<String> FILTER_OPS = new HashSet<>(Arrays.asList(
            "=", "!=", "in", "not_in", ">", ">=", "<", "<=", "between", "is_null"));
    private static final Set<String> HAVING_OPS = new HashSet<>(Arrays.asList(
            "=", "!=", ">", ">=", "<", "<=", "between", "is_null"));

    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    /** time 节点合法键（TimeSpec 字段清单，未知键检查用） */
    private static final Set<String> TIME_KEYS = new HashSet<>(Arrays.asList(
            "field", "between", "preset", "n"));

    private final SemanticLayerRegistry layers;

    /**
     * 运行全部校验。raw 为 semantic_query 的原始 JsonNode（数值类型检查需要区分
     * 缺失/非整数，与 Python isinstance 行为对齐）
     */
    public ValidationResult validate(SemanticQuery sq, JsonNode raw) {
        ValidationResult r = new ValidationResult();
        String queryType = sq.queryTypeOrDefault();

        r.getErrors().addAll(checkMetrics(sq));
        r.getErrors().addAll(checkDimensions(sq, queryType));
        r.getErrors().addAll(checkFilters(sq, queryType, raw));
        r.getErrors().addAll(checkTime(sq, queryType, raw));
        r.getErrors().addAll(checkRange(sq, raw));
        checkValueDomainsAndConcepts(sq, r);
        r.getErrors().addAll(checkWindow(sq, queryType, raw));
        r.getErrors().addAll(checkList(sq, queryType));
        r.getErrors().addAll(checkScope(sq, queryType));

        // §6.1.2: certified=false → 标注未认证（warning）
        for (String name : orEmpty(sq.getMetrics())) {
            MetricDef m = layers.current().metricMap().get(name);
            if (m != null && !m.certifiedOrDefault()) {
                r.getWarnings().add(Issue("§6.1.2", "metrics",
                        String.format("指标 '%s' 未认证（certified=false），结果需标注", name)));
            }
        }

        r.setValid(r.getErrors().isEmpty());
        return r;
    }

    // ── §6.1 指标校验 ──────────────────────────────────────────

    private List<Issue> checkMetrics(SemanticQuery sq) {
        List<Issue> errors = new ArrayList<>();
        // 6.1.1: 指标名必须在 metrics.json 中定义
        for (String name : orEmpty(sq.getMetrics())) {
            if (!layers.current().metricMap().containsKey(name)) {
                errors.add(Issue("§6.1.1", "metrics",
                        String.format("指标 '%s' 未在 metrics.json 中定义", name)));
            }
        }
        for (String name : orEmpty(sq.getMetrics())) {
            MetricDef m = layers.current().metricMap().get(name);
            if (m == null) {
                continue;
            }
            // 6.1.3: 派生指标的 depends_on 必须已定义
            if ("derived".equals(m.getType())) {
                for (String dep : orEmpty(m.getDependsOn())) {
                    if (!layers.current().metricMap().containsKey(dep)) {
                        errors.add(Issue("§6.1.3", "metrics",
                                String.format("派生指标 '%s' 依赖的指标 '%s' 未定义", name, dep)));
                    }
                }
            }
            // 6.1.4: 复合指标的 source_tables 必须完整（跨源表组合由引擎预聚合子查询对齐渲染）
            if ("composite".equals(m.getType())) {
                if (m.getSourceTables() == null || m.getSourceTables().isEmpty()) {
                    errors.add(Issue("§6.1.4", "metrics",
                            String.format("复合指标 '%s' 缺少 source_tables（数组形式列出公式涉及的全部源表，如 [\"db.tbl\"]；"
                                    + "跨源表组合已支持：引擎按各源表预聚合后对齐连接，但须声明完整源表清单）", name)));
                }
            }
        }
        return errors;
    }

    // ── §6.2 维度校验 ──────────────────────────────────────────

    private List<Issue> checkDimensions(SemanticQuery sq, String queryType) {
        List<Issue> errors = new ArrayList<>();
        // 6.2.1: 维度名必须在 dimensions.json 中定义
        for (String name : orEmpty(sq.getDimensions())) {
            if (!layers.current().dimensionMap().containsKey(name)) {
                errors.add(Issue("§6.2.1", "dimensions",
                        String.format("维度 '%s' 未在 dimensions.json 中定义", name)));
            }
        }
        // 6.2.2: 每个维度必须在所用指标的 supported_dimensions 中
        if ("metric".equals(queryType) && !orEmpty(sq.getMetrics()).isEmpty()) {
            for (String name : orEmpty(sq.getDimensions())) {
                if (!layers.current().dimensionMap().containsKey(name)) {
                    continue;
                }
                for (String mname : orEmpty(sq.getMetrics())) {
                    MetricDef m = layers.current().metricMap().get(mname);
                    if (m == null) {
                        continue;
                    }
                    if (!orEmpty(m.getSupportedDimensions()).contains(name)) {
                        errors.add(Issue("§6.2.2", "dimensions",
                                String.format("维度 '%s' 不被指标 '%s' 支持（supported_dimensions 中未声明）",
                                        name, mname)));
                    }
                }
            }
        }
        return errors;
    }

    // ── §6.3 过滤条件校验 ────────────────────────────────────

    private List<Issue> checkFilters(SemanticQuery sq, String queryType, JsonNode raw) {
        List<Issue> errors = new ArrayList<>();
        // 6.3.0: 未知键显式报错——Jackson 宽松绑定会静默丢弃，模型猜错字段名时错误会以别的形状冒出
        if (raw != null) {
            checkUnknownFilterKeys(raw.get("filters"), "filters", errors);
            checkUnknownFilterKeys(raw.get("having"), "having", errors);
        }
        if (sq.getFilters() != null) {
            errors.addAll(walkFilterTree(sq.getFilters(), "filters"));
        }
        if (sq.getHaving() != null) {
            // 6.3.8: having 仅在 metric 查询时有效
            if (!"metric".equals(queryType)) {
                errors.add(Issue("§6.3.8", "having",
                        String.format("having 仅在 query_type=metric 时有效，当前为 %s", queryType)));
            }
            errors.addAll(walkHavingTree(sq.getHaving(), "having"));
        }
        return errors;
    }

    /** 条件节点合法键：组节点（有 conditions）与叶子分别校验，递归子树 */
    private static final Set<String> FILTER_GROUP_KEYS = new HashSet<>(Arrays.asList("operator", "conditions"));
    private static final Set<String> FILTER_LEAF_KEYS = new HashSet<>(Arrays.asList(
            "dimension", "metric", "operator", "value"));

    /** 条件树未知键递归检查（filters/having 同构）：静默丢弃的非法键在此报出，
     *  错误信息直接给出合法键清单供模型自纠。往返序列化的良性冗余不报：
     *  null 值键（NON_NULL 未配置时的序列化产物）与 group 布尔键（FilterNode.isGroup() 序列化产物） */
    private static void checkUnknownFilterKeys(JsonNode node, String path, List<Issue> errors) {
        if (node == null || !node.isObject()) {
            return;
        }
        // 组节点判定用 isArray："conditions":null 是往返序列化冗余，不是组节点
        boolean group = node.get("conditions") != null && node.get("conditions").isArray();
        Set<String> legal = group ? FILTER_GROUP_KEYS : FILTER_LEAF_KEYS;
        for (Iterator<String> it = node.fieldNames(); it.hasNext(); ) {
            String key = it.next();
            JsonNode v = node.get(key);
            if (v == null || v.isNull() || ("group".equals(key) && v.isBoolean())) {
                continue;
            }
            if (!legal.contains(key)) {
                errors.add(Issue("§6.3.0", path,
                        String.format("条件节点出现未知键 '%s'（%s，该键已被静默忽略）", key,
                                group ? "组节点合法键仅 operator/conditions"
                                        : "叶子合法键仅 dimension/metric/operator/value")));
            }
        }
        JsonNode conds = node.get("conditions");
        if (conds != null && conds.isArray()) {
            for (int i = 0; i < conds.size(); i++) {
                checkUnknownFilterKeys(conds.get(i), path + ".conditions[" + i + "]", errors);
            }
        }
    }

    private List<Issue> walkFilterTree(FilterNode node, String path) {
        List<Issue> errors = new ArrayList<>();
        List<FilterNode> conditions = orEmpty(node.getConditions());
        for (int i = 0; i < conditions.size(); i++) {
            FilterNode cond = conditions.get(i);
            String p = path + ".conditions[" + i + "]";
            if (cond.getDimension() != null) {
                String dimName = cond.getDimension();
                // 6.3.1: dimension 必须在 dimensions.json 中
                if (!layers.current().dimensionMap().containsKey(dimName)) {
                    errors.add(Issue("§6.3.1", p + ".dimension",
                            String.format("filters 中的 dimension '%s' 未在 dimensions.json 中定义", dimName)));
                }
                // 6.3.7: 禁止引用指标名（应使用 having）
                if (layers.current().metricMap().containsKey(dimName)) {
                    errors.add(Issue("§6.3.7", p + ".dimension",
                            String.format("filters 中禁止引用指标名 '%s'，后聚合条件必须使用 having", dimName)));
                }
                // 6.3.3: operator 枚举校验
                String op = cond.getOperator() == null ? "" : cond.getOperator();
                if (!FILTER_OPS.contains(op)) {
                    errors.add(Issue("§6.3.3", p + ".operator",
                            String.format("operator '%s' 不合法（允许: %s）", op, joinSorted(FILTER_OPS))));
                }
            } else if (cond.getMetric() != null) {
                // 6.3.7: filters 中不应出现 metric 字段
                errors.add(Issue("§6.3.7", p + ".metric",
                        "filters 中禁止使用 metric 字段，后聚合条件必须使用 having"));
            } else if (cond.isGroup()) {
                errors.addAll(walkFilterTree(cond, p));
            } else {
                errors.add(Issue("§6.3", p, "条件节点缺少 dimension 或 conditions"));
            }
        }
        return errors;
    }

    private List<Issue> walkHavingTree(FilterNode node, String path) {
        List<Issue> errors = new ArrayList<>();
        List<FilterNode> conditions = orEmpty(node.getConditions());
        for (int i = 0; i < conditions.size(); i++) {
            FilterNode cond = conditions.get(i);
            String p = path + ".conditions[" + i + "]";
            if (cond.getMetric() != null) {
                String metricName = cond.getMetric();
                // 6.3.8: metric 必须在 metrics.json 中
                if (!layers.current().metricMap().containsKey(metricName)) {
                    errors.add(Issue("§6.3.8", p + ".metric",
                            String.format("having 中的 metric '%s' 未在 metrics.json 中定义", metricName)));
                }
                // 6.3.3: operator 枚举校验
                String op = cond.getOperator() == null ? "" : cond.getOperator();
                if (!HAVING_OPS.contains(op)) {
                    errors.add(Issue("§6.3.3", p + ".operator",
                            String.format("operator '%s' 不合法（允许: %s）", op, joinSorted(HAVING_OPS))));
                }
            } else if (cond.isGroup()) {
                errors.addAll(walkHavingTree(cond, p));
            } else {
                errors.add(Issue("§6.3", p, "having 条件节点缺少 metric 或 conditions"));
            }
        }
        return errors;
    }

    // ── §6.4 时间校验 ──────────────────────────────────────────

    private List<Issue> checkTime(SemanticQuery sq, String queryType, JsonNode raw) {
        List<Issue> errors = new ArrayList<>();
        TimeSpec time = sq.getTime();

        // 6.4.3: 列表查询时间规则
        if ("list".equals(queryType) && sq.getEntity() != null) {
            EntityDef ent = layers.current().entityMap().get(sq.getEntity());
            if (ent != null && ent.getTimeField() != null) {
                if (time == null) {
                    errors.add(Issue("§6.4.3", "time",
                            String.format("实体 '%s' 声明了 time_field='%s'，list 查询必须携带时间窗口",
                                    sq.getEntity(), ent.getTimeField())));
                } else if (time.getField() != null && !time.getField().equals(ent.getTimeField())) {
                    errors.add(Issue("§6.4.3", "time.field",
                            String.format("list 查询 time.field 应为 '%s'，当前为 '%s'",
                                    ent.getTimeField(), time.getField())));
                }
            }
        }

        if (time == null) {
            return errors;
        }
        // 6.4.0: time 未知键显式报错——Jackson 宽松绑定会静默丢弃，模型猜错字段名（如 dimension/range）时
        // 错误会以 between 缺失/跨度超限等其他形状冒出，排查困难；在此直接报出合法键清单
        JsonNode timeRaw = raw == null ? null : raw.get("time");
        if (timeRaw != null && timeRaw.isObject()) {
            for (Iterator<String> it = timeRaw.fieldNames(); it.hasNext(); ) {
                String key = it.next();
                if (!TIME_KEYS.contains(key)) {
                    errors.add(Issue("§6.4.0", "time",
                            String.format("time 出现未知键 '%s'（合法键仅 field/between/preset/n，该键已被静默忽略）", key)));
                }
            }
        }
        String preset = time.getPreset();

        // 6.4.1: preset 合法性
        if (preset != null && !preset.isEmpty()) {
            if (FIXED_PRESETS.contains(preset)) {
                // ok
            } else if (PARAM_PRESETS.contains(preset)) {
                JsonNode nNode = raw == null || raw.get("time") == null ? null : raw.get("time").get("n");
                if (!isPositiveInt(nNode)) {
                    errors.add(Issue("§6.4.1", "time.n",
                            String.format("参数化模板 '%s' 必须携带正整数 n，当前为 %s", preset, renderRaw(nNode))));
                } else {
                    int limit = paramLimitOf(preset);
                    if (nNode.asInt() > limit) {
                        errors.add(Issue("§6.4.1", "time.n",
                                String.format("%s 的 n=%d 超过上限 %d，请澄清为明确日期区间",
                                        preset, nNode.asInt(), limit)));
                    }
                }
            } else {
                errors.add(Issue("§6.4.1", "time.preset",
                        String.format("preset '%s' 不是合法的时间预设", preset)));
            }
        }

        // 6.4.2: 指标查询时 time.field 必须与指标 time_field 匹配
        if ("metric".equals(queryType) && !orEmpty(sq.getMetrics()).isEmpty() && time.getField() != null) {
            for (String mname : orEmpty(sq.getMetrics())) {
                MetricDef m = layers.current().metricMap().get(mname);
                if (m != null && m.getTimeField() != null && !time.getField().equals(m.getTimeField())) {
                    errors.add(Issue("§6.4.2", "time.field",
                            String.format("指标 '%s' 的口径时间轴为 '%s'，time.field 应匹配，当前为 '%s'",
                                    mname, m.getTimeField(), time.getField())));
                }
            }
        }

        // 6.4.5: between 校验（含年/季/月粒度分组的聚合查询跨度上限放宽——输出行数受粒度数限定）
        List<Object> between = time.getBetween();
        if (between != null) {
            if (between.size() != 2) {
                errors.add(Issue("§6.4.5", "time.between", "between 必须为 [起始日期, 结束日期] 格式"));
            } else {
                try {
                    LocalDate start = LocalDate.parse(String.valueOf(between.get(0)), DATE_FMT);
                    LocalDate end = LocalDate.parse(String.valueOf(between.get(1)), DATE_FMT);
                    if (start.isAfter(end)) {
                        errors.add(Issue("§6.4.5", "time.between",
                                String.format("起始日期 '%s' 晚于结束日期 '%s'", between.get(0), between.get(1))));
                    }
                    long span = ChronoUnit.DAYS.between(start, end);
                    long spanLimit = hasBoundedGranularityDim(sq) ? GRANULAR_SPAN_LIMIT_DAYS : 366;
                    if (span > spanLimit) {
                        errors.add(Issue("§6.4.5", "time.between",
                                String.format("跨度 %d 天超过 %d 天，请澄清为更小范围；" 
                                        + "按年/季/月分组的聚合查询（dimensions 含 granularity=year/quarter/month 的粒度维度）可放宽至 %d 天",
                                        span, spanLimit, GRANULAR_SPAN_LIMIT_DAYS)));
                    }
                } catch (DateTimeParseException e) {
                    errors.add(Issue("§6.4.5", "time.between",
                            String.format("日期格式不合法（需 YYYY-MM-DD）: %s", between)));
                }
            }
        }
        return errors;
    }

    /** 粒度分组聚合查询的跨度上限（天）：约 10 年，仅当 dimensions 含年/季/月粒度维度时生效 */
    private static final long GRANULAR_SPAN_LIMIT_DAYS = 3660L;

    /** 输出行数有界的粒度集合：年≈1 行/年、季≈4 行/年、月≈12 行/年，10 年跨度下输出仍受控 */
    private static final Set<String> BOUNDED_GRANULARITIES = new HashSet<>(Arrays.asList("year", "quarter", "month"));

    /** 查询是否含输出行数有界的粒度分组维度（granularity=year/quarter/month）：跨度约束按输出行数有界放宽，
     *  而非按天封顶（否则「2021~2026 每年…」「近三年每月…」类高频诉求不可表达） */
    private boolean hasBoundedGranularityDim(SemanticQuery sq) {
        for (String d : orEmpty(sq.getDimensions())) {
            DimensionDef dd = layers.current().dimensionMap().get(d);
            if (dd != null && BOUNDED_GRANULARITIES.contains(dd.getGranularity())) {
                return true;
            }
        }
        return false;
    }

    private static int paramLimitOf(String preset) {
        if ("last_n_days".equals(preset)) {
            return 366;
        }
        if ("last_n_weeks".equals(preset)) {
            return 52;
        }
        if ("last_n_months".equals(preset)) {
            return 36;
        }
        return 366;
    }

    // ── §6.5 范围校验 ──────────────────────────────────────────

    private List<Issue> checkRange(SemanticQuery sq, JsonNode raw) {
        List<Issue> errors = new ArrayList<>();
        Set<String> validFields = new HashSet<>();
        validFields.addAll(orEmpty(sq.getMetrics()));
        validFields.addAll(orEmpty(sq.getDimensions()));
        // list 查询的 fields 也可用于 order_by
        validFields.addAll(orEmpty(sq.getFields()));

        // 6.5.1: limit 校验
        JsonNode limitNode = raw == null ? null : raw.get("limit");
        if (limitNode != null && !limitNode.isNull()) {
            if (!limitNode.isInt() || limitNode.asInt() <= 0) {
                errors.add(Issue("§6.5.1", "limit",
                        String.format("limit 必须为正整数，当前为 %s", renderRaw(limitNode))));
            }
        }

        // 6.5.2: order_by.field 来源 + direction 枚举
        List<OrderByItem> orderBy = orEmpty(sq.getOrderBy());
        for (int i = 0; i < orderBy.size(); i++) {
            OrderByItem ob = orderBy.get(i);
            String field = ob.getField() == null ? "" : ob.getField();
            if (!field.isEmpty() && !validFields.contains(field)) {
                errors.add(Issue("§6.5.2", String.format("order_by[%d].field", i),
                        String.format("field '%s' 不在 metrics/dimensions/fields 中", field)));
            }
            String direction = ob.getDirection() == null ? "" : ob.getDirection();
            if (!direction.isEmpty() && !"asc".equals(direction) && !"desc".equals(direction)) {
                errors.add(Issue("§6.5.2", String.format("order_by[%d].direction", i),
                        String.format("direction '%s' 不合法（asc/desc）", direction)));
            }
        }
        return errors;
    }

    // ── §6.6 值域与概念校验 ────────────────────────────────────

    private void checkValueDomainsAndConcepts(SemanticQuery sq, ValidationResult r) {
        if (sq.getFilters() == null) {
            return;
        }
        // 6.6.1: filter 值能否在值域中解析（warning）
        r.getWarnings().addAll(walkFilterValues(sq.getFilters()));
        // 6.6.2: 业务概念必须已展开（error）
        r.getErrors().addAll(walkConceptExpansion(sq.getFilters()));
    }

    private List<Issue> walkFilterValues(FilterNode node) {
        List<Issue> warnings = new ArrayList<>();
        for (FilterNode cond : orEmpty(node.getConditions())) {
            if (cond.getDimension() != null) {
                DimensionDef dim = layers.current().dimensionMap().get(cond.getDimension());
                if (dim == null) {
                    continue;
                }
                String expression = dim.getExpression() == null ? "" : dim.getExpression();
                String domainName = expression.contains(".") ? layers.current().fieldToDomain().get(expression) : null;
                if (domainName != null && layers.current().domains().containsKey(domainName)) {
                    Object value = cond.getValue();
                    if (value != null && !layers.current().canResolveValue(value, layers.current().domains().get(domainName))) {
                        warnings.add(Issue("§6.6.1", "filters.dimension=" + cond.getDimension(),
                                String.format("值 '%s' 无法在值域 '%s' 中解析（label/alias/code 均不匹配）",
                                        value, domainName)));
                    }
                }
            } else if (cond.isGroup()) {
                warnings.addAll(walkFilterValues(cond));
            }
        }
        return warnings;
    }

    private List<Issue> walkConceptExpansion(FilterNode node) {
        List<Issue> errors = new ArrayList<>();
        for (FilterNode cond : orEmpty(node.getConditions())) {
            if (cond.getDimension() != null) {
                if (layers.current().conceptNames().contains(cond.getDimension())) {
                    errors.add(Issue("§6.6.2", "filters.dimension",
                            String.format("业务概念 '%s' 未展开为过滤模板，请先按 concepts.json 定义展开",
                                    cond.getDimension())));
                }
            } else if (cond.isGroup()) {
                errors.addAll(walkConceptExpansion(cond));
            }
        }
        return errors;
    }

    // ── §9.5 窗口排名校验 ──────────────────────────────────────

    private List<Issue> checkWindow(SemanticQuery sq, String queryType, JsonNode raw) {
        List<Issue> errors = new ArrayList<>();
        WindowSpec window = sq.getWindow();
        if (window == null) {
            return errors;
        }
        // 9.5.4: window 仅在 metric 时有效
        if (!"metric".equals(queryType)) {
            errors.add(Issue("§9.5.4", "window",
                    String.format("window 仅在 query_type=metric 时有效，当前为 %s", queryType)));
            return errors;
        }
        Set<String> validFields = new HashSet<>();
        validFields.addAll(orEmpty(sq.getMetrics()));
        validFields.addAll(orEmpty(sq.getDimensions()));

        // 9.5.1: partition_by 必须是 dimensions 的子集
        for (String p : orEmpty(window.getPartitionBy())) {
            if (!orEmpty(sq.getDimensions()).contains(p)) {
                errors.add(Issue("§9.5.1", "window.partition_by",
                        String.format("'%s' 不在 dimensions 中（必须是其子集）", p)));
            }
        }
        // 9.5.2: order_by.field 必须来自 metrics 或 dimensions
        List<OrderByItem> orderBy = orEmpty(window.getOrderBy());
        for (int i = 0; i < orderBy.size(); i++) {
            String field = orderBy.get(i).getField() == null ? "" : orderBy.get(i).getField();
            if (!field.isEmpty() && !validFields.contains(field)) {
                errors.add(Issue("§9.5.2", String.format("window.order_by[%d].field", i),
                        String.format("field '%s' 不在 metrics 或 dimensions 中", field)));
            }
        }
        // 9.5.3: top_n 正整数
        JsonNode topNode = raw == null || raw.get("window") == null ? null : raw.get("window").get("top_n");
        if (!isPositiveInt(topNode)) {
            errors.add(Issue("§9.5.3", "window.top_n",
                    String.format("top_n 必须为正整数，当前为 %s", renderRaw(topNode))));
        }
        return errors;
    }

    // ── §9.4 列表查询校验 ──────────────────────────────────────

    private List<Issue> checkList(SemanticQuery sq, String queryType) {
        List<Issue> errors = new ArrayList<>();
        if (!"list".equals(queryType)) {
            return errors;
        }
        String entityName = sq.getEntity();
        if (entityName == null || entityName.isEmpty()) {
            errors.add(Issue("§9.4.1", "entity", "list 查询必须指定 entity"));
            return errors;
        }
        EntityDef ent = layers.current().entityMap().get(entityName);
        if (ent == null) {
            errors.add(Issue("§9.4.1", "entity",
                    String.format("entity '%s' 未在 entities.json 中定义", entityName)));
        } else if (!Boolean.TRUE.equals(ent.getListable())) {
            errors.add(Issue("§9.4.1", "entity",
                    String.format("entity '%s' 不可列表（listable=false）", entityName)));
        }
        // list 查询不需要 metrics
        if (!orEmpty(sq.getMetrics()).isEmpty()) {
            errors.add(Issue("§9.4", "metrics", "list 查询不需要 metrics 字段"));
        }
        return errors;
    }

    // ── §6.7 跨事实表范围过滤（scope_filter）校验 ─────────────

    private List<Issue> checkScope(SemanticQuery sq, String queryType) {
        List<Issue> errors = new ArrayList<>();
        ScopeFilter scope = sq.getScopeFilter();
        if (scope == null) {
            return errors;
        }
        if (!"metric".equals(queryType)) {
            errors.add(Issue("§6.7", "scope_filter",
                    String.format("scope_filter 仅在 query_type=metric 时有效，当前为 %s", queryType)));
            return errors;
        }

        // 6.7.1: scope 指标必须已定义且为 atomic
        String mname = scope.getMetric();
        MetricDef m = mname == null ? null : layers.current().metricMap().get(mname);
        if (m == null) {
            errors.add(Issue("§6.7.1", "scope_filter.metric",
                    String.format("scope_filter.metric '%s' 未在 metrics.json 中定义", mname)));
            return errors;
        }
        if (!"atomic".equals(m.getType())) {
            errors.add(Issue("§6.7.1", "scope_filter.metric",
                    String.format("scope_filter.metric '%s' 仅支持 atomic 类型", mname)));
        }

        // 6.7.2: scope 指标不得与主查询指标同源表
        for (String outer : orEmpty(sq.getMetrics())) {
            MetricDef om = layers.current().metricMap().get(outer);
            if (om != null && om.getSourceTable() != null && om.getSourceTable().equals(m.getSourceTable())) {
                errors.add(Issue("§6.7.2", "scope_filter.metric",
                        String.format("scope_filter 指标 '%s' 与主查询指标 '%s' 同源表 '%s'，无需跨表范围过滤",
                                mname, outer, m.getSourceTable())));
            }
        }

        // 6.7.3: 关联维度必须已定义、被主查询指标支持、且与 scope 指标实体 JOIN 可达
        String dname = scope.getDimension();
        DimensionDef dim = dname == null ? null : layers.current().dimensionMap().get(dname);
        if (dim == null) {
            errors.add(Issue("§6.7.3", "scope_filter.dimension",
                    String.format("scope_filter.dimension '%s' 未在 dimensions.json 中定义", dname)));
        } else {
            for (String outer : orEmpty(sq.getMetrics())) {
                MetricDef om = layers.current().metricMap().get(outer);
                if (om != null && !orEmpty(om.getSupportedDimensions()).contains(dname)) {
                    errors.add(Issue("§6.7.3", "scope_filter.dimension",
                            String.format("关联维度 '%s' 不被主查询指标 '%s' 支持（supported_dimensions 中未声明）",
                                    dname, outer)));
                }
            }
            String scopeEnt = m.getSourceTable() == null ? null : layers.current().tableToEntity().get(m.getSourceTable());
            String linkEnt = layers.current().dimEntityOfOrNull(dname);
            if (scopeEnt != null && linkEnt != null && !layers.current().hasJoinPath(scopeEnt, linkEnt)) {
                errors.add(Issue("§6.7.3", "scope_filter.dimension",
                        String.format("scope 指标实体 '%s' 与关联维度实体 '%s' 之间无 JOIN 路径",
                                scopeEnt, linkEnt)));
            }
        }

        // op 枚举
        String op = scope.getOp() == null ? "in" : scope.getOp();
        if (!"in".equals(op) && !"not_in".equals(op)) {
            errors.add(Issue("§6.7", "scope_filter.op",
                    String.format("op '%s' 不合法（允许: in, not_in）", op)));
        }

        // 6.7.4: scope.filters 结构合法，且过滤维度被 scope 指标支持
        if (scope.getFilters() != null) {
            errors.addAll(walkFilterTree(scope.getFilters(), "scope_filter.filters"));
            for (String d : collectFilterDims(scope.getFilters())) {
                if (layers.current().dimensionMap().containsKey(d)
                        && !orEmpty(m.getSupportedDimensions()).contains(d)) {
                    errors.add(Issue("§6.7.4", "scope_filter.filters.dimension",
                            String.format("维度 '%s' 不被 scope 指标 '%s' 支持（supported_dimensions 中未声明）",
                                    d, mname)));
                }
            }
        }

        // 6.7.5: scope.having 指标必须与 scope 指标同源表
        if (scope.getHaving() != null) {
            errors.addAll(walkHavingTree(scope.getHaving(), "scope_filter.having"));
            for (String hname : collectHavingMetrics(scope.getHaving())) {
                MetricDef hm = layers.current().metricMap().get(hname);
                if (hm != null && hm.getSourceTable() != null && !hm.getSourceTable().equals(m.getSourceTable())) {
                    errors.add(Issue("§6.7.5", "scope_filter.having.metric",
                            String.format("having 指标 '%s' 必须与 scope 指标同源表 '%s'",
                                    hname, m.getSourceTable())));
                }
            }
        }
        return errors;
    }

    // ── 工具方法 ──────────────────────────────────────────────

    private static Issue Issue(String rule, String field, String message) {
        return new Issue(rule, field, message);
    }

    public static List<String> collectFilterDims(FilterNode node) {
        List<String> out = new ArrayList<>();
        if (node == null) {
            return out;
        }
        for (FilterNode cond : orEmpty(node.getConditions())) {
            if (cond.isGroup()) {
                out.addAll(collectFilterDims(cond));
            } else if (cond.getDimension() != null) {
                out.add(cond.getDimension());
            }
        }
        return out;
    }

    public static List<String> collectHavingMetrics(FilterNode node) {
        List<String> out = new ArrayList<>();
        if (node == null) {
            return out;
        }
        for (FilterNode cond : orEmpty(node.getConditions())) {
            if (cond.isGroup()) {
                out.addAll(collectHavingMetrics(cond));
            } else if (cond.getMetric() != null) {
                out.add(cond.getMetric());
            }
        }
        return out;
    }

    public static <T> List<T> orEmpty(List<T> list) {
        return list == null ? Collections.<T>emptyList() : list;
    }

    /** 与 Python isinstance(n, int) and n > 0 对齐（缺失/非整数/非正均不通过） */
    private static boolean isPositiveInt(JsonNode node) {
        return node != null && node.isInt() && node.asInt() > 0;
    }

    /** 与 Python format() 的 None/数值/字符串渲染对齐 */
    private static String renderRaw(JsonNode node) {
        if (node == null || node.isNull()) {
            return "None";
        }
        if (node.isTextual()) {
            return node.asText();
        }
        if (node.isBoolean()) {
            return node.asBoolean() ? "True" : "False";
        }
        return node.toString();
    }

    private static String joinSorted(Set<String> ops) {
        List<String> sorted = new ArrayList<>(ops);
        Collections.sort(sorted);
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < sorted.size(); i++) {
            if (i > 0) {
                sb.append(", ");
            }
            sb.append(sorted.get(i));
        }
        return sb.toString();
    }
}
