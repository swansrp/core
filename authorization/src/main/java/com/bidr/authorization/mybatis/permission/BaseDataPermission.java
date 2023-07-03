package com.bidr.authorization.mybatis.permission;

import com.bidr.authorization.bo.account.AccountInfo;
import com.bidr.authorization.holder.AccountContext;
import com.bidr.kernel.utils.FuncUtil;
import lombok.extern.slf4j.Slf4j;
import net.sf.jsqlparser.expression.*;
import net.sf.jsqlparser.expression.operators.conditional.OrExpression;
import net.sf.jsqlparser.expression.operators.relational.ExpressionList;
import net.sf.jsqlparser.expression.operators.relational.InExpression;
import net.sf.jsqlparser.expression.operators.relational.IsNullExpression;
import net.sf.jsqlparser.expression.operators.relational.ItemsList;
import net.sf.jsqlparser.schema.Column;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * Title: BaseDataPermission
 * Description: Copyright: Copyright (c) 2023
 *
 * @author Sharp
 * @since 2023/06/26 09:39
 */
@Slf4j
public abstract class BaseDataPermission implements DataPermissionInf {
    protected final Map<String, List<String>> MAP = new ConcurrentHashMap<>();

    @Override
    public Expression expression(Map<String, String> tableAliasMap) {
        AccountInfo accountInfo = AccountContext.get();
        if (FuncUtil.isEmpty(accountInfo)) {
            log.debug("没有AccountInfo, 不进行数据权限校验");
            return null;
        }

        Expression res = null;
        for (Map.Entry<String, List<String>> filerEntry : getFilterMap().entrySet()) {
            String table = filerEntry.getKey();
            if (tableAliasMap.containsKey(table)) {
                List<String> columns = filerEntry.getValue();
                for (String column : columns) {
                    // 同一表中 不同字段的权限 OR 关系连接
                    List<String> permissions = buildPermissionList(table, column);
                    column = getAliasColumnName(tableAliasMap, table, column);
                    if (FuncUtil.isNotEmpty(permissions)) {
                        ItemsList itemsList = getItemList(permissions);
                        if (FuncUtil.isEmpty(res)) {
                            res = new InExpression(new Column(column), itemsList);
                        } else {
                            res = new OrExpression(res, new InExpression(new Column(column), itemsList));
                        }
                    } else {
                        if (FuncUtil.isEmpty(res)) {
                            res = new IsNullExpression().withLeftExpression(new Column(column));
                        } else {
                            res = new OrExpression(res, new IsNullExpression().withLeftExpression(new Column(column)));
                        }
                    }
                }
            }
        }
        return res;
    }

    @Override
    public Map<String, List<String>> getFilterMap() {
        return MAP;
    }

    /**
     * 构造权限过滤条件数据
     * @param table 表名
     * @param column 列名
     *
     * @return 权限过滤条件数据
     */
    protected abstract List<String> buildPermissionList(String table, String column);

    protected ExpressionList getItemList(List<String> permissions) {
        switch (getPermissionType()) {
            case LONG:
                return new ExpressionList(permissions.stream().map(LongValue::new).collect(Collectors.toList()));
            case DOUBLE:
                return new ExpressionList(permissions.stream().map(DoubleValue::new).collect(Collectors.toList()));
            case DATE:
                return new ExpressionList(permissions.stream().map(DateValue::new).collect(Collectors.toList()));
            default:
                return new ExpressionList(permissions.stream().map(StringValue::new).collect(Collectors.toList()));
        }
    }

    /**
     * 构造权限过滤条件数据
     *
     * @return 权限过滤条件数据
     */
    protected abstract DataPermissionFilerType getPermissionType();


}
