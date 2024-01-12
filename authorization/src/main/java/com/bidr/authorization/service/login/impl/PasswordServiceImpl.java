package com.bidr.authorization.service.login.impl;

import com.bidr.authorization.constants.err.AccountErrCode;
import com.bidr.authorization.dao.entity.AcUser;
import com.bidr.authorization.dao.repository.AcUserService;
import com.bidr.authorization.holder.AccountContext;
import com.bidr.authorization.service.login.PasswordService;
import com.bidr.authorization.vo.login.pwd.ChangePasswordReq;
import com.bidr.authorization.vo.login.pwd.InitPasswordReq;
import com.bidr.kernel.constant.dict.common.ActiveStatusDict;
import com.bidr.kernel.utils.FuncUtil;
import com.bidr.kernel.utils.Md5Util;
import com.bidr.kernel.validate.Validator;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Date;

/**
 * Title: PasswordServiceImpl
 * Description: Copyright: Copyright (c) 2023
 *
 * @author Sharp
 * @since 2024/01/11 08:52
 */
@Service
@RequiredArgsConstructor
public class PasswordServiceImpl implements PasswordService {

    private final AcUserService acUserService;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void resetPassword(String customerNumber) {
        AcUser user = acUserService.getByCustomerNumber(customerNumber);
        Validator.assertNotNull(user, AccountErrCode.AC_USER_NOT_EXISTED);
        user.setPassword(null);
        user.setPasswordErrorTime(0);
        user.setStatus(ActiveStatusDict.ACTIVATE.getValue());
        acUserService.updateById(user, false);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void initPassword(InitPasswordReq req) {
        AcUser user = acUserService.getUserByUserName(req.getLoginId());
        if (user == null) {
            user = acUserService.getUserByPhoneNumber(req.getLoginId());
        }
        if (user == null) {
            user = acUserService.getUserByEmail(req.getLoginId());
        }
        Validator.assertNotNull(user, AccountErrCode.AC_USER_NOT_EXISTED);
        Validator.assertTrue(FuncUtil.equals(user.getStatus(), ActiveStatusDict.ACTIVATE.getValue()),
                AccountErrCode.AC_LOCK);
        Validator.assertBlank(user.getPassword(), AccountErrCode.AC_PASSWORD_IS_NOT_INITIAL);
        Validator.assertEquals(req.getPassword(), req.getPasswordConfirm(), AccountErrCode.AC_PASSWORD_CONFIRM_DIFF);
        initPassword(user, req.getPassword());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void changePassword(ChangePasswordReq req) {
        AcUser user = acUserService.getByCustomerNumber(AccountContext.getOperator());
        Validator.assertNotNull(user, AccountErrCode.AC_USER_NOT_EXISTED);
        Validator.assertTrue(FuncUtil.equals(user.getStatus(), ActiveStatusDict.ACTIVATE.getValue()),
                AccountErrCode.AC_LOCK);
        Validator.assertNotBlank(user.getPassword(), AccountErrCode.AC_PASSWORD_NOT_EXISTED);
        Validator.assertTrue(Md5Util.verify(user.getPassword(), req.getOldPassword()),
                AccountErrCode.AC_PASSWORD_OLD_NOT_RIGHT);
        Validator.assertNotEquals(req.getPassword(), req.getPasswordConfirm(), AccountErrCode.AC_PASSWORD_CONFIRM_DIFF);
        Validator.assertFalse(Md5Util.verify(user.getPassword(), req.getPassword()),
                AccountErrCode.AC_PASSWORD_OLD_NEW_SAME);
        initPassword(user, req.getPassword());
    }

    private void initPassword(AcUser user, String password) {
        user.setPasswordErrorTime(0);
        user.setPasswordLastTime(new Date());
        user.setPassword(Md5Util.generate(password));
        acUserService.updateById(user);
    }
}
