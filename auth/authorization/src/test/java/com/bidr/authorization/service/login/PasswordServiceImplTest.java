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
 * Description: changePassword 越界闸口径（纯 Mockito，离线）。
 * <p>口径＝「有无可验凭据」而非登录态：
 * <ul>
 *   <li>目标已有密码：下方 oldPassword 强校验即身份证明——密码过期强制改密（GUEST token）靠它放行，
 *       他人改也必须知道旧密码；</li>
 *   <li>目标无密码（飞书 SSO 建号，passwordLastTime=0 标记）：没有可验凭据，只有本人登录态能设，
 *       防「知道工号即可代设密码后用密码登录绕过 SSO」；SSO 首次设密码走 /login/password/init。</li>
 * </ul>
 * 六腿：GUEST+有密码放行 / GUEST+旧密码错拒 / GUEST+无密码拒 / 登录态跨人+有密码被旧密码拦 /
 * 登录态跨人+无密码拒 / 登录态本人（有密码与无密码）放行。</p>
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

    private AcUser givenUser(String customerNumber, boolean hasPassword) {
        AcUser user = new AcUser();
        user.setCustomerNumber(customerNumber);
        user.setUserName(customerNumber);
        user.setStatus(1);
        if (hasPassword) {
            user.setPassword(Md5Util.generate(OLD_RAW_MD5));
            user.setPasswordLastTime(new java.util.Date());
        } else {
            // 飞书 SSO 建号形态：密码空 + passwordLastTime=0（"尚未设过密码"标记）
            user.setPassword(Md5Util.generate(null));
            user.setPasswordLastTime(new java.util.Date(0L));
        }
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
    public void guestWithPasswordTargetAllowed() {
        // 密码过期强制改密：GUEST token + loginId=自己 + 旧密码正确
        givenGuest();
        AcUser user = givenUser("u1", true);

        service.changePassword(req("u1", OLD_RAW_MD5, NEW_RAW_MD5));

        Mockito.verify(acUserService).updateById(Mockito.any(AcUser.class));
        Assert.assertNotNull(user.getPasswordLastTime());
    }

    @Test
    public void guestWrongOldPasswordRejected() {
        givenGuest();
        givenUser("u1", true);

        try {
            service.changePassword(req("u1", "00000000000000000000000000000000", NEW_RAW_MD5));
            Assert.fail("旧密码错误必须拒绝");
        } catch (ServiceException e) {
            Assert.assertEquals(AccountErrCode.AC_PASSWORD_OLD_NOT_RIGHT, e.getErrCode());
        }
        Mockito.verify(acUserService, Mockito.never()).updateById(Mockito.any(AcUser.class));
    }

    @Test
    public void guestPasswordlessSsoTargetRejected() {
        // 无密码 SSO 账号不能被 GUEST 代设密码（否则可密码登录绕过 SSO）
        givenGuest();
        givenUser("sso-user", false);

        try {
            service.changePassword(req("sso-user", OLD_RAW_MD5, NEW_RAW_MD5));
            Assert.fail("无密码账号的 GUEST 改密必须拒绝");
        } catch (ServiceException e) {
            Assert.assertEquals(AccountErrCode.AC_PASSWORD_OWNER_MISMATCH, e.getErrCode());
        }
        Mockito.verify(acUserService, Mockito.never()).updateById(Mockito.any(AcUser.class));
    }

    @Test
    public void loginStateCrossUserWithPasswordStillNeedsOldPassword() {
        // 有密码账号：闸放行但旧密码校验拦住（等同需要持有对方凭据）
        givenLogin("opA");
        givenUser("opB", true);

        try {
            service.changePassword(req("opB", "00000000000000000000000000000000", NEW_RAW_MD5));
            Assert.fail("登录态跨人改密（旧密码不匹配）必须拒绝");
        } catch (ServiceException e) {
            Assert.assertEquals(AccountErrCode.AC_PASSWORD_OLD_NOT_RIGHT, e.getErrCode());
        }
        Mockito.verify(acUserService, Mockito.never()).updateById(Mockito.any(AcUser.class));
    }

    @Test
    public void loginStateCrossUserPasswordlessRejected() {
        givenLogin("opA");
        givenUser("sso-user", false);

        try {
            service.changePassword(req("sso-user", OLD_RAW_MD5, NEW_RAW_MD5));
            Assert.fail("登录态跨人改无密码账号必须拒绝");
        } catch (ServiceException e) {
            Assert.assertEquals(AccountErrCode.AC_PASSWORD_OWNER_MISMATCH, e.getErrCode());
        }
        Mockito.verify(acUserService, Mockito.never()).updateById(Mockito.any(AcUser.class));
    }

    @Test
    public void loginStateSelfChangeAllowed() {
        givenLogin("opA");
        givenUser("opA", true);

        service.changePassword(req("opA", OLD_RAW_MD5, NEW_RAW_MD5));

        Mockito.verify(acUserService).updateById(Mockito.any(AcUser.class));
    }

    @Test
    public void loginStateSelfPasswordlessAllowed() {
        // SSO 本人登录后设置密码（个人中心场景）
        givenLogin("sso-user");
        givenUser("sso-user", false);

        service.changePassword(req("sso-user", OLD_RAW_MD5, NEW_RAW_MD5));

        Mockito.verify(acUserService).updateById(Mockito.any(AcUser.class));
    }
}