package com.bidr.authorization.mybatis.permission;

import com.bidr.authorization.bo.account.AccountInfo;
import com.bidr.authorization.constants.group.Group;
import com.bidr.authorization.dao.repository.join.AcUserGroupJoinService;
import com.bidr.authorization.holder.AccountContext;
import com.bidr.kernel.utils.FuncUtil;
import lombok.extern.slf4j.Slf4j;
import net.sf.jsqlparser.expression.Expression;
import net.sf.jsqlparser.expression.LongValue;
import net.sf.jsqlparser.expression.operators.conditional.OrExpression;
import net.sf.jsqlparser.expression.operators.relational.ExpressionList;
import net.sf.jsqlparser.expression.operators.relational.InExpression;
import net.sf.jsqlparser.expression.operators.relational.IsNullExpression;
import net.sf.jsqlparser.expression.operators.relational.ItemsList;
import net.sf.jsqlparser.schema.Column;

import javax.annotation.Resource;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * Title: BaseGroupDataPermission
 * Description: Copyright: Copyright (c) 2023
 *
 * @author Sharp
 * @since 2023/05/23 09:28
 */
@Slf4j
public abstract class BaseGroupDataPermission implements DataPermissionInf {
    protected final Map<String, String> MAP = new ConcurrentHashMap<>();
    @Resource
    protected AcUserGroupJoinService acUserGroupJoinService;

    @Override
    public Expression expression(Map<String, String> tableAliasMap) {
        AccountInfo accountInfo = AccountContext.get();
        if (FuncUtil.isEmpty(accountInfo)) {
            log.debug("没有AccountInfo, 不进行数据权限校验");
            return null;
        }
        List<Long> permissions = buildAuthorPermission(accountInfo.getUserId());
        Expression res = null;
        for (Map.Entry<String, String> filerEntry : getFilterMap().entrySet()) {
            String table = filerEntry.getKey();
            String column = filerEntry.getValue();
            if (tableAliasMap.containsKey(table)) {
                column = getAliasColumnName(tableAliasMap, table, column);
                if (FuncUtil.isNotEmpty(permissions)) {
                    ItemsList itemsList = new ExpressionList(
                            permissions.stream().map(LongValue::new).collect(Collectors.toList()));
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
        return res;
    }

    @Override
    public Map<String, String> getFilterMap() {
        return MAP;
    }

    protected List<Long> buildAuthorPermission(Long userId) {
        return acUserGroupJoinService.getUserIdByDataScope(userId, getGroupName().name());
    }

    /**
     * 指明对应group类型名
     *
     * @return group类型名
     */
    protected abstract Group getGroupName();
}
