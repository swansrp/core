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
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 改密越界闸的**凭据判据**守卫（#47）。
 *
 * <p>判据＝`ownAccount || 目标账号已有密码`，即「有无可验凭据」而不是「有无登录态」：</p>
 * <ul>
 *   <li>有密码账号：下方 oldPassword 强校验即身份证明 ⇒ 无会话也能改（登录前的密码过期强制改密
 *       链路，token 为 GUEST）；他人改同样需要知道旧密码＝持有凭据。</li>
 *   <li>无密码账号（飞书 SSO 建号）：没有可验凭据 ⇒ **只有本人登录态可设**，否则任何知道工号的人
 *       都能代设密码、再用密码登录绕过 SSO 接管账号。</li>
 * </ul>
 *
 * <p>🔴 反面教材（2026-11-04 曾短暂采用并被本组用例之一钉死）：判据写成「operator 非空才比对」
 * 会在无会话时整段跳过归属校验，而 oldPassword 校验只对有密码账号生效 ⇒ 匿名可给无密码账号
 * 直接设密＝账号接管。本组第 2 条腿专门防这个回退。</p>
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
        AccountContext.remove(); // 默认无会话（operator＝空串）
    }

    @After
    public void clearContext() {
        AccountContext.remove();
    }

    /* ---------------- 夹具 ---------------- */

    private AcUser user(String userName, String customerNumber, String plainPassword) {
        AcUser u = new AcUser();
        u.setUserId(3L);
        u.setUserName(userName);
        u.setCustomerNumber(customerNumber);
        u.setStatus(1);
        // 存储态＝双 md5 盐混排（与登录链同口径）：generate(md5(明文))
        if (plainPassword != null) {
            u.setPassword(Md5Util.generate(Md5Util.MD5(plainPassword)));
        }
        return u;
    }

    private void loginAs(String customerNumber) {
        AccountInfo info = new AccountInfo();
        info.setCustomerNumber(customerNumber);
        AccountContext.set(info);
    }

    private ChangePasswordReq req(String loginId, String oldPlain, String newPlain) {
        ChangePasswordReq r = new ChangePasswordReq();
        r.setLoginId(loginId);
        // 无密码账号场景没有旧密码可给（也无需给：oldPassword 校验整段对无密码账号跳过）
        if (oldPlain != null) {
            r.setOldPassword(Md5Util.MD5(oldPlain));
        }
        r.setPassword(Md5Util.MD5(newPlain));
        r.setPasswordConfirm(Md5Util.MD5(newPlain));
        return r;
    }

    /* ---------------- 腿 1：过期改密（无会话 + 有密码）必须放行 ---------------- */

    @Test
    public void expiredChangeWithoutSessionWithExistingPasswordPasses() {
        when(acUserService.getUserByUserName("zhang_p")).thenReturn(user("zhang_p", "000003", "Test@1234"));
        // 无会话（登录页过期改密），凭据＝旧密码
        service.changePassword(req("zhang_p", "Test@1234", "Zx7m@Run4"));
        verify(acUserService).updateById(any(AcUser.class));
    }

    /* ---------------- 腿 2：无密码账号 + 无会话 = 账号接管，必须拒 ---------------- */

    @Test
    public void anonymousChangeOnPasswordlessAccountRejected() {
        // 飞书 SSO 建号（无密码）；攻击者只知道工号
        when(acUserService.getUserByUserName("feishu_user")).thenReturn(user("feishu_user", "000099", null));

        ServiceException ex = Assert.assertThrows(ServiceException.class,
                () -> service.changePassword(req("feishu_user", null, "Hacked@123")));

        Assert.assertSame("无密码账号没有可验凭据，匿名设密＝接管，必须拒",
                AccountErrCode.AC_PASSWORD_OWNER_MISMATCH, ex.getErrCode());
        verify(acUserService, never()).updateById(any(AcUser.class));
    }

    /* ---------------- 腿 3：本人会话 + 无密码账号 → 允许首设 ---------------- */

    @Test
    public void ownSessionCanSetPasswordOnPasswordlessAccount() {
        when(acUserService.getUserByUserName("feishu_user")).thenReturn(user("feishu_user", "000099", null));
        loginAs("000099");
        service.changePassword(req("feishu_user", null, "First@123"));
        verify(acUserService).updateById(any(AcUser.class));
    }

    /* ---------------- 腿 4：本人 + 旧密码错 → 拒 ---------------- */

    @Test
    public void ownerWithWrongOldPasswordRejected() {
        when(acUserService.getUserByUserName("zhang_p")).thenReturn(user("zhang_p", "000003", "Test@1234"));
        loginAs("000003");

        ServiceException ex = Assert.assertThrows(ServiceException.class,
                () -> service.changePassword(req("zhang_p", "WrongOld@1", "Zx7m@Run4")));

        Assert.assertSame("有密码账号必须过旧密码强校验", AccountErrCode.AC_PASSWORD_OLD_NOT_RIGHT, ex.getErrCode());
        verify(acUserService, never()).updateById(any(AcUser.class));
    }

    /* ---------------- 腿 5：他人 + 持有旧密码 → 放行（钉住"按凭据判"的刻意放宽） ---------------- */

    @Test
    public void otherUserWithOldPasswordPassesByCredentialRule() {
        // 刻意放宽：知道旧密码 ≡ 持有凭据（他能直接登录该账号自己改）。此人已登录 A 改 B 无危害，
        // 但"按登录态收紧"会重新打死过期改密链路（#47 首次踩坑）——本腿钉住不许收回去。
        when(acUserService.getUserByUserName("zhang_p")).thenReturn(user("zhang_p", "000003", "Test@1234"));
        loginAs("000001");
        service.changePassword(req("zhang_p", "Test@1234", "Zx7m@Run4"));
        verify(acUserService).updateById(any(AcUser.class));
    }
}