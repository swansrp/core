package com.bidr.authorization.service.user.impl;

import com.bidr.authorization.constants.dict.DataPermitScopeDict;
import com.bidr.authorization.constants.dict.UserTypeDict;
import com.bidr.authorization.constants.err.AccountErrCode;
import com.bidr.authorization.constants.param.AccountParam;
import com.bidr.authorization.dao.entity.AcAccount;
import com.bidr.authorization.dao.entity.AcUser;
import com.bidr.authorization.dao.entity.AcUserDept;
import com.bidr.authorization.dao.repository.AcUserDeptService;
import com.bidr.authorization.dao.repository.AcUserService;
import com.bidr.authorization.service.login.CustomerNumberHandler;
import com.bidr.authorization.service.login.RoleBindService;
import com.bidr.authorization.service.user.CreateUserService;
import com.bidr.kernel.constant.CommonConst;
import com.bidr.kernel.constant.dict.common.ActiveStatusDict;
import com.bidr.kernel.constant.err.ErrCodeSys;
import com.bidr.kernel.utils.FuncUtil;
import com.bidr.kernel.utils.Md5Util;
import com.bidr.kernel.validate.Validator;
import com.bidr.platform.service.cache.SysConfigCacheService;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Date;

/**
 * Title: CreateUserServiceImpl
 * Description: Copyright: Copyright (c) 2023 Company: Sharp Ltd.
 *
 * @author Sharp
 * @since 2023/04/27 16:19
 */
@Service
@RequiredArgsConstructor
public class CreateUserServiceImpl implements CreateUserService {

    private final AcUserService acUserService;
    private final SysConfigCacheService sysConfigCacheService;
    private final RoleBindService roleBindService;
    private final AcUserDeptService acUserDeptService;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public AcUser createUserWithName(String phoneNumber, String name) {
        return createUser(phoneNumber, null, name, phoneNumber, null, null);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public AcUser createUser(String userId, String password) {
        return createUser(userId, password, userId, null, null, null);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public AcUser createUser(String loginId, String password, String name, String phoneNumber, String email, String avatar) {
        AcUser user = buildBaseUser();
        user.setUserName(loginId);
        user.setPhoneNumber(phoneNumber);
        user.setPassword(Md5Util.generate(password));
        user.setNickName(name);
        user.setEmail(email);
        user.setAvatar(avatar);
        acUserService.insert(user);
        bindDefaultRole(user);
        return user;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public String changeAccountStatus(String customerNumber, Integer status) {
        AcUser user = acUserService.getByCustomerNumber(customerNumber);
        Validator.assertNotNull(user, AccountErrCode.AC_USER_NOT_EXISTED);
        user.setStatus(status);
        acUserService.updateById(user);
        return user.getName();
    }

    @Override
    public AcUser createUserFromWechat(String unionId, String nickName, String phoneNumber, String avatar) {
        AcUser user = buildBaseUser();
        user.setUserName(unionId);
        if (FuncUtil.isNotEmpty(phoneNumber)) {
            user.setPhoneNumber(phoneNumber);
        }
        if (FuncUtil.isNotEmpty(nickName)) {
            user.setNickName(nickName);
        }
        if (FuncUtil.isNotEmpty(avatar)) {
            user.setAvatar(avatar);
        }
        acUserService.insert(user);
        bindDefaultRole(user);
        return user;
    }

    @Override
    public AcUser mergeWechatPhoneNumber(String unionId, String nickName, String phoneNumber, String avatar) {
        AcUser user = acUserService.getUserByPhoneNumber(phoneNumber);
        Validator.assertBlank(user.getWechatId(), ErrCodeSys.SYS_ERR_MSG, "手机号账户已绑定微信");
        user.setWechatId(unionId);
        if (StringUtils.isBlank(user.getName())) {
            user.setName(nickName);
        }
        acUserService.deleteByUserName(unionId);
        acUserService.updateById(user);
        return user;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public AcUser createUser(AcAccount account) {
        AcUser user = buildBaseUser();
        mergeAccount(user, account);
        acUserService.insert(user);
        bindDefaultRole(user);
        bindDept(account, user);
        return user;
    }

    private void mergeAccount(AcUser acUser, AcAccount account) {
        acUser.setDeptId(account.getDepartment());
        acUser.setUserName(account.getUserName());
        acUser.setCustomerNumber(account.getId());
        acUser.setName(account.getName());
        acUser.setPhoneNumber(account.getMobile());
        acUser.setEmail(account.getEmail());
        acUser.setSex(account.getGender());
        acUser.setAvatar(account.getPictureLink());
        acUser.setUserType(UserTypeDict.ACCOUNT.getValue());
    }

    private void bindDept(AcAccount account, AcUser user) {
        AcUserDept acUserDept = new AcUserDept();
        acUserDept.setUserId(user.getUserId());
        acUserDept.setDeptId(account.getDepartment());
        AcUserDept original = acUserDeptService.selectByMultiId(acUserDept);
        if (FuncUtil.isEmpty(original)) {
            acUserDept.setDataScope(DataPermitScopeDict.OWNER.getValue());
        } else {
            acUserDept.setDataScope(original.getDataScope());
        }
        acUserDeptService.deleteByMultiId(acUserDept);
        acUserDeptService.insert(acUserDept);
    }

    @Override
    public void bindDefaultRole(AcUser user) {
        Long defaultRoleId = sysConfigCacheService.getParamLong(AccountParam.ACCOUNT_DEFAULT_ROLE_ID);
        roleBindService.bindRole(user.getUserId(), defaultRoleId);
    }

    private AcUser buildBaseUser() {
        AcUser user = new AcUser();
        user.setStatus(ActiveStatusDict.ACTIVATE.getValue());
        user.setPasswordErrorTime(0);
        user.setPasswordLastTime(new Date());
        user.setValid(CommonConst.YES);
        return user;
    }
}
