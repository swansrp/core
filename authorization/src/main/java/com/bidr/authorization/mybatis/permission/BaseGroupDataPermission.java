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
public abstract class BaseGroupDataPermission extends BaseCreateByDataPermission {
    @Resource
    protected AcUserGroupJoinService acUserGroupJoinService;

    @Override
    protected List<String> buildAuthorPermission(String customerNumber) {
        return acUserGroupJoinService.getCustomerNumberListFromDataScope(customerNumber, getGroupName().name());
    }

    /**
     * 指明对应group类型名
     *
     * @return group类型名
     */
    protected abstract Group getGroupName();
}
