package com.bidr.forge.service.statistic;

import com.bidr.forge.bo.DatasetColumns;
import com.bidr.forge.bo.MatrixColumns;
import com.bidr.forge.config.jdbc.JdbcConnectService;
import com.bidr.forge.dao.repository.SysDatasetService;
import com.bidr.forge.dao.repository.SysMatrixService;
import com.bidr.forge.engine.builder.BaseSqlBuilder;
import com.bidr.kernel.constant.err.ErrCodeSys;
import com.bidr.kernel.utils.FuncUtil;
import com.bidr.kernel.utils.StringUtil;
import com.bidr.kernel.validate.Validator;
import com.bidr.kernel.vo.common.KeyValueResVO;
import com.bidr.kernel.vo.portal.AdvancedQuery;
import com.bidr.kernel.vo.portal.AdvancedQueryReq;
import com.bidr.kernel.vo.portal.statistic.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.*;

/**
 * Driver 统计通用能力支持服务
 * 设计：采用“策略/模板方法”的思路，把与 Driver 无关的纯算法/拼装逻辑抽出来，Driver 只负责取数与注入依赖。
 * Matrix/Dataset 等不同 Driver 可复用这里的逻辑。
 *
 * @author ZhangFeihao
 * @since 2025/12/24 10:02
 */
@Service
@RequiredArgsConstructor
public class DriverStatisticSupportService {

    private final SysMatrixService sysMatrixService;
    private final SysDatasetService sysDatasetService;

    /**
     * 生成 case when 表达式（参数化）：case when {condition} then {then} else {else} end
     * <ul>
     *     <li>statisticColumnField 非空：then=该字段值，else=0（用于 sum）</li>
     *     <li>statisticColumnField 为空：then=1，else=null（用于 count）</li>
     * </ul>
     */
    public static String buildCaseWhen(AdvancedQuery query,
                                       String statisticColumnField,
                                       Map<String, String> aliasMap,
                                       Map<String, Object> parameters,
                                       StatisticQueryContext ctx) {
        // 1: 根据是否指定统计字段决定 THEN/ELSE 的返回表达式
        String thenStr;
        String elseStr;
        if (FuncUtil.isNotEmpty(statisticColumnField)) {
            String statDb = aliasMap.getOrDefault(statisticColumnField, statisticColumnField);
            thenStr = ctx.formatColumnExpression(statDb);
            elseStr = "0";
        } else {
            thenStr = "1";
            elseStr = "null";
        }

        // 2: 使用 builder 把 AdvancedQuery 转为 where 条件（并把参数写入 parameters）
        BaseSqlBuilder builder = ctx.getConditionBuilder();
        String conditionSql = builder.buildWhereCondition(query, aliasMap, parameters);
        if (FuncUtil.isEmpty(conditionSql)) {
            conditionSql = "1=1"; // 如果没有具体条件，使用恒真条件以便 CASE WHEN 语法合法
        }

        // 3: 返回最终的 CASE WHEN 表达式字符串
        return "case when " + conditionSql + " then " + thenStr + " else " + elseStr + " end";
    }

    /**
     * 通用统计入口：根据 req.metricCondition 是否为空自动选择统计策略。
     */
    public List<StatisticRes> statistic(JdbcConnectService jdbc,
                                        AdvancedStatisticReq req,
                                        StatisticQueryContext ctx,
                                        Map<String, String> aliasMap) {
        if (FuncUtil.isNotEmpty(req.getMetricCondition())) {
            return statisticByMetricCondition(jdbc, req, ctx, aliasMap);
        }
        return statisticByMetricGroup(jdbc, req, ctx, aliasMap);
    }

    /**
     * 汇总统计
     */
    public Map<String, Object> summary(JdbcConnectService jdbc,
                                       AdvancedSummaryReq req,
                                       StatisticQueryContext ctx,
                                       Map<String, String> aliasMap) {
        if (ctx instanceof DatasetStatisticQueryContext) {
            return summary(jdbc, req, (DatasetStatisticQueryContext) ctx, aliasMap);
        }

        // 1. 构建 SELECT 部分
        List<String> columns = req.getColumns();
        if (FuncUtil.isEmpty(columns)) {
            return Collections.emptyMap();
        }

        List<String> innerSelectParts = new ArrayList<>();
        List<String> outerSelectParts = new ArrayList<>();

        for (String column : columns) {
            innerSelectParts.add(column + " AS " + column);
            outerSelectParts.add("SUM(" + column + ") AS " + column);
        }
        String innerSelectSql = String.join(", ", innerSelectParts);
        String outerSelectSql = String.join(", ", outerSelectParts);

        // 2. 构建 FROM 和 WHERE 部分
        Map<String, Object> parameters = new HashMap<>();
        BaseSqlBuilder builder = ctx.getConditionBuilder();
        // 使用 buildQueryClauses 获取 WHERE/GROUP BY/HAVING，不包含 ORDER BY
        String whereSql = builder.buildQueryClauses(req, aliasMap, parameters, false);

        StringBuilder sql = new StringBuilder();
        // 构造嵌套查询：SELECT SUM(col) FROM (SELECT expr AS col FROM table WHERE ...) tt
        sql.append("SELECT ").append(outerSelectSql);
        sql.append(" FROM (SELECT ").append(innerSelectSql);
        sql.append(" FROM ").append(ctx.getFromSql());
        sql.append(whereSql);
        sql.append(") tt");

        // 3. 执行查询
        List<Map<String, Object>> result = jdbc.executeQuery(sql.toString(), parameters);
        if (FuncUtil.isEmpty(result)) {
            return Collections.emptyMap();
        }
        return result.get(0);
    }

    /**
     * Dataset 汇总统计：
     * - 有条件查询（req.condition 非空）：使用 Dataset 预览 SQL 作为子查询，并在外层用“输出列别名”做过滤（例如 projectName）；
     * - 无条件查询：直接复用 ctx.getFromSql()，保留 Dataset 默认 ORDER BY（行为与原来一致）。
     */
    public Map<String, Object> summary(JdbcConnectService jdbc,
                                       AdvancedSummaryReq req,
                                       DatasetStatisticQueryContext ctx,
                                       Map<String, String> aliasMap) {
        // 1. 构建 SELECT 部分
        List<String> columns = req.getColumns();
        if (FuncUtil.isEmpty(columns)) {
            return Collections.emptyMap();
        }

        List<String> innerSelectParts = new ArrayList<>();
        List<String> outerSelectParts = new ArrayList<>();
        for (String column : columns) {
            innerSelectParts.add(column + " AS " + column);
            outerSelectParts.add("SUM(" + column + ") AS " + column);
        }
        String innerSelectSql = String.join(", ", innerSelectParts);
        String outerSelectSql = String.join(", ", outerSelectParts);

        BaseSqlBuilder builder = ctx.getConditionBuilder();
        Map<String, Object> parameters = new HashMap<>();

        // 2. 无条件：直接用 ctx.getFromSql()（保留 Dataset 默认 ORDER BY）
        if (FuncUtil.isEmpty(req.getCondition())) {
            StringBuilder sql = new StringBuilder();
            sql.append("SELECT ").append(outerSelectSql)
                    .append(" FROM (SELECT ").append(innerSelectSql)
                    .append(" FROM ").append(ctx.getFromSql())
                    .append(") tt");

            List<Map<String, Object>> result = jdbc.executeQuery(sql.toString(), parameters);
            if (FuncUtil.isEmpty(result)) {
                return Collections.emptyMap();
            }
            return result.get(0);
        }

        // 3. 有条件：把 where/group/having 下推到 preview SQL 内层（从而可以用 t.project_name 这种物理列做过滤）
        String clausesWithoutOrder = builder.buildQueryClauses(req, aliasMap, parameters, false);

        // ctx.getFromSql() 返回 (previewSql-noLimit) AS t，其中 previewSql 默认带 ORDER BY。
        // 我们要的结构是：在 previewSql 内部（dw_dws.xxx t 这一层）先 where，再 order by。
        // 由于 ctx.getFromSql() 已经把 previewSql 包起来了，这里重新构造一层：
        // FROM ( (previewSql_noLimit_noOrder + clausesWithoutOrder + orderByClause) ) AS t

        // 1) 先拿一份无 LIMIT 的 preview SQL（维持 dataset 默认 order by 字段逻辑），并拆出 ORDER BY
        // ctx.getFromSql() 形如：(SELECT ... ORDER BY ...) AS t
        // 这里为了最小改动，直接把 where 追加到这个子查询内部：
        // (SELECT ... FROM ... WHERE ... ORDER BY ...) AS t
        // 做法：插入到最后一个 " ORDER BY " 之前；若没有 ORDER BY，则直接追加到末尾。
        String fromSql = ctx.getFromSql();
        if (fromSql.startsWith("(") && fromSql.endsWith("AS t")) {
            int orderIdx = fromSql.toUpperCase(Locale.ROOT).lastIndexOf(" ORDER BY ");
            if (orderIdx > 0) {
                // 在 ORDER BY 前插入 where/group/having
                fromSql = fromSql.substring(0, orderIdx) + clausesWithoutOrder + fromSql.substring(orderIdx);
            } else {
                // 没有 ORDER BY，直接追加
                int asIdx = fromSql.toUpperCase(Locale.ROOT).lastIndexOf(") AS T");
                if (asIdx > 0) {
                    fromSql = fromSql.substring(0, asIdx) + clausesWithoutOrder + fromSql.substring(asIdx);
                } else {
                    fromSql = fromSql + clausesWithoutOrder;
                }
            }
        } else {
            // 兜底：直接拼到 fromSql 后面
            fromSql = fromSql + clausesWithoutOrder;
        }

        StringBuilder sql = new StringBuilder();
        sql.append("SELECT ").append(outerSelectSql)
                .append(" FROM (SELECT ").append(innerSelectSql)
                .append(" FROM ").append(fromSql)
                .append(") tt");

        List<Map<String, Object>> result = jdbc.executeQuery(sql.toString(), parameters);
        if (FuncUtil.isEmpty(result)) {
            return Collections.emptyMap();
        }
        return result.get(0);
    }

    /**
     * 获取Matrix配置和列
     */
    public MatrixColumns getMatrixColumns(String portalName) {
        return sysMatrixService.getMatrixColumnsByPortalName(portalName);
    }

    /**
     * 获取Dataset配置和列
     */
    public DatasetColumns getDatasetColumns(String portalName) {
        return sysDatasetService.getDatasetColumnsByPortalName(portalName);
    }

    // 简要：指标为主时的结果组装
    public List<StatisticRes> buildMetricMajorResult(List<Map<String, Object>> rows,
                                                     Metric groupMetric,
                                                     List<MetricCondition> metricConditions,
                                                     List<KeyValueResVO> statisticColumns,
                                                     Integer sort) {
        // 1: 把 SQL 返回摊平成 bucket 结构：metricVal -> (joinKey -> value)
        Map<String, Map<String, BigDecimal>> bucket = new LinkedHashMap<>();

        if (FuncUtil.isEmpty(groupMetric)) {
            // 1.1 无 groupMetric 时，SQL 只返回一行，直接从该行填充 map
            Map<String, Object> first = rows.get(0);
            Map<String, BigDecimal> m = new LinkedHashMap<>();
            for (MetricCondition condition : metricConditions) {
                for (KeyValueResVO col : statisticColumns) {
                    String key = StringUtil.join(condition.getLabel(), col.getLabel());
                    Object v = first.get(key);
                    m.put(key, FuncUtil.isNotEmpty(v) ? new BigDecimal(v.toString()) : BigDecimal.ZERO);
                }
            }
            bucket.put(StatisticRes.NULL, m);
        } else {
            // 1.2 有 groupMetric 时，按每行的 groupMetric 值分桶累加
            for (Map<String, Object> row : rows) {
                Object metricObj = row.get(groupMetric.getColumn());
                String metricVal = FuncUtil.isNotEmpty(metricObj) ? metricObj.toString() : StatisticRes.NULL;

                // 过滤不在字典内的 metricVal（若配置了 dictMap）
                if (FuncUtil.isNotEmpty(groupMetric.getDictMap())) {
                    if (!groupMetric.getDictMap().containsKey(metricVal)) {
                        continue;
                    }
                }

                Map<String, BigDecimal> m = bucket.computeIfAbsent(metricVal, k -> new LinkedHashMap<>());
                for (MetricCondition condition : metricConditions) {
                    for (KeyValueResVO col : statisticColumns) {
                        String key = StringUtil.join(condition.getLabel(), col.getLabel());
                        Object v = row.get(key);
                        m.put(key, FuncUtil.isNotEmpty(v) ? new BigDecimal(v.toString()) : BigDecimal.ZERO);
                    }
                }
            }
        }

        // 2: 把 bucket 转为目标的树形结构（groupMetric -> condition -> statisticColumn）
        List<StatisticRes> result = new ArrayList<>();
        for (Map.Entry<String, Map<String, BigDecimal>> metricEntry : bucket.entrySet()) {
            String metricVal = metricEntry.getKey();

            // 2.1 构造当前顶层 metric 节点（若无 groupMetric 则使用 NULL 占位）
            StatisticRes metricNode;
            if (FuncUtil.isEmpty(groupMetric)) {
                metricNode = new StatisticRes(null, StatisticRes.NULL, StatisticRes.NULL, BigDecimal.ZERO);
            } else {
                String metricLabel = null;
                if (FuncUtil.isNotEmpty(groupMetric.getDictMap())) {
                    metricLabel = groupMetric.getDictMap().get(metricVal);
                }
                metricNode = new StatisticRes(groupMetric.getColumn(), metricVal, metricLabel, BigDecimal.ZERO);
            }

            // 2.2 初始化 condition 层（每个 condition 一项），condition 的 metric 值使用 condition.value 或 label
            Map<String, StatisticRes> conditionMap = new LinkedHashMap<>();
            for (MetricCondition condition : metricConditions) {
                String conditionValue = FuncUtil.isNotEmpty(condition.getValue()) ? condition.getValue() : condition.getLabel();
                conditionMap.put(conditionValue, new StatisticRes(null, conditionValue, condition.getLabel(), BigDecimal.ZERO));
            }

            // 2.3 填充每个 condition 下的 statistic 列（metricEntry 的键使用 joinKey 约定）
            for (Map.Entry<String, BigDecimal> e : metricEntry.getValue().entrySet()) {
                String[] metricArray = e.getKey().split(StringUtil.SPLITTER);
                String conditionValue = metricArray.length > 0 ? metricArray[0] : e.getKey();
                String statisticLabel = metricArray.length > 1 ? metricArray[1] : e.getKey();

                StatisticRes conditionNode = conditionMap.get(conditionValue);
                if (FuncUtil.isEmpty(conditionNode)) {
                    continue;
                }

                // 2.4 把统计值作为叶子节点加入，并累加 condition 的统计汇总
                StatisticRes statLeaf = new StatisticRes(null, statisticLabel, statisticLabel, e.getValue());
                conditionNode.setStatistic(conditionNode.getStatistic().add(e.getValue()));
                conditionNode.getChildren().add(statLeaf);
            }

            // 2.5 计算顶层 metric 的汇总值并设置 children
            BigDecimal summary = BigDecimal.ZERO;
            for (StatisticRes c : conditionMap.values()) {
                summary = summary.add(c.getStatistic());
            }
            metricNode.setStatistic(summary);
            metricNode.setChildren(new ArrayList<>(conditionMap.values()));

            result.add(metricNode);
        }

        // 3: 对顶层按 sort 排序（可空），最后返回
        if (FuncUtil.isNotEmpty(sort)) {
            if (Integer.valueOf(1).equals(sort)) {
                result.sort(Comparator.comparing(StatisticRes::getStatistic));
            } else if (Integer.valueOf(2).equals(sort)) {
                result.sort((o1, o2) -> o2.getStatistic().compareTo(o1.getStatistic()));
            }
        }

        if (FuncUtil.isEmpty(groupMetric)) {
            return FuncUtil.isNotEmpty(result) ? result.get(0).getChildren() : Collections.emptyList();
        }
        return result;
    }

    // 简要：按分组维度聚合统计（不带自定义条件）
    public List<StatisticRes> statisticByMetricGroup(JdbcConnectService jdbc,
                                                     AdvancedStatisticReq req,
                                                     StatisticQueryContext ctx,
                                                     Map<String, String> aliasMap) {
        // 1: 校验输入并读取分组列、统计列
        // 1.1 获取 metricColumn 列表并校验非空
        List<Metric> metricColumns = req.getMetricColumn();
        Validator.assertNotEmpty(metricColumns, ErrCodeSys.PA_DATA_NOT_EXIST, "分类指标");

        // 1.2 取第一个 metric 作为分组列（当前仅支持一层分组），并校验其 column 字段
        Metric metric = metricColumns.get(0);
        Validator.assertTrue(FuncUtil.isNotEmpty(metric.getColumn()), ErrCodeSys.SYS_ERR_MSG, "分类指标列不能为空");

        // 2: 构建 WHERE 条件（参数化）
        // 2.1 创建参数 map，交由 buildWhereSql 填充
        Map<String, Object> parameters = new HashMap<>();
        // 2.2 通过 buildWhereSql 获取 where 子句（不含 WHERE 前缀）
        String where = buildWhereSql(req, ctx, aliasMap, parameters);

        // 3: 准备 SELECT 子句（分组列 + 聚合表达式）
        // 3.1 计算分组列对应的数据库列名（优先 aliasMap），并构造 SELECT 的 metric 列表达式
        String metricColumnDb = aliasMap.getOrDefault(metric.getColumn(), metric.getColumn());
        String metricSelect = "IFNULL(" + ctx.formatColumnExpression(metricColumnDb) + ", '" + StatisticRes.NULL + "') AS `" + metric.getColumn() + "`";

        // 3.2 根据第一个 statisticColumn 决定聚合表达式：有 value 则 SUM，否则 COUNT
        KeyValueResVO firstStatistic = req.getStatisticColumn().get(0);
        String statisticExpr;
        if (FuncUtil.isNotEmpty(firstStatistic.getValue())) {
            // 3.2.1 若 statistic.value 非空，取其数据库列名并用 SUM 聚合
            String statDb = aliasMap.getOrDefault(firstStatistic.getValue(), firstStatistic.getValue());
            statisticExpr = "SUM(" + ctx.formatColumnExpression(statDb) + ")";
        } else {
            // 3.2.2 否则使用 COUNT(1)
            statisticExpr = "COUNT(1)";
        }

        // 4: 组装 SQL（含排序）并执行
        // 4.1 组装基础 SQL：SELECT {metricSelect}, {statisticExpr} AS `statistic` FROM {table}
        StringBuilder sql = new StringBuilder();
        sql.append("SELECT ").append(metricSelect).append(", ").append(statisticExpr).append(" AS `statistic`")
                .append(" FROM ").append(ctx.getFromSql());
        // 4.2 若有 where 条件则追加
        if (FuncUtil.isNotEmpty(where)) {
            sql.append(" WHERE ").append(where);
        }
        // 4.3 按分组列 GROUP BY
        sql.append(" GROUP BY ").append(ctx.formatColumnExpression(metricColumnDb));
        // 4.4 如 req.sort 存在则追加排序规则（1=ASC,2=DESC）
        if (FuncUtil.isNotEmpty(req.getSort())) {
            if (Integer.valueOf(1).equals(req.getSort())) {
                sql.append(" ORDER BY `statistic` ASC");
            } else if (Integer.valueOf(2).equals(req.getSort())) {
                sql.append(" ORDER BY `statistic` DESC");
            }
        }

        // 4.5 执行 SQL，jdbc.executeQuery 返回行列表
        List<Map<String, Object>> rows = jdbc.executeQuery(sql.toString(), parameters);

        // 5: 解析结果行并构造返回对象
        List<StatisticRes> resList = new ArrayList<>();
        if (FuncUtil.isEmpty(rows)) {
            // 5.1 若无返回行则直接返回空列表
            return resList;
        }

        // 5.2 遍历每一行，提取分组值、统计值，并做字典过滤与 label 映射
        for (Map<String, Object> row : rows) {
            Object metricObj = row.get(metric.getColumn());
            Object statisticObj = row.get("statistic");
            // 5.2.1 以字符串形式获取 metricVal，若为空则使用 StatisticRes.NULL
            String metricVal = FuncUtil.isNotEmpty(metricObj) ? metricObj.toString() : StatisticRes.NULL;

            // 5.2.2 如果配置了字典（dictMap），只保留字典中存在的项（兼容 metricObj/metricVal 两种 key）
            if (FuncUtil.isNotEmpty(metric.getDictMap())) {
                if (!metric.getDictMap().containsKey(metricVal) && !metric.getDictMap().containsKey(metricObj)) {
                    // 不在字典中则忽略该行
                    continue;
                }
            }

            // 5.2.3 解析 metricLabel（优先取 dictMap 映射值）
            String metricLabel = null;
            if (FuncUtil.isNotEmpty(metric.getDictMap())) {
                metricLabel = metric.getDictMap().getOrDefault(metricVal, metric.getDictMap().get(metricObj));
            }

            // 5.2.4 解析统计数值，转换为 BigDecimal（保证精度）
            BigDecimal statistic = FuncUtil.isNotEmpty(statisticObj) ? new BigDecimal(statisticObj.toString()) : BigDecimal.ZERO;
            // 5.2.5 构造 StatisticRes 并加入结果列表
            resList.add(new StatisticRes(metric.getColumn(), metricVal, metricLabel, statistic));
        }

        // 6: 返回结果
        return resList;
    }

    // 简要：带自定义条件的统计（每个 condition x statistic 生成列）
    public List<StatisticRes> statisticByMetricCondition(JdbcConnectService jdbc,
                                                         AdvancedStatisticReq req,
                                                         StatisticQueryContext ctx,
                                                         Map<String, String> aliasMap) {
        // 1: 读取并校验自定义条件
        List<MetricCondition> metricConditions = req.getMetricCondition();
        Validator.assertNotEmpty(metricConditions, ErrCodeSys.PA_DATA_NOT_EXIST, "自定义指标");

        // 1.1: 读取可选的 groupMetric（即 metricColumn 的第一个元素）
        List<Metric> metrics = req.getMetricColumn();
        Metric groupMetric = FuncUtil.isNotEmpty(metrics) ? metrics.get(0) : null;

        // 2: 构建 WHERE 条件并准备参数容器
        Map<String, Object> parameters = new HashMap<>();
        String where = buildWhereSql(req, ctx, aliasMap, parameters);

        // 3: 组装 SELECT 列（可包含 groupMetric 列 + 多个 case/count 列）
        List<String> selectParts = new ArrayList<>();
        String groupByDb = null;
        if (groupMetric != null && FuncUtil.isNotEmpty(groupMetric.getColumn())) {
            String metricColumnDb = aliasMap.getOrDefault(groupMetric.getColumn(), groupMetric.getColumn());
            groupByDb = metricColumnDb;
            selectParts.add("IFNULL(" + ctx.formatColumnExpression(metricColumnDb) + ", '" + StatisticRes.NULL + "') AS `" + groupMetric.getColumn() + "`");
        }

        // 4: 为每个 metricCondition x statisticColumn 生成 CASE WHEN 或 COUNT 列
        // 规则：列别名使用 join(condition.label, statistic.label) 的约定，便于后续解析
        Map<String, String> aliasMapping = new HashMap<>();
        int aliasCounter = 0;
        for (MetricCondition metricCondition : metricConditions) {
            Validator.assertTrue(FuncUtil.isNotEmpty(metricCondition.getLabel()), ErrCodeSys.SYS_ERR_MSG, "自定义条件label不能为空");
            for (KeyValueResVO statistic : req.getStatisticColumn()) {
                // 4.1 alias 作为 SQL 列别名（例如："分布统计###指标"），供后续解析使用
                String originalAlias = StringUtil.join(metricCondition.getLabel(), statistic.getLabel());

                // 使用安全别名避免特殊字符导致 SQL 解析错误
                String safeAlias = "stat_col_" + (aliasCounter++);
                aliasMapping.put(safeAlias, originalAlias);

                // 4.2 使用 buildCaseWhen 生成参数化的 CASE WHEN 表达式，并把参数写入 parameters
                String caseExpr = buildCaseWhen(metricCondition.getCondition(), statistic.getValue(), aliasMap, parameters, ctx);
                // 4.3 如果 statistic.value 存在则对 caseExpr 求和，否则用 COUNT 统计匹配次数
                if (FuncUtil.isNotEmpty(statistic.getValue())) {
                    selectParts.add("SUM(" + caseExpr + ") AS `" + safeAlias + "`");
                } else {
                    selectParts.add("COUNT(" + caseExpr + ") AS `" + safeAlias + "`");
                }
            }
        }

        // 5: 组装并执行 SQL
        StringBuilder sql = new StringBuilder();
        sql.append("SELECT ").append(String.join(", ", selectParts))
                .append(" FROM ").append(ctx.getFromSql());
        if (FuncUtil.isNotEmpty(where)) {
            sql.append(" WHERE ").append(where);
        }
        if (FuncUtil.isNotEmpty(groupByDb)) {
            sql.append(" GROUP BY ").append(ctx.formatColumnExpression(groupByDb));
        }

        List<Map<String, Object>> rows = jdbc.executeQuery(sql.toString(), parameters);
        // 6: 空结果直接返回
        if (FuncUtil.isEmpty(rows)) {
            return Collections.emptyList();
        }

        // 还原别名
        List<Map<String, Object>> restoredRows = new ArrayList<>(rows.size());
        for (Map<String, Object> row : rows) {
            Map<String, Object> newRow = new LinkedHashMap<>(row);
            for (Map.Entry<String, String> entry : aliasMapping.entrySet()) {
                String safe = entry.getKey();
                String original = entry.getValue();
                if (newRow.containsKey(safe)) {
                    Object val = newRow.remove(safe);
                    newRow.put(original, val);
                }
            }
            restoredRows.add(newRow);
        }
        rows = restoredRows;

        // 7: 根据 majorCondition 决定输出结构（条件为主 or 指标为主）
        boolean conditionMajor = FuncUtil.isEmpty(req.getMajorCondition())
                || StringUtil.convertSwitch(req.getMajorCondition());

        if (conditionMajor) {
            // 条件为主：先构造 condition 维度，再把每个 condition 下的 children 填充为 groupMetric 的值
            return buildConditionMajorResult(rows, groupMetric, metricConditions, req.getStatisticColumn(), req.getSort());
        }
        // 指标为主：先按指标聚合，再把 condition 作为子维度
        return buildMetricMajorResult(rows, groupMetric, metricConditions, req.getStatisticColumn(), req.getSort());
    }

    // 简要：构建 WHERE SQL（利用 MatrixSqlBuilder）
    private String buildWhereSql(AdvancedQueryReq req,
                                 StatisticQueryContext ctx,
                                 Map<String, String> aliasMap,
                                 Map<String, Object> parameters) {
        // 1: 如果没有条件，返回空字符串（调用方在组装 SQL 时判断是否追加 WHERE）
        if (FuncUtil.isEmpty(req.getCondition())) {
            return "";
        }
        // 2: 使用 MatrixSqlBuilder 将 AdvancedQuery 转为 SQL 条件片段，并把参数放入 parameters
        return ctx.getConditionBuilder().buildWhereCondition(req.getCondition(), aliasMap, parameters);
    }

    // 简要：条件为主时的结果组装（会调用 pivot 等工具）
    private List<StatisticRes> buildConditionMajorResult(List<Map<String, Object>> rows,
                                                         Metric groupMetric,
                                                         List<MetricCondition> metricConditions,
                                                         List<KeyValueResVO> statisticColumns,
                                                         Integer sort) {
        // 1: 初始化 resMap，确保每个 condition x statistic 都有占位节点（防止缺省项丢失）
        Map<String, StatisticRes> resMap = new LinkedHashMap<>();

        for (MetricCondition condition : metricConditions) {
            for (KeyValueResVO col : statisticColumns) {
                String key = StringUtil.join(condition.getLabel(), col.getLabel());
                resMap.put(key, new StatisticRes(null, key, col.getLabel(), BigDecimal.ZERO));
            }
        }

        // 2: 若无 groupMetric，SQL 应仅返回一行，直接从该行填充 resMap 并调用 groupByCondition
        if (FuncUtil.isEmpty(groupMetric)) {
            Map<String, Object> first = rows.get(0);
            for (Map.Entry<String, StatisticRes> e : resMap.entrySet()) {
                Object val = first.get(e.getKey());
                e.getValue().setStatistic(FuncUtil.isNotEmpty(val) ? new BigDecimal(val.toString()) : BigDecimal.ZERO);
            }
            return groupByCondition(metricConditions, resMap, sort);
        }

        // 3: 有 groupMetric 时，遍历每行，把每个 joinKey 下的值作为子节点加入对应父节点
        for (Map<String, Object> row : rows) {
            // 3.1 读取当前行的分组维度值（groupMetric 的列）
            Object metricObj = row.get(groupMetric.getColumn());
            String metricVal = FuncUtil.isNotEmpty(metricObj) ? metricObj.toString() : StatisticRes.NULL;

            // 3.2 如果配置了字典 dictMap，则进行白名单过滤（兼容 metricObj 与 metricVal）
            if (FuncUtil.isNotEmpty(groupMetric.getDictMap())) {
                if (!groupMetric.getDictMap().containsKey(metricVal) && !groupMetric.getDictMap().containsKey(metricObj)) {
                    // 若不在字典里则跳过该行
                    continue;
                }
            }

            // 3.3 遍历该行的每一列（跳过分组列本身），把该列的值作为对应父节点的 child 添加
            for (Map.Entry<String, Object> entry : row.entrySet()) {
                if (entry.getKey().equals(groupMetric.getColumn())) {
                    continue; // 跳过分组列
                }
                StatisticRes parent = resMap.get(entry.getKey());
                if (FuncUtil.isEmpty(parent)) {
                    continue; // 若父节点不存在（可能是非统计列），跳过
                }
                BigDecimal statistic = FuncUtil.isNotEmpty(entry.getValue()) ? new BigDecimal(entry.getValue().toString()) : BigDecimal.ZERO;

                // 3.4 计算该 groupMetric 值的展示 label（若有字典则使用字典映射）
                String metricLabel = null;
                if (FuncUtil.isNotEmpty(groupMetric.getDictMap())) {
                    metricLabel = groupMetric.getDictMap().getOrDefault(metricVal, groupMetric.getDictMap().get(metricObj));
                }
                // 3.5 把该 groupMetric 值作为子节点添加到父节点的 children 列表
                parent.getChildren().add(new StatisticRes(groupMetric.getColumn(), metricVal, metricLabel, statistic));
            }
        }

        // 4: 把父节点集合透视为最终按 index&&label 的顶层结构并返回
        return pivotByGroupMetricIndex(groupMetric, statisticColumns, resMap);
    }

    /**
     * 透视结果：按分组维度值输出，metric="idx&&metricVal"，children 为固定统计列节点。
     * idx 规则：
     * - 优先按 groupMetric.dictMap 的 key 顺序生成 0-based 下标
     * - dictMap 为空：按 metricVal 的自然序生成 0-based 下标
     */
    private List<StatisticRes> pivotByGroupMetricIndex(Metric groupMetric,
                                                       List<KeyValueResVO> statisticColumns,
                                                       Map<String, StatisticRes> resMap) {
        // 1: 读取字典映射并生成 metricVal -> index 的映射（优先使用 dictMap 的 key 顺序）
        Map<String, String> dictMap = groupMetric.getDictMap();
        int baseIndex = 0;
        Map<String, Integer> indexMap = new LinkedHashMap<>();
        if (FuncUtil.isNotEmpty(dictMap)) {
            int i = 0;
            for (String key : dictMap.keySet()) {
                indexMap.put(key, baseIndex + i);
                i++;
            }
        }

        // 2: 聚合所有 joinNode 的 children，得到 metricVal -> (statisticLabel -> sum)
        Map<String, Map<String, BigDecimal>> metricAgg = new LinkedHashMap<>();
        for (StatisticRes joinNode : resMap.values()) {
            if (FuncUtil.isEmpty(joinNode.getChildren())) {
                continue; // 跳过无 children 的节点
            }
            String[] metricArray = joinNode.getMetric().split(StringUtil.SPLITTER);
            String statisticLabel = metricArray.length > 1 ? metricArray[1] : joinNode.getMetric();

            for (StatisticRes child : joinNode.getChildren()) {
                String metricVal = child.getMetric();
                BigDecimal v = FuncUtil.isNotEmpty(child.getStatistic()) ? child.getStatistic() : BigDecimal.ZERO;
                metricAgg.computeIfAbsent(metricVal, k -> new LinkedHashMap<>());
                metricAgg.get(metricVal).merge(statisticLabel, v, BigDecimal::add);
            }
        }

        // 3: 确保字典中所有 metricVal 都存在于聚合结果中（即使为 0）
        if (FuncUtil.isNotEmpty(dictMap)) {
            for (String metricVal : dictMap.keySet()) {
                metricAgg.computeIfAbsent(metricVal, k -> new LinkedHashMap<>());
            }
        }

        // 4: 排序 metricVal 列表（有 dictMap 按其顺序，无则按自然序）并补全 indexMap
        List<String> orderedMetricVals = new ArrayList<>(metricAgg.keySet());
        if (FuncUtil.isEmpty(dictMap)) {
            Collections.sort(orderedMetricVals);
            int i = 0;
            for (String mv : orderedMetricVals) {
                indexMap.putIfAbsent(mv, baseIndex + i);
                i++;
            }
        } else {
            orderedMetricVals.sort(Comparator.comparingInt(mv -> indexMap.getOrDefault(mv, Integer.MAX_VALUE)));
        }

        // 5: 构造顶层节点（metric = idx&&label）及其 children（统计列）
        List<StatisticRes> result = new ArrayList<>();
        for (String metricVal : orderedMetricVals) {
            Integer idx = indexMap.getOrDefault(metricVal, baseIndex);
            String topLabel = FuncUtil.isNotEmpty(dictMap) ? dictMap.get(metricVal) : metricVal;
            String topMetric = idx + "&&" + topLabel; // 顶层 metric 使用下标&&label 的格式

            BigDecimal summary = BigDecimal.ZERO;
            List<StatisticRes> statNodes = new ArrayList<>();

            for (KeyValueResVO col : statisticColumns) {
                BigDecimal statVal = metricAgg.getOrDefault(metricVal, Collections.emptyMap())
                        .getOrDefault(col.getLabel(), BigDecimal.ZERO);
                summary = summary.add(statVal);

                // 子节点的 metric 字段使用 metricVal###statisticLabel 的约定（供前端或后续逻辑解析）
                StatisticRes statNode = new StatisticRes(null,
                        col.getLabel(),
                        metricVal + StringUtil.SPLITTER + col.getLabel(),
                        statVal);
                statNode.setChildren(new ArrayList<>());
                statNodes.add(statNode);
            }

            StatisticRes top = new StatisticRes(null, topMetric, topLabel, summary);
            top.setChildren(statNodes);
            result.add(top);
        }

        // 6: 返回透视后的列表
        return result;
    }

    // 简要：按条件分组并返回（对齐 kernel 的 groupByCondition）
    private List<StatisticRes> groupByCondition(List<MetricCondition> metricConditions,
                                                Map<String, StatisticRes> resMap,
                                                Integer sort) {
        // 1: 初始化父节点 map，key 使用 condition.label（展示用），父节点 metric 使用 condition.value
        Map<String, StatisticRes> resultMap = new LinkedHashMap<>();
        for (MetricCondition condition : metricConditions) {
            resultMap.put(condition.getLabel(), new StatisticRes(null, condition.getValue(), condition.getLabel(), BigDecimal.ZERO));
        }

        // 2: 遍历 resMap，把每个 joinNode 按 conditionLabel 挂到对应父节点下，累加统计值
        for (StatisticRes re : resMap.values()) {
            String[] metricArray = re.getMetric().split(StringUtil.SPLITTER);
            String conditionLabel = metricArray.length > 0 ? metricArray[0] : re.getMetric();

            StatisticRes parent = resultMap.get(conditionLabel);
            if (FuncUtil.isEmpty(parent)) {
                continue; // 找不到父节点则跳过
            }
            parent.setStatistic(parent.getStatistic().add(re.getStatistic()));
            parent.getChildren().add(re);

            // 如果 joinNode 的 metric 含有 "###" 分隔的后缀，把后缀设置为其 metricLabel，并更新 metric 为后缀部分
            if (metricArray.length > 1) {
                re.setMetricLabel(re.getMetric());
                re.setMetric(metricArray[1]);
            }
        }

        // 3: 转为列表并按 sort 排序（只对顶层生效）
        List<StatisticRes> resultList = new ArrayList<>(resultMap.values());
        if (FuncUtil.isNotEmpty(sort)) {
            if (Integer.valueOf(1).equals(sort)) {
                resultList.sort(Comparator.comparing(StatisticRes::getStatistic));
            } else if (Integer.valueOf(2).equals(sort)) {
                resultList.sort((o1, o2) -> o2.getStatistic().compareTo(o1.getStatistic()));
            }
        }
        // 4: 返回结果
        return resultList;
    }
}
