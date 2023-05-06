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
 * @date 2023/05/02 17:31
 */
@Slf4j
public class SqlParseUtil {

    public static Map<String, String> buildTableAliasMap(String sql) {
        PlainSelect plainSelect = getPlainSelect(sql);
        Map<String, String> mapTable = new HashMap<>();
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
        return mapTable;
    }

    private static PlainSelect getPlainSelect(String sql) {
        Select select = null;
        try {
            select = (Select) CCJSqlParserUtil.parse(sql, ccjSqlParser -> ccjSqlParser.withSquareBracketQuotation(true));
        } catch (JSQLParserException e) {
            log.error(sql);
            Validator.assertException(ErrCodeSys.PA_PARAM_FORMAT, "SQL");
        }
        return (PlainSelect) select.getSelectBody();
    }

    public static String mergeWhere(String sql, Expression newExpression) {
        PlainSelect plainSelect = getPlainSelect(sql);
        Expression plainSelectWhere = plainSelect.getWhere();
        if (FuncUtil.isNotEmpty(newExpression)) {
            if (FuncUtil.isNotEmpty(plainSelectWhere)) {
                plainSelect.setWhere(new AndExpression(plainSelectWhere, new Parenthesis(newExpression)));
            } else {
                plainSelect.setWhere(newExpression);
            }
        }
        return plainSelect.toString();
    }
}
