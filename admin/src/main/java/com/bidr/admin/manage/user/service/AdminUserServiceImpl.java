package com.bidr.admin.manage.user.service;

import com.bidr.admin.manage.user.vo.UserAdminRes;
import com.bidr.admin.service.common.BasePortalService;
import com.bidr.authorization.dao.entity.AcDept;
import com.bidr.authorization.dao.entity.AcUser;
import com.bidr.authorization.service.login.CustomerNumberHandler;
import com.bidr.authorization.service.user.CreateUserService;
import com.bidr.kernel.constant.CommonConst;
import com.bidr.kernel.constant.dict.common.ActiveStatusDict;
import com.bidr.kernel.mybatis.dao.repository.SaSequenceService;
import com.bidr.kernel.utils.DbUtil;
import com.bidr.kernel.utils.FuncUtil;
import com.github.yulichang.wrapper.MPJLambdaWrapper;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 * Title: AdminUserServiceImpl
 * Description: Copyright: Copyright (c) 2023
 *
 * @author Sharp
 * @since 2024/01/24 14:31
 */
@Service
@RequiredArgsConstructor
public class AdminUserServiceImpl extends BasePortalService<AcUser, UserAdminRes> {

    private final CreateUserService createUserService;
    private final SaSequenceService sequenceService;
    @Autowired(required = false)
    private CustomerNumberHandler customerNumberHandler;

    @Override
    public void beforeAdd(AcUser user) {
        String customerNumber;
        if (FuncUtil.isNotEmpty(customerNumberHandler)) {
            customerNumber = customerNumberHandler.getCustomerNumber(user);
        } else {
            customerNumber = sequenceService.getMapper().getSeq("AC_USER_CUSTOMER_NUMBER_SEQ");
        }
        user.setCustomerNumber(customerNumber);
        user.setStatus(ActiveStatusDict.ACTIVATE.getValue());
    }

    @Override
    public void afterAdd(AcUser user) {
        createUserService.bindDefaultRole(user);
    }

    @Override
    public void beforeUpdate(AcUser acUser) {
        AcUser original = getRepo().getById(acUser.getUserId());
        acUser.setPassword(original.getPassword());
        super.beforeUpdate(acUser);
    }

    @Override
    public void getJoinWrapper(MPJLambdaWrapper<AcUser> wrapper) {
        super.getJoinWrapper(wrapper);
        wrapper.leftJoin(AcDept.class, DbUtil.getTableName(AcDept.class), AcDept::getDeptId, AcUser::getDeptId);
        wrapper.eq(AcUser::getValid, CommonConst.YES);
    }
}
