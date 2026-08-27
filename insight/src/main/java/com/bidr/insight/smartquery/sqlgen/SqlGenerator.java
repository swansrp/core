package com.bidr.insight.smartquery.sqlgen;

import com.bidr.insight.smartquery.layer.DimensionDef;
import com.bidr.insight.smartquery.layer.EntityDef;
import com.bidr.insight.smartquery.layer.MetricDef;
import com.bidr.insight.smartquery.layer.RelationDef;
import com.bidr.insight.smartquery.layer.RowPolicyDef;
import com.bidr.insight.smartquery.model.FilterNode;
import com.bidr.insight.smartquery.model.GenResult;
import com.bidr.insight.smartquery.model.GenResult.ColumnInfo;
import com.bidr.insight.smartquery.model.OrderByItem;
import com.bidr.insight.smartquery.model.ScopeFilter;
import com.bidr.insight.smartquery.model.SemanticQuery;
import com.bidr.insight.smartquery.model.WindowSpec;
import com.bidr.insight.smartquery.semantic.SemanticLayer;
import com.bidr.insight.smartquery.semantic.SemanticLayerRegistry;
import com.bidr.insight.smartquery.validate.SemanticQueryValidator;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Title: SqlGenerator
 * Description: 从 semantic_query 生成参数化 SQL（sql_gen.py 的 1:1 移植，SKILL.md §12）。
 * 输入须已通过 SemanticQueryValidator 校验；表/字段/JOIN 一律来自语义层静态定义，
 * 用户输入值全部走 ? 参数化绑定，LIMIT 强制 ≤ 1000。
 * <p>
 * 年全量快照模型要点：所有表按 dy 分区；JOIN 一律附加 dy 对齐条件；
 * 未显式指定年份且未按年份分组时默认取事实表最新快照年（MAX(dy) 子查询）；
 * 枚举过滤值经值域归一为存储码值。与 Python 版唯一差异：占位符 %s → ?（JDBC）
 * <p>
 * 跨源表能力（Python 版所无）：指标源表并集 >1 时（跨表 composite 或多指标不同源）
 * 走预聚合子查询对齐分支——每源表独立渲染「维度∪聚合项」子查询，外层按分组维度
 * LEFT JOIN 后做公式算术组合，聚合先于 JOIN 天然无扇出
 *
 * @author Sharp
 * @since 2026/8/18
 */
@Component
@RequiredArgsConstructor
public class SqlGenerator {

    private static final int MAX_LIMIT = 1000;
    private static final int DEFAULT_LIMIT = 100;

    private static final Map<String, String> SQL_OPS = new HashMap<>();

    static {
        SQL_OPS.put("=", "=");
        SQL_OPS.put("!=", "!=");
        SQL_OPS.put(">", ">");
        SQL_OPS.put(">=", ">=");
        SQL_OPS.put("<", "<");
        SQL_OPS.put("<=", "<=");
    }

    private final SemanticLayerRegistry layers;

    /** 行权限用户上下文（单例并发隔离：ThreadLocal，generate 入口设置 finally 清理） */
    private static final ThreadLocal<RowPolicyUserContext> USER_CTX = new ThreadLocal<>();

    /** 别名命名空间（Python 的 self.aliases/_reset_aliases 等价物，按次生成隔离） */
    private static class AliasCtx {
        final Map<String, String> aliases = new HashMap<>();
        final String prefix;
        int seq;

        AliasCtx(String prefix) {
            this.prefix = prefix;
        }

        String aliasOf(String table) {
            String alias = aliases.get(table);
            if (alias == null) {
                alias = prefix + "t" + seq;
                seq++;
                aliases.put(table, alias);
            }
            return alias;
        }
    }

    public GenResult generate(SemanticQuery sq) {
        return generate(sq, null);
    }

    /**
     * 带用户上下文生成：row-policies 资产渲染期注入 WHERE（参数化绑定，对 semantic_query
     * 载荷不可见不可绕过）。userCtx=null 且目标表配了行策略时 fail-closed 拒绝生成；
     * 上下文经 ThreadLocal 传递（generate 同步方法内无线程切换，finally 清理防串线程）
     */
    public GenResult generate(SemanticQuery sq, RowPolicyUserContext userCtx) {
        USER_CTX.set(userCtx);
        try {
            if ("list".equals(sq.queryTypeOrDefault())) {
                return genList(sq);
            }
            return genMetric(sq);
        } finally {
            USER_CTX.remove();
        }
    }

    // ── metric 查询 ───────────────────────────────────────────

    private GenResult genMetric(SemanticQuery sq) {
        AliasCtx ctx = new AliasCtx("");
        GenResult result = new GenResult();
        List<Object> params = result.getParams();
        List<String> notes = result.getNotes();
        List<String> metrics = SemanticQueryValidator.orEmpty(sq.getMetrics());
        List<String> dims = SemanticQueryValidator.orEmpty(sq.getDimensions());

        for (String mname : metrics) {
            MetricDef m = layers.current().metricMap().get(mname);
            if (m == null) {
                throw new SqlGenException("指标 '" + mname + "' 未定义");
            }
            if (!"atomic".equals(m.getType()) && !"composite".equals(m.getType())) {
                throw new SqlGenException(String.format(
                        "指标 '%s' 类型为 %s，当前 SQL 生成器仅支持 atomic 与同源表 composite"
                        + "（derived 引用其他指标名暂不支持）", mname, m.getType()));
            }
        }
        // 指标源表并集：composite 取 source_tables 声明，atomic 取 source_table，
        // 两处均缺时从公式列引用兑底提取（表名与语义层同口径）。
        // 单源表走既有直接渲染；多源表（跨表 composite 或多指标不同源）走预聚合子查询对齐分支
        Set<String> sourceTables = new TreeSet<>();
        for (String m : metrics) {
            sourceTables.addAll(collectMetricTables(layers.current().metricMap().get(m)));
        }
        if (sourceTables.isEmpty()) {
            throw new SqlGenException("查询未定位到任何源表（指标缺 source_table/source_tables 声明且公式无表引用）");
        }
        if (sourceTables.size() > 1) {
            return genMetricMultiTable(sq, metrics, dims, sourceTables);
        }
        String factTable = sourceTables.iterator().next();
        String factEnt = layers.current().tableToEntity().get(factTable);

        // 需要的实体：维度所属实体（含过滤维度、scope 关联维度），排序保证别名分配确定性
        List<String> filterDims = SemanticQueryValidator.collectFilterDims(sq.getFilters());
        ScopeFilter scope = sq.getScopeFilter();
        Set<String> needed = new TreeSet<>();
        for (String d : concat(dims, filterDims)) {
            String ent = layers.current().dimEntityOf(d);
            if (!ent.equals(factEnt)) {
                needed.add(ent);
            }
        }
        if (scope != null) {
            String ent = layers.current().dimEntityOf(scope.getDimension());
            if (!ent.equals(factEnt)) {
                needed.add(ent);
            }
        }

        String[] from = buildFrom(ctx, factEnt, needed);
        String fromSql = from[0];
        List<String> joinSqls = new ArrayList<>(Arrays.asList(from).subList(1, from.length));

        // SELECT
        List<String> selectParts = new ArrayList<>();
        List<String> groupParts = new ArrayList<>();
        for (String d : dims) {
            DimensionDef dimDef = layers.current().dimensionMap().get(d);
            String colref = rewriteExpr(ctx, dimDef.getExpression());
            String groupExpr = granularityExpr(dimDef, colref);
            selectParts.add(groupExpr + " AS `" + d + "`");
            groupParts.add(groupExpr);
            result.getColumns().add(new ColumnInfo(d, "dimension", dimDef.getDisplayName()));
            if (layers.current().domainOfDim(d) != null) {
                result.getTranslate().put(d, d);
            }
        }
        for (String mname : metrics) {
            String formula = rewriteFormula(ctx, layers.current().metricMap().get(mname).getFormula());
            selectParts.add(formula + " AS `" + mname + "`");
            result.getColumns().add(new ColumnInfo(mname, "metric",
                    layers.current().metricMap().get(mname).getDisplayName()));
        }

        // WHERE
        List<String> whereParts = new ArrayList<>();
        appendDefaultFilters(ctx, factTable, whereParts);
        appendRowPolicies(ctx, factTable, whereParts, params, notes);
        String yearSub = yearConditions(sq.getFilters(), dims, factTable, notes);
        if (yearSub != null) {
            whereParts.add(ctx.aliasOf(factTable) + "." + quoteCol("dy") + " = " + yearSub);
        }
        if (sq.getFilters() != null && !SemanticQueryValidator.orEmpty(sq.getFilters().getConditions()).isEmpty()) {
            String fsql = renderFilter(ctx, sq.getFilters(), params, notes, result.getTranslate());
            if (!fsql.isEmpty()) {
                whereParts.add(fsql);
            }
        }
        if (scope != null) {
            whereParts.add(renderScope(ctx, scope, factTable, params, notes));
        }

        StringBuilder sql = new StringBuilder();
        sql.append("SELECT ").append(String.join(",\n       ", selectParts))
                .append("\nFROM ").append(fromSql);
        if (!joinSqls.isEmpty()) {
            sql.append("\n").append(String.join("\n", joinSqls));
        }
        if (!whereParts.isEmpty()) {
            sql.append("\nWHERE ").append(String.join("\n  AND ", whereParts));
        }
        if (!groupParts.isEmpty()) {
            sql.append("\nGROUP BY ").append(String.join(", ", groupParts));
        }

        // HAVING
        if (sq.getHaving() != null && !SemanticQueryValidator.orEmpty(sq.getHaving().getConditions()).isEmpty()) {
            String hparts = renderHaving(ctx, sq.getHaving(), params);
            if (!hparts.isEmpty()) {
                sql.append("\nHAVING ").append(hparts);
            }
        }

        // 窗口函数（§12 规则 21）
        WindowSpec window = sq.getWindow();
        if (window != null) {
            String wrapped = wrapWindow(sql.toString(), window, notes);
            sql = new StringBuilder(wrapped);
        }

        // ORDER BY
        String orderSql = renderOrderBy(sq, metrics, dims);
        if (!orderSql.isEmpty()) {
            sql.append("\nORDER BY ").append(orderSql);
        } else if (window != null) {
            List<String> ps = new ArrayList<>();
            for (String p : SemanticQueryValidator.orEmpty(window.getPartitionBy())) {
                ps.add("`" + p + "`");
            }
            sql.append("\nORDER BY ").append(String.join(", ", ps));
        }

        // LIMIT
        int limit = Math.min(sq.getLimit() == null ? DEFAULT_LIMIT : sq.getLimit(), MAX_LIMIT);
        sql.append("\nLIMIT ?");
        params.add(limit);

        result.setSql(sql.toString());
        return result;
    }

    // ── 跨源表多指标/复合指标：预聚合子查询对齐 ────────────────
    // 每张源表各自渲染一个「分组维度∪聚合项」子查询（内部独立 JOIN 维度表、独立过滤、
    // 独立 dy 快照策略与 default_filters），外层按分组维度 LEFT JOIN 对齐后做公式算术组合。
    // 聚合先于 JOIN，多事实表双向扇出天然消除（renderScope 半连接防扇出的同族思路）；
    // 非驱动表缺失侧按 0 参与算术（COALESCE），驱动表为 sourceTables 排序首表（确定性）

    /** 聚合项提取：AGG(DISTINCT? db.tbl.col)；跨表公式的合法构造仅此与算术运算符/数字/括号 */
    private static final Pattern AGG_TERM = Pattern.compile(
            "(SUM|COUNT|AVG|MIN|MAX)\\s*\\(\\s*(DISTINCT\\s+)?((?:`[^`]+`|\\w+)\\.(?:`[^`]+`|\\w+)\\.(?:`[^`]+`|\\w+))\\s*\\)",
            Pattern.CASE_INSENSITIVE);

    /** 一次聚合项出现（归一键去重合并）：alias 为子查询输出列名（m1、m2…按首现顺序） */
    private static final class AggTerm {
        final String agg;
        final boolean distinct;
        final String table;
        final String col;
        final String alias;

        AggTerm(String agg, boolean distinct, String table, String col, String alias) {
            this.agg = agg;
            this.distinct = distinct;
            this.table = table;
            this.col = col;
            this.alias = alias;
        }
    }

    /** 跨源表 metric 查询主入口：术语提取 → 每表预聚合子查询 → 外层对齐组合 */
    private GenResult genMetricMultiTable(SemanticQuery sq, List<String> metrics,
                                          List<String> dims, Set<String> sourceTables) {
        if (sq.getScopeFilter() != null) {
            throw new SqlGenException("跨源表查询暂不支持 scope_filter（半连接范围过滤），请拆分查询");
        }
        GenResult result = new GenResult();
        List<Object> params = result.getParams();
        List<String> notes = result.getNotes();

        // 公式内 DISTINCT COUNT( 归一为 COUNT(DISTINCT （同 rewriteFormula 口径）
        Map<String, String> formulas = new LinkedHashMap<>();
        for (String mname : metrics) {
            String f = layers.current().metricMap().get(mname).getFormula();
            formulas.put(mname, f == null ? "" : f.replaceAll("(?i)\\bDISTINCT\\s+COUNT\\s*\\(", "COUNT(DISTINCT "));
        }

        // 聚合项收集（跨全部公式，首现顺序编号）+ 公式合法构造校验
        LinkedHashMap<String, AggTerm> terms = new LinkedHashMap<>();
        for (String mname : metrics) {
            String formula = formulas.get(mname);
            Matcher mt = AGG_TERM.matcher(formula);
            while (mt.find()) {
                String agg = mt.group(1).toUpperCase(Locale.ROOT);
                boolean distinct = mt.group(2) != null;
                String[] tc = normTableCol(mt.group(3));
                if (!sourceTables.contains(tc[0])) {
                    throw new SqlGenException(String.format(
                            "跨表指标 '%s' 公式引用表 '%s' 不在 source_tables 声明范围内（%s）",
                            mname, tc[0], sourceTables));
                }
                String key = agg + "|" + distinct + "|" + tc[0] + "." + tc[1];
                if (!terms.containsKey(key)) {
                    terms.put(key, new AggTerm(agg, distinct, tc[0], tc[1], "m" + (terms.size() + 1)));
                }
            }
            String residue = AGG_TERM.matcher(formula).replaceAll("");
            if (!residue.replaceAll("[0-9+\\-*/().\\s]", "").isEmpty()) {
                throw new SqlGenException(String.format(
                        "跨表指标 '%s' 公式含不支持的构造（仅允许聚合项 SUM/COUNT/AVG/MIN/MAX(db.tbl.col) 与算术运算）: %s",
                        mname, formula));
            }
        }
        if (terms.isEmpty()) {
            throw new SqlGenException("跨源表查询公式未提取到任何聚合项（须形如 SUM(db.tbl.col)）");
        }
        // 表 → 该表聚合项（sourceTables 迭代序即渲染序）
        Map<String, List<AggTerm>> termsByTable = new LinkedHashMap<>();
        for (String t : sourceTables) {
            termsByTable.put(t, new ArrayList<>());
        }
        for (AggTerm t : terms.values()) {
            termsByTable.get(t.table).add(t);
        }

        // 维度/过滤维度从每张源表须可达（同实体或 JOIN 路径；dy 列特例免 JOIN——本表自带）
        List<String> filterDims = SemanticQueryValidator.collectFilterDims(sq.getFilters());
        for (String t : sourceTables) {
            String ent = layers.current().tableToEntity().get(t);
            for (String d : concat(dims, filterDims)) {
                if (isLocalDyDim(d, t)) {
                    continue;
                }
                String dimEnt = layers.current().dimEntityOf(d);
                if (!dimEnt.equals(ent) && !layers.current().hasJoinPath(ent, dimEnt)) {
                    throw new SqlGenException(String.format(
                            "跨表查询维度 '%s'（实体 %s）从源表 '%s' 不可达（无 JOIN 路径），无法在各源表统一分组口径",
                            d, dimEnt, t));
                }
            }
        }

        // 每源表渲染预聚合子查询（params 按子查询顺序追加，与 SQL 文本顺序一致）
        Map<String, String> outerAliasOf = new LinkedHashMap<>();
        List<String> subSqls = new ArrayList<>();
        int seq = 0;
        for (String t : sourceTables) {
            outerAliasOf.put(t, "jt" + (++seq));
            subSqls.add(buildPreAggSub(sq, t, dims, termsByTable.get(t), params, notes, result));
        }
        String driving = outerAliasOf.get(sourceTables.iterator().next());

        // 外层 SELECT：维度取驱动表，公式逐项替换为 jtX.mK（非驱动表包 COALESCE 0）
        List<String> selectParts = new ArrayList<>();
        for (String d : dims) {
            selectParts.add(driving + ".`" + d + "` AS `" + d + "`");
            DimensionDef dimDef = layers.current().dimensionMap().get(d);
            result.getColumns().add(new ColumnInfo(d, "dimension", dimDef.getDisplayName()));
            if (layers.current().domainOfDim(d) != null) {
                result.getTranslate().put(d, d);
            }
        }
        Map<String, String> outerFormulas = new LinkedHashMap<>();
        for (String mname : metrics) {
            String of = outerFormula(formulas.get(mname), terms, outerAliasOf, driving);
            outerFormulas.put(mname, of);
            selectParts.add(of + " AS `" + mname + "`");
            result.getColumns().add(new ColumnInfo(mname, "metric",
                    layers.current().metricMap().get(mname).getDisplayName()));
        }

        StringBuilder sql = new StringBuilder();
        sql.append("SELECT ").append(String.join(",\n       ", selectParts))
                .append("\nFROM (\n").append(subSqls.get(0)).append("\n) AS ").append(driving);
        for (int j = 1; j < subSqls.size(); j++) {
            String alias = outerAliasOf.get(new ArrayList<>(sourceTables).get(j));
            if (dims.isEmpty()) {
                // 总量口径：每子查询单行聚合，交叉连接即对齐
                sql.append("\nCROSS JOIN (\n").append(subSqls.get(j)).append("\n) AS ").append(alias);
            } else {
                List<String> onParts = new ArrayList<>();
                for (String d : dims) {
                    onParts.add(driving + ".`" + d + "` = " + alias + ".`" + d + "`");
                }
                sql.append("\nLEFT JOIN (\n").append(subSqls.get(j)).append("\n) AS ").append(alias)
                        .append("\n  ON ").append(String.join(" AND ", onParts));
            }
        }

        // HAVING（公式已替换为外层列引用）
        if (sq.getHaving() != null && !SemanticQueryValidator.orEmpty(sq.getHaving().getConditions()).isEmpty()) {
            String hparts = renderHavingOuter(sq.getHaving(), outerFormulas, params);
            if (!hparts.isEmpty()) {
                sql.append("\nHAVING ").append(hparts);
            }
        }

        notes.add("跨源表 composite：源表 " + String.join(", ", sourceTables)
                + " 各自预聚合后按维度 " + (dims.isEmpty() ? "（总量口径）" : String.join(", ", dims))
                + " 对齐连接，缺失侧按 0 参与算术；主表 " + sourceTables.iterator().next());

        // 窗口函数（WITH _base 包装，机制同单表）
        WindowSpec window = sq.getWindow();
        if (window != null) {
            sql = new StringBuilder(wrapWindow(sql.toString(), window, notes));
        }

        // ORDER BY
        String orderSql = renderOrderBy(sq, metrics, dims);
        if (!orderSql.isEmpty()) {
            sql.append("\nORDER BY ").append(orderSql);
        } else if (window != null) {
            List<String> ps = new ArrayList<>();
            for (String p : SemanticQueryValidator.orEmpty(window.getPartitionBy())) {
                ps.add("`" + p + "`");
            }
            sql.append("\nORDER BY ").append(String.join(", ", ps));
        }

        int limit = Math.min(sq.getLimit() == null ? DEFAULT_LIMIT : sq.getLimit(), MAX_LIMIT);
        sql.append("\nLIMIT ?");
        params.add(limit);

        result.setSql(sql.toString());
        return result;
    }

    /** 单源表预聚合子查询：SELECT 维度..., 聚合项... FROM 表 JOIN 维度实体 WHERE 本表过滤 GROUP BY 维度表达式。
     *  维度列引用含 dy 特例（year 维且本表自带 dy 字段时直接用本表列，免拉维度原表）；
     *  过滤渲染的值域映射 note 去重合并（同一过滤在每张源表各渲染一次） */
    private String buildPreAggSub(SemanticQuery sq, String table, List<String> dims,
                                  List<AggTerm> terms, List<Object> params,
                                  List<String> notes, GenResult result) {
        AliasCtx ctx = new AliasCtx("s");
        String ent = layers.current().tableToEntity().get(table);

        Set<String> needed = new TreeSet<>();
        List<String> selectParts = new ArrayList<>();
        List<String> groupParts = new ArrayList<>();
        for (String d : dims) {
            DimensionDef dimDef = layers.current().dimensionMap().get(d);
            String colref = dimColrefLocal(ctx, d, dimDef, table, ent, needed);
            String groupExpr = granularityExpr(dimDef, colref);
            selectParts.add(groupExpr + " AS `" + d + "`");
            groupParts.add(groupExpr);
        }
        for (AggTerm t : terms) {
            selectParts.add(t.agg + "(" + (t.distinct ? "DISTINCT " : "") + quoteCol(t.col) + ") AS `" + t.alias + "`");
        }

        String[] from = buildFrom(ctx, ent, needed);
        String fromSql = from[0];
        List<String> joinSqls = new ArrayList<>(Arrays.asList(from).subList(1, from.length));

        List<String> whereParts = new ArrayList<>();
        appendDefaultFilters(ctx, table, whereParts);
        appendRowPolicies(ctx, table, whereParts, params, notes);
        String yearSub = yearConditions(sq.getFilters(), dims, table, notes);
        if (yearSub != null) {
            whereParts.add(ctx.aliasOf(table) + "." + quoteCol("dy") + " = " + yearSub);
        }
        if (sq.getFilters() != null && !SemanticQueryValidator.orEmpty(sq.getFilters().getConditions()).isEmpty()) {
            List<String> subNotes = new ArrayList<>();
            String fsql = renderFilter(ctx, sq.getFilters(), params, subNotes, result.getTranslate(), table);
            if (!fsql.isEmpty()) {
                whereParts.add(fsql);
            }
            for (String n : subNotes) {
                if (!notes.contains(n)) {
                    notes.add(n);
                }
            }
        }

        StringBuilder sb = new StringBuilder();
        sb.append("SELECT ").append(String.join(",\n       ", selectParts))
                .append("\nFROM ").append(fromSql);
        if (!joinSqls.isEmpty()) {
            sb.append("\n").append(String.join("\n", joinSqls));
        }
        if (!whereParts.isEmpty()) {
            sb.append("\nWHERE ").append(String.join("\n  AND ", whereParts));
        }
        if (!groupParts.isEmpty()) {
            sb.append("\nGROUP BY ").append(String.join(", ", groupParts));
        }
        return sb.toString();
    }

    /** 维度列引用（子查询内）：year 维（列=dy）且本表自带 dy 字段时直接用本表列免 JOIN；
     *  否则按维度所属实体计入 needed 由 buildFrom 拉路径 */
    private String dimColrefLocal(AliasCtx ctx, String dimName, DimensionDef dimDef,
                                  String table, String ent, Set<String> needed) {
        String[] tc = SemanticLayer.splitExpr(dimDef.getExpression());
        if ("dy".equals(tc[1]) && hasField(table, "dy")) {
            return ctx.aliasOf(table) + "." + quoteCol("dy");
        }
        String dimEnt = layers.current().dimEntityOf(dimName);
        if (!dimEnt.equals(ent)) {
            needed.add(dimEnt);
        }
        return rewriteExpr(ctx, dimDef.getExpression());
    }

    /** year 维（列=dy）且指定表自带 dy 字段：该表内可用本表列渲染，免 JOIN 即可达 */
    private boolean isLocalDyDim(String dimName, String table) {
        DimensionDef dimDef = layers.current().dimensionMap().get(dimName);
        return dimDef != null
                && "dy".equals(SemanticLayer.splitExpr(dimDef.getExpression())[1])
                && hasField(table, "dy");
    }

    /** 外层公式重建：公式内每个聚合项替换为 jtX.`mK`（非驱动表包 COALESCE 0），其余文本原样保留 */
    private String outerFormula(String formula, LinkedHashMap<String, AggTerm> terms,
                                Map<String, String> outerAliasOf, String driving) {
        StringBuilder out = new StringBuilder();
        Matcher mt = AGG_TERM.matcher(formula);
        int pos = 0;
        while (mt.find()) {
            out.append(formula, pos, mt.start());
            String agg = mt.group(1).toUpperCase(Locale.ROOT);
            boolean distinct = mt.group(2) != null;
            String[] tc = normTableCol(mt.group(3));
            AggTerm t = terms.get(agg + "|" + distinct + "|" + tc[0] + "." + tc[1]);
            String ref = outerAliasOf.get(t.table) + ".`" + t.alias + "`";
            out.append(outerAliasOf.get(t.table).equals(driving) ? ref : "COALESCE(" + ref + ", 0)");
            pos = mt.end();
        }
        out.append(formula, pos, formula.length());
        return out.toString();
    }

    /** `db`.`tbl`.`col` / db.tbl.col 三段引用归一为 [db.tbl, col] */
    private static String[] normTableCol(String ref) {
        String[] parts = ref.replace("`", "").split("\\.");
        return new String[]{parts[0] + "." + parts[1], parts[2]};
    }

    /** 外层 HAVING：条件公式用预替换的外层列引用版（jtX.`mK`），而非子查询别名空间 */
    private String renderHavingOuter(FilterNode node, Map<String, String> outerFormulas, List<Object> params) {
        String op = node.operatorOrDefault();
        List<String> parts = new ArrayList<>();
        for (FilterNode cond : SemanticQueryValidator.orEmpty(node.getConditions())) {
            if (cond.isGroup()) {
                String sub = renderHavingOuter(cond, outerFormulas, params);
                if (!sub.isEmpty()) {
                    parts.add("(" + sub + ")");
                }
                continue;
            }
            String mname = cond.getMetric();
            String formula = outerFormulas.get(mname);
            if (formula == null) {
                throw new SqlGenException("having 指标 '" + mname + "' 不在查询指标清单中");
            }
            String hop = cond.getOperator() == null ? "=" : cond.getOperator();
            Object value = cond.getValue();
            if ("between".equals(hop)) {
                List<?> vals = (List<?>) value;
                parts.add(formula + " BETWEEN ? AND ?");
                params.add(vals.get(0));
                params.add(vals.get(1));
            } else if ("is_null".equals(hop)) {
                parts.add(formula + " IS NULL");
            } else if (SQL_OPS.containsKey(hop)) {
                parts.add(formula + " " + SQL_OPS.get(hop) + " ?");
                params.add(value);
            } else {
                throw new SqlGenException("不支持的 having 操作符: " + hop);
            }
        }
        return String.join(" " + op + " ", parts);
    }

    // ── list 查询 ─────────────────────────────────────────────

    private GenResult genList(SemanticQuery sq) {
        AliasCtx ctx = new AliasCtx("");
        GenResult result = new GenResult();
        List<Object> params = result.getParams();
        List<String> notes = result.getNotes();

        String entName = sq.getEntity();
        EntityDef ent = layers.current().entityMap().get(entName);
        if (ent == null) {
            throw new SqlGenException("实体 '" + entName + "' 未定义");
        }
        if (!Boolean.TRUE.equals(ent.getListable())) {
            throw new SqlGenException("实体 '" + entName + "' 不可列表");
        }

        List<String> fields = SemanticQueryValidator.orEmpty(sq.getFields()).isEmpty()
                ? SemanticQueryValidator.orEmpty(ent.getDisplayFields())
                : sq.getFields();
        Set<String> fieldNames = new HashSet<>();
        for (EntityDef.EntityFieldDef f : SemanticQueryValidator.orEmpty(ent.getFields())) {
            fieldNames.add(f.getName());
        }
        for (String f : fields) {
            if (!fieldNames.contains(f)) {
                throw new SqlGenException(String.format("字段 '%s' 不在实体 '%s' 字段清单中", f, entName));
            }
        }

        // 去重规则：fields 未覆盖全部主键 → DISTINCT
        Set<String> pk = new HashSet<>(SemanticQueryValidator.orEmpty(ent.getPrimaryKey()));
        boolean distinct = !new HashSet<>(fields).containsAll(pk);

        String table = ent.getTable();
        String entAlias = ctx.aliasOf(table);
        List<String> selectParts = new ArrayList<>();
        for (String f : fields) {
            EntityDef.EntityFieldDef fld = findField(ent, f);
            selectParts.add(entAlias + "." + quoteCol(f) + " AS `" + f + "`");
            result.getColumns().add(new ColumnInfo(f, "field",
                    fld.getDisplayName() == null ? f : fld.getDisplayName()));
            if (fld.getValueDomain() != null && !fld.getValueDomain().isEmpty()) {
                result.getTranslate().put(f, "_entity_field:" + entName + "." + f);
            }
        }

        // 过滤/排序引用的维度 → 需要 JOIN 的实体
        Set<String> needed = new TreeSet<>();
        for (String d : SemanticQueryValidator.collectFilterDims(sq.getFilters())) {
            String e = layers.current().dimEntityOf(d);
            if (!e.equals(entName)) {
                needed.add(e);
            }
        }
        for (OrderByItem ob : SemanticQueryValidator.orEmpty(sq.getOrderBy())) {
            String d = ob.getField();
            if (layers.current().dimensionMap().containsKey(d)) {
                String e = layers.current().dimEntityOf(d);
                if (!e.equals(entName)) {
                    needed.add(e);
                }
            }
        }

        String[] from = buildFrom(ctx, entName, needed);
        String fromSql = from[0];
        List<String> joinSqls = new ArrayList<>(Arrays.asList(from).subList(1, from.length));

        List<String> whereParts = new ArrayList<>();
        if (!treeHasYearFilter(sq.getFilters()) && hasField(table, "dy")) {
            String sub = "(SELECT MAX(dy) FROM " + qualifyTable(table) + ")";
            whereParts.add(entAlias + "." + quoteCol("dy") + " = " + sub);
            notes.add("未指定快照年份，默认取最新快照年");
        }
        appendDefaultFilters(ctx, table, whereParts);
        appendRowPolicies(ctx, table, whereParts, params, notes);
        if (sq.getFilters() != null && !SemanticQueryValidator.orEmpty(sq.getFilters().getConditions()).isEmpty()) {
            String fsql = renderFilter(ctx, sq.getFilters(), params, notes, result.getTranslate());
            if (!fsql.isEmpty()) {
                whereParts.add(fsql);
            }
        }

        StringBuilder sql = new StringBuilder();
        sql.append("SELECT ").append(distinct ? "DISTINCT " : "")
                .append(String.join(",\n       ", selectParts))
                .append("\nFROM ").append(fromSql);
        if (!joinSqls.isEmpty()) {
            sql.append("\n").append(String.join("\n", joinSqls));
        }
        if (!whereParts.isEmpty()) {
            sql.append("\nWHERE ").append(String.join("\n  AND ", whereParts));
        }

        // ORDER BY：字段名或维度表达式
        List<String> oparts = new ArrayList<>();
        Set<String> valid = new HashSet<>(fields);
        valid.addAll(layers.current().dimensionMap().keySet());
        for (OrderByItem ob : SemanticQueryValidator.orEmpty(sq.getOrderBy())) {
            String f = ob.getField();
            if (!valid.contains(f)) {
                throw new SqlGenException("排序字段 '" + f + "' 不在白名单中");
            }
            if (fields.contains(f)) {
                oparts.add("`" + f + "` " + ob.directionOrDefault("asc").toUpperCase());
            } else {
                String expr = layers.current().dimensionMap().get(f).getExpression();
                oparts.add(rewriteExpr(ctx, expr) + " " + ob.directionOrDefault("asc").toUpperCase());
            }
        }
        if (!oparts.isEmpty()) {
            sql.append("\nORDER BY ").append(String.join(", ", oparts));
        }

        int limit = Math.min(sq.getLimit() == null ? DEFAULT_LIMIT : sq.getLimit(), MAX_LIMIT);
        sql.append("\nLIMIT ?");
        params.add(limit);

        result.setSql(sql.toString());
        return result;
    }

    // ── 表集合与 JOIN 渲染 ────────────────────────────────────

    /** 返回 [fromSql, joinSql...]；JOIN 附加 dy 对齐（同一快照时点内关联） */
    private String[] buildFrom(AliasCtx ctx, String factEntName, Set<String> neededEntities) {
        EntityDef fact = layers.current().entityMap().get(factEntName);
        String factTbl = fact.getTable();
        String factAlias = ctx.aliasOf(factTbl);
        Set<String> joined = new HashSet<>();
        joined.add(factEntName);
        List<String> joinSqls = new ArrayList<>();
        for (String entName : neededEntities) {
            if (joined.contains(entName)) {
                continue;
            }
            for (Object[] edge : layers.current().joinPath(factEntName, entName)) {
                String src = (String) edge[0];
                String dst = (String) edge[1];
                RelationDef rel = (RelationDef) edge[2];
                if (joined.contains(dst)) {
                    continue;
                }
                String srcTbl = layers.current().entityMap().get(src).getTable();
                String dstTbl = layers.current().entityMap().get(dst).getTable();
                String srcA = ctx.aliasOf(srcTbl);
                String dstA = ctx.aliasOf(dstTbl);
                List<String> conds = new ArrayList<>();
                for (RelationDef.JoinKey jp : SemanticQueryValidator.orEmpty(rel.getJoin())) {
                    // left 属于 from_entity(src)，right 属于 to_entity(dst)
                    conds.add(srcA + "." + quoteCol(jp.getLeft())
                            + " = " + dstA + "." + quoteCol(jp.getRight()));
                }
                // 年快照对齐：同一快照时点内关联
                conds.add(srcA + "." + quoteCol("dy") + " = " + dstA + "." + quoteCol("dy"));
                joinSqls.add("LEFT JOIN " + qualifyTable(dstTbl) + " AS " + dstA
                        + " ON " + String.join(" AND ", conds));
                joined.add(dst);
            }
        }
        List<String> out = new ArrayList<>();
        out.add(qualifyTable(factTbl) + " AS " + factAlias);
        out.addAll(joinSqls);
        return out.toArray(new String[0]);
    }

    private static String qualifyTable(String table) {
        String[] parts = table.split("\\.");
        return "`" + parts[0] + "`.`" + parts[1] + "`";
    }

    private static String quoteCol(String col) {
        return "`" + col + "`";
    }

    /** db.tbl.col → alias.col（MySQL 语法系不支持三段式列引用） */
    private String rewriteExpr(AliasCtx ctx, String expression) {
        String[] tc = SemanticLayer.splitExpr(expression);
        return ctx.aliasOf(tc[0]) + "." + quoteCol(tc[1]);
    }

    /**
     * 指标公式中的 db.tbl.col 引用改写为 alias.col。
     * 长 token 优先替换，且要求后界非标识符字符，防止 year 误吃 year_real_rate 前缀。
     * 资产书写约定 'DISTINCT COUNT(col)' 归一为标准 SQL 'COUNT(DISTINCT col)'
     */
    private String rewriteFormula(AliasCtx ctx, String formula) {
        String out = formula.replaceAll("(?i)\\bDISTINCT\\s+COUNT\\s*\\(", "COUNT(DISTINCT ");
        List<String[]> tokens = new ArrayList<>();
        for (EntityDef ent : layers.current().entities()) {
            String table = ent.getTable();
            for (EntityDef.EntityFieldDef f : SemanticQueryValidator.orEmpty(ent.getFields())) {
                tokens.add(new String[]{table + "." + f.getName(), table, f.getName()});
            }
        }
        tokens.sort((a, b) -> Integer.compare(b[0].length(), a[0].length()));
        for (String[] t : tokens) {
            String token = t[0];
            int idx = out.indexOf(token);
            while (idx >= 0) {
                int end = idx + token.length();
                char next = end < out.length() ? out.charAt(end) : 0;
                if (!Character.isLetterOrDigit(next) && next != '_') {
                    out = out.substring(0, idx) + ctx.aliasOf(t[1]) + "." + quoteCol(t[2])
                            + out.substring(end);
                }
                idx = out.indexOf(token, idx + 1);
            }
        }
        return out;
    }

    // ── 实体缺省过滤 / 字段存在性 ─────────────────────────────

    /** 实体 default_filters（可信资产）自动追加主表等值条件，如启用+已审核 */
    private void appendDefaultFilters(AliasCtx ctx, String table, List<String> whereParts) {
        String entName = layers.current().tableToEntity().get(table);
        EntityDef ent = entName == null ? null : layers.current().entityMap().get(entName);
        if (ent == null || ent.getDefaultFilters() == null) {
            return;
        }
        for (EntityDef.DefaultFilter df : ent.getDefaultFilters()) {
            whereParts.add(ctx.aliasOf(table) + "." + quoteCol(df.getField()) + " = " + literal(df.getValue()));
        }
    }

    /** 缺省过滤值字面量（资产可信；字符串单引号转义） */
    private static String literal(Object value) {
        if (value instanceof Number) {
            return String.valueOf(value);
        }
        return "'" + String.valueOf(value).replace("'", "''") + "'";
    }

    /**
     * 行级权限注入（P2-2）：row-policies 资产按表追加 WHERE 谓词，值全部参数化绑定
     * （登录态模板 ${user.xxx} 由用户上下文解析，不拼字面量）。fail-closed：表配了策略
     * 但无用户上下文/变量无值时抛 SqlGenException 拒绝生成，绝不静默放行越权全量数据；
     * 无策略的表零影响。注入位置=事实表/明细表（含跨表 composite 的预聚合子查询），
     * JOIN 维表为公共维度数据不注入
     */
    private void appendRowPolicies(AliasCtx ctx, String table, List<String> whereParts,
                                   List<Object> params, List<String> notes) {
        List<RowPolicyDef.Policy> policies = layers.current().rowPoliciesOf(table);
        if (policies.isEmpty()) {
            return;
        }
        RowPolicyUserContext uc = USER_CTX.get();
        if (uc == null) {
            throw new SqlGenException("表 '" + table + "' 配置了行级权限策略，当前链路无登录用户上下文，"
                    + "拒绝生成（fail-closed；后台链路请移除该表策略或改走管理员通道）");
        }
        for (RowPolicyDef.Policy p : policies) {
            String colref = ctx.aliasOf(table) + "." + quoteCol(p.getColumn());
            String op = p.getOp() == null ? "" : p.getOp();
            if ("in".equals(op) || "not_in".equals(op)) {
                if (!(p.getValue() instanceof List)) {
                    throw new SqlGenException("行权限策略 '" + table + "." + p.getColumn()
                            + "' 操作符为 " + op + "，value 必须为数组");
                }
                List<String> holders = new ArrayList<>();
                for (Object v : (List<?>) p.getValue()) {
                    holders.add("?");
                    params.add(resolvePolicyValue(uc, v));
                }
                whereParts.add(colref + ("in".equals(op) ? " IN (" : " NOT IN (")
                        + String.join(", ", holders) + ")");
            } else if (SQL_OPS.containsKey(op)) {
                whereParts.add(colref + " " + SQL_OPS.get(op) + " ?");
                params.add(resolvePolicyValue(uc, p.getValue()));
            } else {
                throw new SqlGenException("行权限策略 '" + table + "." + p.getColumn()
                        + "' 操作符 '" + op + "' 不在白名单（= != > >= < <= in not_in）");
            }
        }
        notes.add("表 '" + table + "' 已按行级权限策略过滤（" + policies.size() + " 条，对查询方不可见）");
    }

    /** 策略值解析：字符串走登录态模板（无模板原样返回），非字符串常量直接绑定 */
    private static Object resolvePolicyValue(RowPolicyUserContext uc, Object v) {
        return v instanceof String ? uc.resolve((String) v) : v;
    }

    /** 表的实体是否声明了指定字段（dy 快照默认策略等场景用） */
    private boolean hasField(String table, String field) {
        String entName = layers.current().tableToEntity().get(table);
        EntityDef ent = entName == null ? null : layers.current().entityMap().get(entName);
        if (ent == null) {
            return false;
        }
        for (EntityDef.EntityFieldDef f : SemanticQueryValidator.orEmpty(ent.getFields())) {
            if (field.equals(f.getName())) {
                return true;
            }
        }
        return false;
    }

    // ── 年份（dy）默认策略 ────────────────────────────────────

    /** db.tbl.col 列引用中的 db.tbl 部分（formula 兑底提取源表用；容忍反引号写法） */
    private static final Pattern FORMULA_TABLE_REF =
            Pattern.compile("((?:`[^`]+`|\\w+)\\.(?:`[^`]+`|\\w+))\\s*\\.\\s*(?:`[^`]+`|\\w+)");

    /** 指标涉及源表并集：composite 取 source_tables 声明，atomic 取 source_table，
     *  两处均缺时从公式 db.tbl.col 引用兑底提取（表名原样，与语义层表名同口径） */
    private static Set<String> collectMetricTables(MetricDef m) {
        Set<String> out = new TreeSet<>();
        if (m.getSourceTables() != null) {
            for (String t : m.getSourceTables()) {
                if (t != null && !t.trim().isEmpty()) {
                    out.add(t.trim());
                }
            }
        }
        if (m.getSourceTable() != null && !m.getSourceTable().trim().isEmpty()) {
            out.add(m.getSourceTable().trim());
        }
        if (out.isEmpty() && m.getFormula() != null) {
            Matcher mt = FORMULA_TABLE_REF.matcher(m.getFormula());
            while (mt.find()) {
                out.add(mt.group(1));
            }
        }
        return out;
    }

    /** 粒度维度渲染：granularity 有值时对列引用包日期函数后返回（year→YEAR(x)，
     *  month→'YYYY-MM' 字符串，quarter→'YYYY-Qn'，后两者带年份避免跨年同月/同季混淆）；
     *  无粒度或未知粒度原样返回 */
    private static String granularityExpr(DimensionDef dim, String colref) {
        String g = dim.getGranularity();
        if (g == null || g.isEmpty()) {
            return colref;
        }
        switch (g) {
            case "year":
                return "YEAR(" + colref + ")";
            case "month":
                return "DATE_FORMAT(" + colref + ", '%Y-%m')";
            case "quarter":
                return "CONCAT(YEAR(" + colref + "), '-Q', QUARTER(" + colref + "))";
            default:
                return colref;
        }
    }

    /** 未显式指定年份且未按年份分组时返回 MAX(dy) 子查询，否则返回 null；实体无 dy 字段（非快照模型）不适用 */
    private String yearConditions(FilterNode filters, List<String> groupDims, String factTable, List<String> notes) {
        if (!hasField(factTable, "dy")) {
            return null;
        }
        boolean hasExplicitYear = treeHasYearFilter(filters);
        boolean groupByYear = false;
        for (String d : groupDims) {
            if (isYearDim(d)) {
                groupByYear = true;
                break;
            }
        }
        if (hasExplicitYear || groupByYear) {
            return null;
        }
        notes.add(String.format("未指定快照年份，默认取最新快照年（%s 的 MAX(dy)）", factTable));
        return "(SELECT MAX(dy) FROM " + qualifyTable(factTable) + ")";
    }

    private boolean isYearDim(String dimName) {
        if (!layers.current().dimensionMap().containsKey(dimName)) {
            return false;
        }
        return SemanticLayer.splitExpr(layers.current().dimensionMap().get(dimName).getExpression())[1].equals("dy");
    }

    private boolean treeHasYearFilter(FilterNode node) {
        if (node == null) {
            return false;
        }
        for (FilterNode cond : SemanticQueryValidator.orEmpty(node.getConditions())) {
            if (cond.isGroup()) {
                if (treeHasYearFilter(cond)) {
                    return true;
                }
            } else if (cond.getDimension() != null && isYearDim(cond.getDimension())) {
                return true;
            }
        }
        return false;
    }

    // ── 过滤树渲染 ────────────────────────────────────────────

    private String renderFilter(AliasCtx ctx, FilterNode node, List<Object> params,
                                List<String> notes, Map<String, String> translate) {
        return renderFilter(ctx, node, params, notes, translate, null);
    }

    /** dyOverrideTable 非空时：year 维（列=dy）且该表自带 dy 字段的过滤条件改用该表本表列渲染
     *  （跨表预聚合子查询免拉维度原表；其余维度仍按原表别名渲染） */
    private String renderFilter(AliasCtx ctx, FilterNode node, List<Object> params,
                                List<String> notes, Map<String, String> translate, String dyOverrideTable) {
        String op = node.operatorOrDefault();
        List<String> parts = new ArrayList<>();
        for (FilterNode cond : SemanticQueryValidator.orEmpty(node.getConditions())) {
            if (cond.isGroup()) {
                String sub = renderFilter(ctx, cond, params, notes, translate, dyOverrideTable);
                if (!sub.isEmpty()) {
                    parts.add("(" + sub + ")");
                }
                continue;
            }
            String dimName = cond.getDimension();
            if (!layers.current().dimensionMap().containsKey(dimName)) {
                throw new SqlGenException("过滤维度 '" + dimName + "' 未定义");
            }
            DimensionDef dimDef = layers.current().dimensionMap().get(dimName);
            String colref;
            if (dyOverrideTable != null
                    && "dy".equals(SemanticLayer.splitExpr(dimDef.getExpression())[1])
                    && hasField(dyOverrideTable, "dy")) {
                colref = ctx.aliasOf(dyOverrideTable) + "." + quoteCol("dy");
            } else {
                colref = rewriteExpr(ctx, dimDef.getExpression());
            }
            String fop = cond.getOperator() == null ? "=" : cond.getOperator();
            Object value = cond.getValue();
            // 多值维度（逗号分隔 code 串，match=multi）：等值/in 改写 FIND_IN_SET 包含匹配，
            // 直等值会把「A,B」整串当单值漏行；比较/区间类语义不成立直接拒（防静默失真）
            if ("multi".equals(dimDef.getMatch())) {
                parts.add(renderMultiValueFilter(dimName, colref, fop, value, params, notes));
                continue;
            }
            if ("is_null".equals(fop)) {
                parts.add(colref + " IS NULL");
            } else if ("between".equals(fop)) {
                List<?> vals = (List<?>) value;
                Object v1 = layers.current().resolveValue(dimName, vals.get(0));
                Object v2 = layers.current().resolveValue(dimName, vals.get(1));
                parts.add(colref + " BETWEEN ? AND ?");
                params.add(v1);
                params.add(v2);
            } else if ("in".equals(fop) || "not_in".equals(fop)) {
                String sqlOp = "in".equals(fop) ? "IN" : "NOT IN";
                List<?> vals = (List<?>) value;
                List<Object> resolved = new ArrayList<>();
                for (Object v : vals) {
                    resolved.add(layers.current().resolveValue(dimName, v));
                }
                List<String> holders = new ArrayList<>();
                for (int i = 0; i < resolved.size(); i++) {
                    holders.add("?");
                }
                parts.add(colref + " " + sqlOp + " (" + String.join(", ", holders) + ")");
                params.addAll(resolved);
            } else if (SQL_OPS.containsKey(fop)) {
                Object rv = layers.current().resolveValue(dimName, value);
                if (!java.util.Objects.equals(rv, value) && layers.current().domainOfDim(dimName) != null) {
                    notes.add(String.format("过滤值 '%s' 已按值域映射为存储码值 '%s'", value, rv));
                }
                parts.add(colref + " " + SQL_OPS.get(fop) + " ?");
                params.add(rv);
            } else {
                throw new SqlGenException("不支持的过滤操作符: " + fop);
            }
        }
        return String.join(" " + op + " ", parts);
    }

    /** 多值维度（逗号分隔）过滤改写：=/in 转 FIND_IN_SET 包含匹配（任一命中即命中），
     *  !=/not_in 转「均不含」（NULL 串视为不含）；值域映射（label→code）照常生效；
     *  比较/区间类在多值列上语义不成立，显式拒绝不静默退化 */
    private String renderMultiValueFilter(String dimName, String colref, String fop, Object value,
                                          List<Object> params, List<String> notes) {
        if ("is_null".equals(fop)) {
            return colref + " IS NULL";
        }
        notes.add(String.format("维度 '%s' 为多值列（逗号分隔），过滤已改写为 FIND_IN_SET 包含匹配", dimName));
        if ("=".equals(fop)) {
            params.add(layers.current().resolveValue(dimName, value));
            return "FIND_IN_SET(?, " + colref + ") > 0";
        }
        if ("!=".equals(fop)) {
            params.add(layers.current().resolveValue(dimName, value));
            return "(" + colref + " IS NULL OR FIND_IN_SET(?, " + colref + ") = 0)";
        }
        if ("in".equals(fop) || "not_in".equals(fop)) {
            List<?> vals = (List<?>) value;
            List<String> subs = new ArrayList<>();
            for (Object v : vals) {
                subs.add("FIND_IN_SET(?, " + colref + ") " + ("in".equals(fop) ? ">" : "=") + " 0");
                params.add(layers.current().resolveValue(dimName, v));
            }
            String joined = String.join(" " + ("in".equals(fop) ? "OR" : "AND") + " ", subs);
            // not_in 语义 = 均不含；NULL 串视为不含任何值（放行）
            return "in".equals(fop) ? "(" + joined + ")" : "(" + colref + " IS NULL OR (" + joined + "))";
        }
        throw new SqlGenException("多值维度 '" + dimName + "' 不支持 '" + fop + "' 过滤（逗号分隔多值列仅支持等值/in 包含匹配）");
    }

    private String renderHaving(AliasCtx ctx, FilterNode node, List<Object> params) {
        String op = node.operatorOrDefault();
        List<String> parts = new ArrayList<>();
        for (FilterNode cond : SemanticQueryValidator.orEmpty(node.getConditions())) {
            if (cond.isGroup()) {
                String sub = renderHaving(ctx, cond, params);
                if (!sub.isEmpty()) {
                    parts.add("(" + sub + ")");
                }
                continue;
            }
            String mname = cond.getMetric();
            MetricDef m = layers.current().metricMap().get(mname);
            if (m == null) {
                throw new SqlGenException("having 指标 '" + mname + "' 未定义");
            }
            String formula = rewriteFormula(ctx, m.getFormula());
            String hop = cond.getOperator() == null ? "=" : cond.getOperator();
            Object value = cond.getValue();
            if ("between".equals(hop)) {
                List<?> vals = (List<?>) value;
                parts.add(formula + " BETWEEN ? AND ?");
                params.add(vals.get(0));
                params.add(vals.get(1));
            } else if ("is_null".equals(hop)) {
                parts.add(formula + " IS NULL");
            } else if (SQL_OPS.containsKey(hop)) {
                parts.add(formula + " " + SQL_OPS.get(hop) + " ?");
                params.add(value);
            } else {
                throw new SqlGenException("不支持的 having 操作符: " + hop);
            }
        }
        return String.join(" " + op + " ", parts);
    }

    // ── scope_filter：跨事实表范围子查询（半连接）────────────
    // 主查询指标与范围指标来自不同事实表时不直接 JOIN（粒度不同会双向扇出），
    // 把范围条件压成 IN 子查询，子查询内各自独立聚合，天然去重不扇出

    private String renderScope(AliasCtx outerCtx, ScopeFilter scope, String factTable,
                               List<Object> params, List<String> notes) {
        String mname = scope.getMetric();
        MetricDef m = layers.current().metricMap().get(mname);
        if (m == null) {
            throw new SqlGenException("scope_filter 指标 '" + mname + "' 未定义");
        }
        if (!"atomic".equals(m.getType())) {
            throw new SqlGenException("scope_filter 指标 '" + mname + "' 仅支持 atomic 类型");
        }
        String scopeTable = m.getSourceTable();
        if (scopeTable.equals(factTable)) {
            throw new SqlGenException("scope_filter 指标与主查询指标同源表，无需跨表范围过滤");
        }

        String linkDim = scope.getDimension();
        if (!layers.current().dimensionMap().containsKey(linkDim)) {
            throw new SqlGenException("scope_filter 关联维度 '" + linkDim + "' 未定义");
        }

        // 子查询使用独立别名空间（s 前缀），避免污染外层别名
        AliasCtx ctx = new AliasCtx("s");
        String scopeEnt = layers.current().tableToEntity().get(scopeTable);
        String linkEnt = layers.current().dimEntityOf(linkDim);
        // JOIN 路径必须存在（scope 事实表 → 关联维度实体）
        layers.current().joinPath(scopeEnt, linkEnt);

        Set<String> needed = new TreeSet<>();
        if (!linkEnt.equals(scopeEnt)) {
            needed.add(linkEnt);
        }
        for (String d : SemanticQueryValidator.collectFilterDims(scope.getFilters())) {
            String e = layers.current().dimEntityOf(d);
            if (!e.equals(scopeEnt)) {
                needed.add(e);
            }
        }

        String[] from = buildFrom(ctx, scopeEnt, needed);
        String fromSql = from[0];
        List<String> joinSqls = new ArrayList<>(Arrays.asList(from).subList(1, from.length));
        String linkRef = rewriteExpr(ctx, layers.current().dimensionMap().get(linkDim).getExpression());

        List<String> whereParts = new ArrayList<>();
        appendDefaultFilters(ctx, scopeTable, whereParts);
        String yearSub = yearConditions(scope.getFilters(), Collections.<String>emptyList(), scopeTable, notes);
        if (yearSub != null) {
            whereParts.add(ctx.aliasOf(scopeTable) + "." + quoteCol("dy") + " = " + yearSub);
        }
        if (scope.getFilters() != null && !SemanticQueryValidator.orEmpty(scope.getFilters().getConditions()).isEmpty()) {
            String fsql = renderFilter(ctx, scope.getFilters(), params, notes, new HashMap<String, String>());
            if (!fsql.isEmpty()) {
                whereParts.add(fsql);
            }
        }

        StringBuilder sql = new StringBuilder();
        sql.append("SELECT ").append(linkRef).append("\nFROM ").append(fromSql);
        if (!joinSqls.isEmpty()) {
            sql.append("\n").append(String.join("\n", joinSqls));
        }
        if (!whereParts.isEmpty()) {
            sql.append("\nWHERE ").append(String.join("\n  AND ", whereParts));
        }
        sql.append("\nGROUP BY ").append(linkRef);

        if (scope.getHaving() != null && !SemanticQueryValidator.orEmpty(scope.getHaving().getConditions()).isEmpty()) {
            for (String hname : SemanticQueryValidator.collectHavingMetrics(scope.getHaving())) {
                MetricDef hm = layers.current().metricMap().get(hname);
                if (hm == null || !scopeTable.equals(hm.getSourceTable())) {
                    throw new SqlGenException(String.format(
                            "scope_filter.having 指标 '%s' 必须与 scope 指标同源表 '%s'", hname, scopeTable));
                }
            }
            String hsql = renderHaving(ctx, scope.getHaving(), params);
            if (!hsql.isEmpty()) {
                sql.append("\nHAVING ").append(hsql);
            }
        }

        notes.add(String.format("跨事实表范围过滤：主查询经 %s 半连接（IN 子查询）圈定于指标 '%s' 满足条件的项目",
                linkDim, mname));
        String outerRef = rewriteExpr(outerCtx, layers.current().dimensionMap().get(linkDim).getExpression());
        String sqlOp = "not_in".equals(scope.getOp()) ? "NOT IN" : "IN";
        return outerRef + " " + sqlOp + " (\n" + sql + "\n)";
    }

    // ── 窗口函数 / ORDER BY ──────────────────────────────────

    private String wrapWindow(String baseSql, WindowSpec window, List<String> notes) {
        List<String> ps = new ArrayList<>();
        for (String p : SemanticQueryValidator.orEmpty(window.getPartitionBy())) {
            ps.add("`" + p + "`");
        }
        List<String> os = new ArrayList<>();
        for (OrderByItem ob : SemanticQueryValidator.orEmpty(window.getOrderBy())) {
            os.add("`" + ob.getField() + "` " + ob.directionOrDefault("desc").toUpperCase());
        }
        int topN = window.getTopN();
        notes.add(String.format("窗口排名：按 [%s] 分组，取每组前 %d 名",
                String.join(", ", SemanticQueryValidator.orEmpty(window.getPartitionBy())), topN));
        return "WITH _base AS (\n" + baseSql + "\n)\n"
                + "SELECT * FROM (\n"
                + "  SELECT _base.*, ROW_NUMBER() OVER (PARTITION BY " + String.join(", ", ps)
                + " ORDER BY " + String.join(", ", os) + ") AS _rn\n"
                + "  FROM _base\n"
                + ") _w\n"
                + "WHERE _w._rn <= " + topN;
    }

    private String renderOrderBy(SemanticQuery sq, List<String> metrics, List<String> dims) {
        Set<String> valid = new HashSet<>(metrics);
        valid.addAll(dims);
        List<String> parts = new ArrayList<>();
        for (OrderByItem ob : SemanticQueryValidator.orEmpty(sq.getOrderBy())) {
            String f = ob.getField();
            if (!valid.contains(f)) {
                throw new SqlGenException("排序字段 '" + f + "' 不在白名单中");
            }
            parts.add("`" + f + "` " + ob.directionOrDefault("asc").toUpperCase());
        }
        return String.join(", ", parts);
    }

    // ── 结果码值翻译（§16.4 出参方向）────────────────────────

    /** rows 按 columns 顺序排列；translate 列的码值替换为业务标签 */
    public List<List<Object>> translateResult(GenResult gen, List<List<Object>> rows) {
        return translateResult(gen, rows, true);
    }

    /** translateEntityFields=false 时实体字段码列（_entity_field 映射）不翻译：
     *  穿透明细由 portal SELECT 字典列自行 code→label 渲染，输出保留码值才能
     *  命中字典；且筛选回传值与码列同为码值，与 WHERE 口径一致 */
    public List<List<Object>> translateResult(GenResult gen, List<List<Object>> rows, boolean translateEntityFields) {
        Map<String, String> translate = gen.getTranslate();
        if (translate.isEmpty()) {
            return rows;
        }
        Map<Integer, String> idxMap = new HashMap<>();
        List<ColumnInfo> columns = gen.getColumns();
        for (int i = 0; i < columns.size(); i++) {
            // 存 translate 映射值（维度列=维度名；实体字段列=_entity_field:实体.字段），
            // 而非列别名——否则 _entity_field 前缀判断失效，明细码列会被当维度翻译
            String tkey = translate.get(columns.get(i).getAlias());
            if (tkey != null) {
                idxMap.put(i, tkey);
            }
        }
        List<List<Object>> out = new ArrayList<>();
        for (List<Object> row : rows) {
            List<Object> r = new ArrayList<>(row);
            for (Map.Entry<Integer, String> e : idxMap.entrySet()) {
                int i = e.getKey();
                String key = e.getValue();
                if (key.startsWith("_entity_field:")) {
                    if (!translateEntityFields) {
                        continue;
                    }
                    String[] ef = key.substring("_entity_field:".length()).split("\\.");
                    EntityDef ent = layers.current().entityMap().get(ef[0]);
                    EntityDef.EntityFieldDef fld = findField(ent, ef[1]);
                    String label = null;
                    if (fld != null && fld.getValueDomain() != null) {
                        com.bidr.insight.smartquery.layer.ValueDomainDef domain =
                                layers.current().domains().get(fld.getValueDomain());
                        if (domain != null && "code".equals(domain.getStoredAs())) {
                            if (r.get(i) != null) {
                                for (com.bidr.insight.smartquery.layer.ValueDomainDef.DomainValue v
                                        : SemanticQueryValidator.orEmpty(domain.getValues())) {
                                    if (String.valueOf(r.get(i)).equals(String.valueOf(v.getCode()))) {
                                        label = v.getLabel();
                                        break;
                                    }
                                }
                            }
                            r.set(i, label != null ? label
                                    : (r.get(i) != null ? "未知码值(" + r.get(i) + ")" : r.get(i)));
                        }
                        // label 型值域：原值本身即业务文本，不翻译
                    }
                } else {
                    com.bidr.insight.smartquery.layer.ValueDomainDef domain =
                            layers.current().domainOfDim(key);
                    if (domain != null && !"code".equals(domain.getStoredAs())) {
                        continue; // label 型值域：原值即业务标签，无需翻译
                    }
                    String label = layers.current().translateBack(key, r.get(i));
                    if (label != null) {
                        r.set(i, label);
                    } else if (r.get(i) != null) {
                        r.set(i, "未知码值(" + r.get(i) + ")");
                    }
                }
            }
            out.add(r);
        }
        return out;
    }

    // ── 工具 ─────────────────────────────────────────────────

    private static EntityDef.EntityFieldDef findField(EntityDef ent, String name) {
        for (EntityDef.EntityFieldDef f : SemanticQueryValidator.orEmpty(ent.getFields())) {
            if (f.getName().equals(name)) {
                return f;
            }
        }
        return null;
    }

    private static List<String> concat(List<String> a, List<String> b) {
        List<String> out = new ArrayList<>(SemanticQueryValidator.orEmpty(a));
        out.addAll(SemanticQueryValidator.orEmpty(b));
        return out;
    }
}
