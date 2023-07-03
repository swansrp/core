package com.bidr.authorization.mybatis.permission;

import com.bidr.authorization.constants.group.Group;
import com.bidr.authorization.dao.repository.join.AcUserGroupJoinService;
import com.bidr.authorization.holder.AccountContext;
import lombok.extern.slf4j.Slf4j;

import javax.annotation.Resource;
import java.util.List;

/**
 * Title: BaseGroupDataPermission
 * Description: Copyright: Copyright (c) 2023
 *
 * @author Sharp
 * @since 2023/05/23 09:28
 */
@Slf4j
public abstract class BaseGroupDataPermission extends BaseDataPermission {
    @Resource
    protected AcUserGroupJoinService acUserGroupJoinService;

    @Override
    protected List<String> buildPermissionList(String table, String column) {
        String customerNumber = AccountContext.getOperator();
        return acUserGroupJoinService.getCustomerNumberListFromDataScope(customerNumber, getGroupName().name());
    }

    /**
     * 指明对应group类型名
     *
     * @return group类型名
     */
    protected abstract Group getGroupName();
}
