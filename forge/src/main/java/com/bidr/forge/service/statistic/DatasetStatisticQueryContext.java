package com.bidr.forge.service.statistic;

import com.bidr.forge.bo.DatasetColumns;
import com.bidr.forge.dao.entity.SysDatasetColumn;
import com.bidr.forge.dao.entity.SysDatasetTable;
import com.bidr.forge.engine.builder.BaseSqlBuilder;
import com.bidr.forge.engine.builder.DatasetSqlBuilder;
import com.bidr.kernel.utils.FuncUtil;
import com.bidr.forge.utils.SqlIdentifierUtil;
import com.bidr.kernel.vo.portal.AdvancedQueryReq;

import java.util.*;

/**
 * Dataset 统计上下文适配器。
 *
 * @author ZhangFeihao
 * @since 2025/12/25 15:45
 */
public class DatasetStatisticQueryContext implements StatisticQueryContext {

    private static final String SUBQUERY_ALIAS = "t";

    private final DatasetSqlBuilder builder;
    private final String defaultTableAlias;
    /**
     * Dataset 预览 SELECT 输出列的别名集合（包含 portal 字段映射后的 voFieldName 与 columnAlias）。
     * 统计 SQL 应优先引用这些 alias（例如 age），而不是强行补成 t.age。
     */
    private final Set<String> selectAliases;

    public DatasetStatisticQueryContext(DatasetColumns datasetColumns,
                                        List<SysDatasetTable> datasets,
                                        List<SysDatasetColumn> columns) {
        // 统计 SQL 里经常直接引用 dy/userNo 等字段；在多表 join 场景下需要补齐主表别名避免歧义。
        this.defaultTableAlias = resolveDefaultTableAlias(datasets);
        this.selectAliases = resolveSelectAliases(columns);

        this.builder = new DatasetSqlBuilder(datasetColumns.getId(), datasets, columns) {
            @Override
            protected String formatColumnName(String columnName) {
                return formatColumnExpression(columnName);
            }
        };
    }

    private static Set<String> resolveSelectAliases(List<SysDatasetColumn> columns) {
        if (columns == null || columns.isEmpty()) {
            return Collections.emptySet();
        }
        Set<String> aliases = new HashSet<>();
        for (SysDatasetColumn c : columns) {
            if (c == null) {
                continue;
            }
            if (FuncUtil.isNotEmpty(c.getColumnAlias())) {
                String cleaned = SqlIdentifierUtil.sanitizeQuotedIdentifier(c.getColumnAlias());
                if (FuncUtil.isNotEmpty(cleaned)) {
                    aliases.add(cleaned);
                }
            }
        }
        return aliases;
    }

    private static String resolveDefaultTableAlias(List<SysDatasetTable> datasets) {
        if (datasets == null || datasets.isEmpty()) {
            return null;
        }
        // 优先取 joinType 为空的主表别名
        for (SysDatasetTable t : datasets) {
            if (t != null && FuncUtil.isEmpty(t.getJoinType())
                    && FuncUtil.isNotEmpty(t.getTableAlias())) {
                return t.getTableAlias();
            }
        }
        // 兜底：取 tableOrder 最小的表别名
        SysDatasetTable first = datasets.stream()
                .filter(Objects::nonNull)
                .min(Comparator.comparing(SysDatasetTable::getTableOrder, Comparator.nullsLast(Integer::compareTo)))
                .orElse(null);
        if (first != null && FuncUtil.isNotEmpty(first.getTableAlias())) {
            return first.getTableAlias();
        }
        return null;
    }

    @Override
    public String getFromSql() {
        // 统计 SQL 需要能引用 Dataset 的 SELECT 输出列别名（例如 TIMESTAMPDIFF(...) AS age）。
        // 因此这里必须使用“预览 SQL（去除 LIMIT）”作为子查询，然后在外层做聚合统计：
        // SELECT ... FROM (SELECT ... FROM ... WHERE ... GROUP BY ... HAVING ... ORDER BY ...) AS t
        // 注意：不包含 LIMIT，避免统计时受分页影响。
        String previewSql = builder.buildSelect(new AdvancedQueryReq(), Collections.emptyMap(), new HashMap<>());
        String upper = previewSql.toUpperCase(Locale.ROOT);

        // 去除 LIMIT（如果存在）
        int limitIdx = upper.lastIndexOf(" LIMIT ");
        String noLimitSql = limitIdx >= 0 ? previewSql.substring(0, limitIdx).trim() : previewSql.trim();

        return "(" + noLimitSql + ") AS " + SUBQUERY_ALIAS;
    }

    @Override
    public BaseSqlBuilder getConditionBuilder() {
        return builder;
    }

    @Override
    public String formatColumnExpression(String dbColumnOrAlias) {
        // Dataset 条件/选择列通常是 tableAlias.col 或 columnAlias。
        // 但统计配置里也可能直接写 dy 这种“裸列名”，在多表 JOIN 时会触发 Column 'dy' is ambiguous。
        // 另外，统计还可能引用 Dataset SELECT 输出列别名（例如 age），这种场景应保留为裸列名，
        // 由外层 FROM (subquery) t 暴露出来，不应补成 t.age。
        if (FuncUtil.isEmpty(dbColumnOrAlias)) {
            return dbColumnOrAlias;
        }
        String expr = dbColumnOrAlias.trim();

        if (expr.length() > 1 && expr.startsWith("'") && expr.endsWith("'")) {
            expr = expr.substring(1, expr.length() - 1);
        }
        // 兼容历史脏数据：`'age'`/`age`/ 'age'
        expr = SqlIdentifierUtil.sanitizeQuotedIdentifier(expr);

        // 如果是 Dataset SELECT 输出列别名（如 projectName/dy/...），必须保留为裸列名
        if (selectAliases != null && selectAliases.contains(expr)) {
            return expr;
        }

        // 已经是 table.col / 函数/表达式/带空格/带括号 的，不处理
        if (expr.contains(".") || expr.contains("(") || expr.contains(")") || expr.contains(" ")) {
            return expr;
        }

        // 兜底：如果没有默认表别名，就直接返回裸列名
        if (FuncUtil.isEmpty(defaultTableAlias)) {
            return expr;
        }

        // 简单标识符：补齐主表别名（用于解决多表 join 的歧义）
        return defaultTableAlias + "." + expr;
    }
}
