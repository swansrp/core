package com.bidr.authorization.service.admin;

import com.bidr.authorization.dao.entity.AcDept;
import com.bidr.authorization.dao.entity.AcUser;
import com.bidr.authorization.service.user.CreateUserService;
import com.bidr.authorization.vo.admin.UserRes;
import com.bidr.kernel.mybatis.dao.repository.SaSequenceService;
import com.bidr.kernel.utils.DbUtil;
import com.bidr.platform.service.portal.BasePortalService;
import com.github.yulichang.wrapper.MPJLambdaWrapper;
import lombok.RequiredArgsConstructor;
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
public class AdminUserServiceImpl extends BasePortalService<AcUser, UserRes> {

    private final CreateUserService createUserService;
    private final SaSequenceService sequenceService;

    @Override
    public void beforeAdd(AcUser user) {
        String customerNumber = sequenceService.getMapper().getSeq("AC_USER_CUSTOMER_NUMBER_SEQ");
        user.setCustomerNumber(customerNumber);
    }

    @Override
    public void afterAdd(AcUser user) {
        createUserService.bindDefaultRole(user);
    }

    @Override
    public MPJLambdaWrapper<AcUser> getJoinWrapper() {
        MPJLambdaWrapper<AcUser> wrapper = super.getJoinWrapper();
        wrapper.selectAs(AcDept::getName, UserRes::getDeptName);
        wrapper.leftJoin(AcDept.class, DbUtil.getTableName(AcDept.class), AcDept::getDeptId, AcUser::getDeptId);
        return wrapper;
    }
}
