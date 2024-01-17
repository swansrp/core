package com.bidr.authorization.controller.admin;

import com.bidr.authorization.dao.entity.AcUser;
import com.bidr.authorization.service.user.CreateUserService;
import com.bidr.authorization.vo.admin.UserRes;
import com.bidr.kernel.config.response.Resp;
import com.bidr.kernel.constant.dict.common.ActiveStatusDict;
import com.bidr.kernel.controller.BaseAdminController;
import com.bidr.kernel.mybatis.dao.mapper.SaSequenceDao;
import com.bidr.kernel.mybatis.dao.repository.SaSequenceService;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;

/**
 * Title: AdminUserController
 * Description: Copyright: Copyright (c) 2023
 *
 * @author Sharp
 * @since 2023/12/29 10:31
 */
@Api(tags = "系统管理 - 用户管理")
@RestController
@RequiredArgsConstructor
@RequestMapping(path = {"/web/portal/user"})
public class AdminUserController extends BaseAdminController<AcUser, UserRes> {

    private final CreateUserService createUserService;
    private final SaSequenceService sequenceService;

    @Override
    protected void beforeAdd(AcUser user) {
        String customerNumber = sequenceService.getMapper().getSeq("AC_USER_CUSTOMER_NUMBER_SEQ");
        user.setCustomerNumber(customerNumber);
    }

    @Override
    protected void afterAdd(AcUser user) {
        createUserService.bindDefaultRole(user);
    }

    @ApiOperation(value = "禁用某用户")
    @RequestMapping(value = "/disable", method = RequestMethod.POST)
    public void disableUser(String customerNumber) {
        String name = createUserService.changeAccountStatus(customerNumber, ActiveStatusDict.DEACTIVATE.getValue());
        Resp.notice("用户[" + name + "]已禁用");
    }

    @ApiOperation(value = "启用某用户")
    @RequestMapping(value = "/enable", method = RequestMethod.POST)
    public void enableUser(String customerNumber) {
        String name = createUserService.changeAccountStatus(customerNumber, ActiveStatusDict.ACTIVATE.getValue());
        Resp.notice("用户[" + name + "]已启用");
    }


}
