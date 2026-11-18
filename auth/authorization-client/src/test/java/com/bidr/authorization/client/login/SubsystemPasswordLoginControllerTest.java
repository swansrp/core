package com.bidr.authorization.client.login;

import com.bidr.authorization.annotation.auth.Auth;
import com.bidr.authorization.annotation.auth.AuthToken;
import com.bidr.authorization.annotation.captcha.CaptchaVerify;
import com.bidr.authorization.client.controller.RetiredCredentialController;
import com.bidr.authorization.client.oauth2.IdpPrincipal;
import com.bidr.authorization.client.oauth2.IdpTokenVerifier;
import com.bidr.authorization.client.oauth2.OAuth2Client;
import com.bidr.authorization.client.oauth2.OAuth2ExchangeException;
import com.bidr.authorization.client.oauth2.OAuth2TokenRes;
import com.bidr.authorization.client.session.SubsystemSessionService;
import com.bidr.authorization.vo.login.LoginRes;
import com.bidr.kernel.exception.ServiceException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.web.bind.annotation.RequestMapping;

import java.lang.reflect.Method;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * {@link SubsystemLoginService} 账密腿＋{@link RetiredCredentialController} 让位 {@code /web/login}
 * 的离线单测（T3.1 子系统登录上提，2026-11-17）。
 *
 * <p>钉六件事：① 机器凭证未装配（{@link OAuth2Client} 缺失）⇒ 抛墓碑文案
 * {@link SubsystemLoginService#RETIRED_MESSAGE}，不降级成本地登录；② 主系统验身失败时剥掉
 * {@code OAuth2ExchangeException} 的技术前缀，只透出人话（如「用户不存在」）；③ 成功腿委托
 * {@link SubsystemLoginService#loginByIdpAccessToken}（验签 → {@link SubsystemSessionService#login}
 * 恰好一次）；④ {@link SubsystemLoginSyncHook} 抛异常时登录仍成功（best-effort 不阻断）；
 * ⑤ {@link RetiredCredentialController} 的映射表不再占用 {@code /web/login}（防两条 mapping 撞车）；
 * ⑥ 登录端点注解契约（{@code @Auth(AuthToken.class)}＋{@code @CaptchaVerify("LOGIN_CAPTCHA")}）
 * 不得退化——{@code TokenHolder} 由 {@code AuthToken} 写入，验证码校验依赖它。</p>
 *
 * @author coder (棒2, 子系统登录上提 2026-11-17)
 * @since 2026-11-17
 */
@ExtendWith(MockitoExtension.class)
class SubsystemPasswordLoginControllerTest {

    private static final String LOGIN_ID = "u1";
    private static final String PASSWORD = "p@ss";
    private static final String ACCESS_TOKEN = "idp-access-token";

    @Mock
    private ObjectProvider<OAuth2Client> oauth2ClientProvider;
    @Mock
    private ObjectProvider<IdpTokenVerifier> idpTokenVerifierProvider;
    @Mock
    private ObjectProvider<SubsystemLoginSyncHook> syncHookProvider;
    @Mock
    private SubsystemSessionService sessionService;
    @Mock
    private OAuth2Client oauth2Client;
    @Mock
    private IdpTokenVerifier idpTokenVerifier;

    private SubsystemLoginService loginService;

    @BeforeEach
    void setUp() {
        loginService = new SubsystemLoginService(oauth2ClientProvider, idpTokenVerifierProvider,
                sessionService, syncHookProvider);
    }

    @Test
    void loginByPasswordWithoutOAuth2ClientKeepsRetiredMessage() {
        when(oauth2ClientProvider.getIfAvailable()).thenReturn(null);

        ServiceException ex = assertThrows(ServiceException.class,
                () -> loginService.loginByPassword(LOGIN_ID, PASSWORD));

        assertEquals(SubsystemLoginService.RETIRED_MESSAGE, ex.getMessage());
        verifyNoInteractions(sessionService);
    }

    @Test
    void loginByPasswordStripsTechnicalPrefixFromExchangeFailure() {
        when(oauth2ClientProvider.getIfAvailable()).thenReturn(oauth2Client);
        when(oauth2Client.exchangePassword(LOGIN_ID, PASSWORD))
                .thenThrow(new OAuth2ExchangeException("IdP token 端点拒绝: business_error - 用户不存在"));

        ServiceException ex = assertThrows(ServiceException.class,
                () -> loginService.loginByPassword(LOGIN_ID, PASSWORD));

        assertEquals("用户不存在", ex.getMessage());
        verifyNoInteractions(sessionService);
    }

    @Test
    void loginByPasswordVerifiesTicketAndIssuesSessionExactlyOnce() {
        OAuth2TokenRes token = new OAuth2TokenRes();
        token.setAccessToken(ACCESS_TOKEN);
        IdpPrincipal principal = new IdpPrincipal();
        principal.setUsername(LOGIN_ID);
        LoginRes expected = new LoginRes();

        when(oauth2ClientProvider.getIfAvailable()).thenReturn(oauth2Client);
        when(oauth2Client.exchangePassword(LOGIN_ID, PASSWORD)).thenReturn(token);
        when(idpTokenVerifierProvider.getIfAvailable()).thenReturn(idpTokenVerifier);
        when(idpTokenVerifier.verify(ACCESS_TOKEN)).thenReturn(principal);
        when(sessionService.login(LOGIN_ID)).thenReturn(expected);

        LoginRes actual = loginService.loginByPassword(LOGIN_ID, PASSWORD);

        assertSame(expected, actual);
        verify(idpTokenVerifier).verify(ACCESS_TOKEN);
        verify(sessionService).login(LOGIN_ID);
    }

    @Test
    void loginSucceedsEvenWhenSyncHookThrows() {
        OAuth2TokenRes token = new OAuth2TokenRes();
        token.setAccessToken(ACCESS_TOKEN);
        IdpPrincipal principal = new IdpPrincipal();
        principal.setUsername(LOGIN_ID);
        LoginRes expected = new LoginRes();
        SubsystemLoginSyncHook hook = mock(SubsystemLoginSyncHook.class);
        doThrow(new IllegalStateException("sync down")).when(hook).afterIdpVerified(any(), any());

        when(oauth2ClientProvider.getIfAvailable()).thenReturn(oauth2Client);
        when(oauth2Client.exchangePassword(LOGIN_ID, PASSWORD)).thenReturn(token);
        when(idpTokenVerifierProvider.getIfAvailable()).thenReturn(idpTokenVerifier);
        when(idpTokenVerifier.verify(ACCESS_TOKEN)).thenReturn(principal);
        when(syncHookProvider.getIfAvailable()).thenReturn(hook);
        when(sessionService.login(LOGIN_ID)).thenReturn(expected);

        LoginRes actual = loginService.loginByPassword(LOGIN_ID, PASSWORD);

        assertSame(expected, actual);
        verify(hook).afterIdpVerified(principal, ACCESS_TOKEN);
        verify(sessionService).login(LOGIN_ID);
    }

    @Test
    void retiredCredentialControllerNoLongerMapsWebLogin() throws Exception {
        Method retired = RetiredCredentialController.class.getMethod("retired");
        RequestMapping mapping = retired.getAnnotation(RequestMapping.class);
        assertNotNull(mapping, "retired() 必须保留 @RequestMapping 墓碑映射");

        assertFalse(Arrays.asList(mapping.value()).contains("/web/login"),
                "「/web/login」已由 SubsystemPasswordLoginController 独占，墓碑不得再占用");
        assertEquals(8, mapping.value().length, "其余 8 条墓碑路径必须原样保留");
    }

    @Test
    void passwordLoginEndpointRequiresCaptchaAndTokenRole() throws Exception {
        // 契约钉死：@Auth(AuthToken.class) 写 TokenHolder，@CaptchaVerify 才能在拦截器里取到临时 token
        Method login = SubsystemPasswordLoginController.class.getMethod("login", String.class, String.class);
        Auth auth = login.getAnnotation(Auth.class);
        CaptchaVerify captcha = login.getAnnotation(CaptchaVerify.class);

        assertNotNull(auth);
        assertEquals(1, auth.value().length);
        assertEquals(AuthToken.class, auth.value()[0]);
        assertNotNull(captcha);
        assertEquals("LOGIN_CAPTCHA", captcha.value());
    }
}