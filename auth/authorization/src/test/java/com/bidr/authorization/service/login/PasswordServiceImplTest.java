package com.bidr.authorization.service.login;

import com.bidr.authorization.bo.account.AccountInfo;
import com.bidr.authorization.constants.err.AccountErrCode;
import com.bidr.authorization.dao.entity.AcUser;
import com.bidr.authorization.dao.repository.AcUserService;
import com.bidr.authorization.holder.AccountContext;
import com.bidr.authorization.service.login.impl.PasswordServiceImpl;
import com.bidr.authorization.service.token.TokenService;
import com.bidr.authorization.vo.login.pwd.ChangePasswordReq;
import com.bidr.email.service.EmailService;
import com.bidr.kernel.exception.ServiceException;
import com.bidr.kernel.utils.Md5Util;
import com.bidr.platform.service.cache.SysConfigCacheService;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;

/**
 * Title: PasswordServiceImplTest
 * Description: changePassword 越界闸与密码过期强制改密（GUEST）放行腿（纯 Mockito，离线）。
 * <p>背景：2026-10-16 安全收口给带 loginId 的改密加了 owner 闸（operator==customerNumber），
 * 把"密码过期强制改密"（未登录 GUEST token + loginId=自己）一并拦死成死循环。修复口径：
 * GUEST（isLoginToken=false）跳过 owner 闸，本人由 oldPassword 强校验兜住（有密码必验）；
 * 登录态维持原闸防越权。四腿：GUEST 自改放行 / GUEST 旧密码错仍拒 / 登录态越权拒 / 登录态自改放行。</p>
 * Copyright: Copyright (c) 2026 Company: Bidr Ltd.
 *
 * @author Sharp
 * @since 2026/11/04
 */
public class PasswordServiceImplTest {

    private static final String OLD_RAW_MD5 = "5f4dcc3b5aa765d61d8327deb882cf99";
    private static final String NEW_RAW_MD5 = "6ba2cea6b5bb6b9863ead5604dd2492a";

    private AcUserService acUserService;
    private TokenService tokenService;
    private PasswordServiceImpl service;

    @Before
    public void setUp() {
        acUserService = Mockito.mock(AcUserService.class);
        tokenService = Mockito.mock(TokenService.class);
        service = new PasswordServiceImpl(acUserService, tokenService,
                Mockito.mock(SysConfigCacheService.class), Mockito.mock(EmailService.class));
        Mockito.when(acUserService.updateById(Mockito.any(AcUser.class))).thenReturn(true);
    }

    @After
    public void tearDown() {
        AccountContext.remove();
    }

    private AcUser givenUser(String customerNumber) {
        AcUser user = new AcUser();
        user.setCustomerNumber(customerNumber);
        user.setUserName(customerNumber);
        user.setStatus(1);
        user.setPassword(Md5Util.generate(OLD_RAW_MD5));
        Mockito.when(acUserService.getUserByUserName(customerNumber)).thenReturn(user);
        return user;
    }

    private void givenGuest() {
        Mockito.when(tokenService.isLoginToken()).thenReturn(false);
        AccountInfo info = new AccountInfo();
        info.setCustomerNumber("GUEST");
        AccountContext.set(info);
    }

    private void givenLogin(String customerNumber) {
        Mockito.when(tokenService.isLoginToken()).thenReturn(true);
        AccountInfo info = new AccountInfo();
        info.setCustomerNumber(customerNumber);
        AccountContext.set(info);
    }

    private ChangePasswordReq req(String loginId, String oldMd5, String newMd5) {
        ChangePasswordReq req = new ChangePasswordReq();
        req.setLoginId(loginId);
        req.setOldPassword(oldMd5);
        req.setPassword(newMd5);
        req.setPasswordConfirm(newMd5);
        return req;
    }

    @Test
    public void guestSelfChangeAllowed() {
        givenGuest();
        AcUser user = givenUser("u1");

        service.changePassword(req("u1", OLD_RAW_MD5, NEW_RAW_MD5));

        Mockito.verify(acUserService).updateById(Mockito.any(AcUser.class));
        // initPassword 腿：密码时间刷新（过期闸解除）
        Assert.assertNotNull(user.getPasswordLastTime());
    }

    @Test
    public void guestWrongOldPasswordStillRejected() {
        givenGuest();
        givenUser("u1");

        try {
            service.changePassword(req("u1", "00000000000000000000000000000000", NEW_RAW_MD5));
            Assert.fail("旧密码错误必须拒绝");
        } catch (ServiceException e) {
            Assert.assertEquals(AccountErrCode.AC_PASSWORD_OLD_NOT_RIGHT, e.getErrCode());
        }
        Mockito.verify(acUserService, Mockito.never()).updateById(Mockito.any(AcUser.class));
    }

    @Test
    public void loginStateCrossUserStillRejected() {
        givenLogin("opA");
        givenUser("opB");

        try {
            service.changePassword(req("opB", OLD_RAW_MD5, NEW_RAW_MD5));
            Assert.fail("登录态越权改他人密码必须拒绝");
        } catch (ServiceException e) {
            Assert.assertEquals(AccountErrCode.AC_PASSWORD_OWNER_MISMATCH, e.getErrCode());
        }
        Mockito.verify(acUserService, Mockito.never()).updateById(Mockito.any(AcUser.class));
    }

    @Test
    public void loginStateSelfChangeAllowed() {
        givenLogin("opA");
        givenUser("opA");

        service.changePassword(req("opA", OLD_RAW_MD5, NEW_RAW_MD5));

        Mockito.verify(acUserService).updateById(Mockito.any(AcUser.class));
    }
}
