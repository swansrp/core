package com.bidr.authorization.service.login.impl;

import com.bidr.authorization.bo.token.TokenInfo;
import com.bidr.authorization.constants.err.AccountErrCode;
import com.bidr.authorization.constants.param.AccountParam;
import com.bidr.authorization.constants.token.ChangePasswordTokenItemConst;
import com.bidr.authorization.dao.entity.AcUser;
import com.bidr.authorization.dao.repository.AcUserService;
import com.bidr.authorization.holder.AccountContext;
import com.bidr.authorization.service.login.PasswordService;
import com.bidr.authorization.service.token.TokenService;
import com.bidr.authorization.vo.login.pwd.ChangePasswordReq;
import com.bidr.authorization.vo.login.pwd.InitPasswordReq;
import com.bidr.email.service.EmailService;
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
import java.util.Map;

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
    private final TokenService tokenService;
    private final SysConfigCacheService sysConfigCacheService;
    private final EmailService emailService;

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
        if (FuncUtil.isNotEmpty(user.getPassword())) {
            Validator.assertTrue(Md5Util.verify(req.getOldPassword(), user.getPassword()),
                    AccountErrCode.AC_PASSWORD_OLD_NOT_RIGHT);
            Validator.assertFalse(Md5Util.verify(req.getPassword(), user.getPassword()),
                    AccountErrCode.AC_PASSWORD_OLD_NEW_SAME);
        }
        Validator.assertEquals(req.getPassword(), req.getPasswordConfirm(), AccountErrCode.AC_PASSWORD_CONFIRM_DIFF);
        initPassword(user, req.getPassword());
    }

    @Override
    public void sendChangePasswordEmail(String userId, String email) {
        TokenInfo token = tokenService.buildEmailToken(userId);
        fillChangePasswordToken(userId, email, token);
        sendEmail(userId, email, token);
    }

    @Override
    public void changePasswordByMsgCode(String phoneNumber, String password) {
        AcUser user = acUserService.getUserByPhoneNumber(phoneNumber);
        Validator.assertNotNull(user, ErrCodeSys.PA_DATA_NOT_EXIST, "用户");
        Validator.assertTrue(StringUtils.equals(user.getPhoneNumber(), phoneNumber), ErrCodeSys.PA_DATA_DIFF,
                "登录用户手机号码");
        changePassword(user, password);
    }

    @Override
    public void emailChangeReq(String userId, String email) {
        AcUser user = acUserService.getByCustomerNumber(userId);
        Validator.assertNotNull(user, ErrCodeSys.PA_DATA_NOT_EXIST, "用户");
        Validator.assertTrue(StringUtils.equals(user.getEmail(), email), ErrCodeSys.PA_DATA_DIFF, "登录用户邮箱");
        sendChangePasswordEmail(userId, email);
    }

    private void fillChangePasswordToken(String userId, String email, TokenInfo token) {
        AcUser user = acUserService.getByCustomerNumber(userId);
        Map<String, Object> map = tokenService.getTokenValue(token);
        map.put(ChangePasswordTokenItemConst.USER_ID.name(), userId);
        map.put(ChangePasswordTokenItemConst.EMAIL.name(), email);
        map.put(ChangePasswordTokenItemConst.INIT.name(), user == null);
        tokenService.setTokenValue(token, map);
    }

    private void sendEmail(String id, String email, TokenInfo token) {
        String title = sysConfigCacheService.getParamValueAvail(AccountParam.EMAIL_SET_PWD_TITLE);
        String textFormat = sysConfigCacheService.getParamValueAvail(AccountParam.EMAIL_SET_PWD_TEXT_FORMAT);
        String confirmUrlFormat = sysConfigCacheService.getParamValueAvail(AccountParam.EMAIL_SET_PWD_CONFIRM_URL);
        String url = String.format(confirmUrlFormat, id, token.getToken());
        String text = String.format(textFormat, url);
        emailService.sendEmail(email, title, text);
    }

    @Override
    public void changePassword(String userId, String password) {
        AcUser user = acUserService.getByCustomerNumber(userId);
        changePassword(user, password);
    }

    private void changePassword(AcUser user, String password) {
        if (StringUtils.isNotBlank(user.getPassword())) {
            if (Md5Util.verify(password, user.getPassword())) {
                Validator.assertException(ErrCodeSys.SYS_ERR_MSG, "新老密码相同");
            }
        }
        user.setPassword(Md5Util.generate(password));
        user.setPasswordErrorTime(0);
        user.setStatus(ActiveStatusDict.ACTIVATE.getValue());
        user.setPasswordLastTime(new Date());
        acUserService.updateById(user);
    }

    private void initPassword(AcUser user, String password) {
        user.setPasswordErrorTime(0);
        user.setPasswordLastTime(new Date());
        user.setPassword(Md5Util.generate(password));
        acUserService.updateById(user);
    }
}
