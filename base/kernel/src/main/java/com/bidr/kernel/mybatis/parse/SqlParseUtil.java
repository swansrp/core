package com.bidr.kernel.mybatis.parse;

import com.bidr.kernel.constant.err.ErrCodeSys;
import com.bidr.kernel.utils.FuncUtil;
import com.bidr.kernel.validate.Validator;
import lombok.extern.slf4j.Slf4j;
import net.sf.jsqlparser.JSQLParserException;
import net.sf.jsqlparser.expression.Expression;
import net.sf.jsqlparser.expression.Parenthesis;
import net.sf.jsqlparser.expression.operators.conditional.AndExpression;
import net.sf.jsqlparser.parser.CCJSqlParserUtil;
import net.sf.jsqlparser.schema.Table;
import net.sf.jsqlparser.statement.select.Join;
import net.sf.jsqlparser.statement.select.PlainSelect;
import net.sf.jsqlparser.statement.select.Select;

import java.util.HashMap;
import java.util.Map;

/**
 * Title: SqlParseUtil
 * Description: Copyright: Copyright (c) 2023 Company: Sharp Ltd.
 *
 * @author Sharp
 * @since 2023/05/02 17:31
 */
@Slf4j
public class SqlParseUtil {

    public static Map<String, String> buildTableAliasMap(String sql) {
        PlainSelect plainSelect = (PlainSelect) getPlainSelect(sql).getSelectBody();
        Map<String, String> mapTable = new HashMap<>();
        if (FuncUtil.isNotEmpty(plainSelect.getFromItem())) {
            if (Table.class.isAssignableFrom(plainSelect.getFromItem().getClass())) {
                Table table = (Table) plainSelect.getFromItem();
                if (table != null) {
                    if (table.getAlias() != null) {
                        mapTable.put(table.getName(), table.getAlias().getName());
                    } else {
                        mapTable.put(table.getName(), null);
                    }
                }

                if (FuncUtil.isNotEmpty(plainSelect.getJoins())) {
                    for (Join join : plainSelect.getJoins()) {
                        Table joinTable = (Table) join.getRightItem();
                        if (joinTable.getAlias() != null) {
                            mapTable.put(joinTable.getName(), joinTable.getAlias().getName());
                        } else {
                            mapTable.put(joinTable.getName(), null);
                        }
                    }
                }
            }
        }
        return mapTable;
    }

    public static Select getPlainSelect(String sql) {
        Select select = null;
        try {
            select = (Select) CCJSqlParserUtil.parse(collapseBlankLines(sql),
                    ccjSqlParser -> ccjSqlParser.withSquareBracketQuotation(true));
        } catch (JSQLParserException e) {
            log.error(sql);
            Validator.assertException(ErrCodeSys.PA_PARAM_FORMAT, "SQL");
        }
        return select;
    }

    /**
     * 压掉连续空行后再交给解析器。
     *
     * <p>MyBatis-Plus 的 insert / updateById 模板按字段生成 {@code <if>}，被跳过的 null 字段会在
     * 语句文本里留下空行；而 JSqlParser <b>4.6</b>（本模块当前钉的版本）对"任意子句里连续 3 个换行"
     * 直接抛 {@code ParseException}（4.4 可以）。{@code @DataScope} 拿到的正是这种运行时 SQL 文本，
     * 不规整就会在最常见的"实体有几个空字段"上炸掉。</p>
     *
     * <p>纯空白变换：值一律走 {@code ?} 占位符，不触碰任何字面量。修在这个唯一解析入口里，
     * 而不是让每个业务拦截器各打一份补丁。</p>
     */
    private static String collapseBlankLines(String sql) {
        return sql != null && sql.contains("\n\n") ? sql.replaceAll("\n+", "\n") : sql;
    }

    public static String mergeWhere(String sql, Expression dataExpression) {
        Select select = getPlainSelect(sql);
        PlainSelect plainSelect = (PlainSelect) select.getSelectBody();
        Expression plainSelectWhere = plainSelect.getWhere();
        if (FuncUtil.isNotEmpty(dataExpression)) {
            if (FuncUtil.isNotEmpty(plainSelectWhere)) {
                plainSelect.setWhere(new AndExpression(plainSelectWhere, new Parenthesis(dataExpression)));
            } else {
                plainSelect.setWhere(dataExpression);
            }
        }
        return select.toString();
    }
}
