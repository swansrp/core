package com.bidr.authorization.service.login.impl;

import com.bidr.authorization.bo.account.AccountInfo;
import com.bidr.authorization.constants.err.AccountErrCode;
import com.bidr.authorization.dao.entity.AcUser;
import com.bidr.authorization.dao.repository.AcUserService;
import com.bidr.authorization.holder.AccountContext;
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

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 改密归属校验的会话态分界（#47，6.60 真机 zhang_p 实证）。
 *
 * <p>钉两件事：①未登录（登录页过期改密/首次设密，operator 为空串）传 loginId 必须放行——
 * 归属证明是旧密码验签＋验证码，不是会话；原判据不区分有无会话，把过期改密整条路堵死成
 * 「不能修改他人密码」；②已登录态传他人 loginId 仍恒拒（防会话内横向改密）。</p>
 *
 * @author sharp
 * @since 2026-11-04
 */
public class PasswordOwnerMismatchTest {

    private AcUserService acUserService;
    private PasswordServiceImpl service;

    @Before
    public void setUp() {
        acUserService = mock(AcUserService.class);
        service = new PasswordServiceImpl(
                acUserService,
                mock(TokenService.class),
                mock(SysConfigCacheService.class),
                mock(EmailService.class));
        AccountContext.remove();
    }

    @After
    public void clearContext() {
        AccountContext.remove();
    }

    private AcUser zhangP() {
        AcUser user = new AcUser();
        user.setUserId(3L);
        user.setUserName("zhang_p");
        user.setCustomerNumber("000003");
        user.setStatus(1);
        // 存储态＝双 md5 盐混排（与登录链同口径）：generate(md5(明文))
        user.setPassword(Md5Util.generate(Md5Util.MD5("Test@1234")));
        return user;
    }

    private ChangePasswordReq req() {
        ChangePasswordReq req = new ChangePasswordReq();
        req.setLoginId("zhang_p");
        req.setOldPassword(Md5Util.MD5("Test@1234"));
        req.setPassword(Md5Util.MD5("Zx7m@Run4"));
        req.setPasswordConfirm(Md5Util.MD5("Zx7m@Run4"));
        return req;
    }

    @Test
    public void expiredChangeWithoutSessionMustPassOwnership() {
        // #47 回归针：无会话（operator=空串）＋loginId —— 不得抛 AC_PASSWORD_OWNER_MISMATCH，
        // 应走到旧密码验签（正确旧密码）并落库新口令。
        when(acUserService.getUserByUserName("zhang_p")).thenReturn(zhangP());
        service.changePassword(req());
        verify(acUserService).updateById(any(AcUser.class));
    }

    @Test
    public void loggedInOwnerMismatchStillRejected() {
        // 已登录（operator=000001）传他人 loginId —— 归属校验必须仍然恒拒。
        AccountInfo info = new AccountInfo();
        info.setCustomerNumber("000001");
        AccountContext.set(info);
        when(acUserService.getUserByUserName("zhang_p")).thenReturn(zhangP());
        ServiceException ex = Assert.assertThrows(ServiceException.class, () -> service.changePassword(req()));
        Assert.assertSame("已登录改他人密码必须恒拒", AccountErrCode.AC_PASSWORD_OWNER_MISMATCH, ex.getErrCode());
    }
}
