package com.bidr.authorization.client.login;

import com.bidr.authorization.client.oauth2.IdpPrincipal;
import com.bidr.authorization.client.oauth2.IdpTokenVerifier;
import com.bidr.authorization.client.oauth2.OAuth2Client;
import com.bidr.authorization.client.session.SubsystemSessionService;
import com.bidr.authorization.vo.login.LoginRes;
import com.bidr.kernel.constant.err.ErrCodeSys;
import com.bidr.kernel.exception.ServiceException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.ObjectProvider;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * {@link SubsystemLoginService} 的 fail-closed 腿（R-1 修复轮，2026-11-17）：
 *
 * <p>钉五件事：① {@code loginByIdToken} 的空票（null/空串/空白）一律回墓碑文案
 * {@link SubsystemLoginService#RETIRED_MESSAGE}，验票器/会话零交互；② {@link IdpTokenVerifier}
 * 未装配时同墓碑（不静默降级）；③ 本地验签返回 null 时明确拒绝（「墨枢票验签失败或已过期」），
 * 登录后同步 hook 与会话签发零交互；④ 账密参数为空按 {@link ErrCodeSys#PA_PARAM_FORMAT}
 * 拒绝，且不触达机器凭证；⑤ 登录后同步 hook 必须发生在 {@link SubsystemSessionService#login(String)}
 * 之前（先同步镜像/租户，后签发会话）。</p>
 *
 * @author coder (修复2, 2026-11-17)
 * @since 2026-11-17
 */
@ExtendWith(MockitoExtension.class)
class SubsystemLoginServiceTest {

    private static final String ACCESS_TOKEN = "idp-access-token";
    private static final String LOGIN_ID = "u1";
    private static final String VERIFY_FAILED_MESSAGE = "墨枢票验签失败或已过期，请重新登录";

    @Mock
    private ObjectProvider<OAuth2Client> oauth2ClientProvider;
    @Mock
    private ObjectProvider<IdpTokenVerifier> idpTokenVerifierProvider;
    @Mock
    private ObjectProvider<SubsystemLoginSyncHook> syncHookProvider;
    @Mock
    private SubsystemSessionService sessionService;
    @Mock
    private IdpTokenVerifier idpTokenVerifier;
    @Mock
    private SubsystemLoginSyncHook syncHook;

    private SubsystemLoginService loginService;

    @BeforeEach
    void setUp() {
        loginService = new SubsystemLoginService(oauth2ClientProvider, idpTokenVerifierProvider,
                sessionService, syncHookProvider);
    }

    @Test
    void loginByIdTokenBlankAlwaysKeepsRetiredMessage() {
        String[] blanks = {null, "", " "};
        for (String blank : blanks) {
            ServiceException ex = assertThrows(ServiceException.class,
                    () -> loginService.loginByIdToken(blank), "blank=[" + blank + "]");

            assertEquals(SubsystemLoginService.RETIRED_MESSAGE, ex.getMessage(), "blank=[" + blank + "]");
        }
        // 空票在任何验票/同步/会话交互之前就被拒绝
        verifyNoInteractions(idpTokenVerifierProvider, idpTokenVerifier, syncHookProvider, sessionService);
    }

    @Test
    void idpTokenVerifierMissingKeepsRetiredMessage() {
        when(idpTokenVerifierProvider.getIfAvailable()).thenReturn(null);

        ServiceException ex = assertThrows(ServiceException.class,
                () -> loginService.loginByIdToken(ACCESS_TOKEN));

        assertEquals(SubsystemLoginService.RETIRED_MESSAGE, ex.getMessage());
        verifyNoInteractions(syncHook, syncHookProvider, sessionService);
    }

    @Test
    void verificationFailureRejectsWithoutHookOrSession() {
        when(idpTokenVerifierProvider.getIfAvailable()).thenReturn(idpTokenVerifier);
        when(idpTokenVerifier.verify(ACCESS_TOKEN)).thenReturn(null);

        ServiceException ex = assertThrows(ServiceException.class,
                () -> loginService.loginByIdToken(ACCESS_TOKEN));

        assertEquals(VERIFY_FAILED_MESSAGE, ex.getMessage());
        verifyNoInteractions(syncHook, syncHookProvider, sessionService);
    }

    @Test
    void loginByPasswordRejectsBlankParametersWithParamFormat() {
        ServiceException blankLoginId = assertThrows(ServiceException.class,
                () -> loginService.loginByPassword("", "x"));
        ServiceException blankPassword = assertThrows(ServiceException.class,
                () -> loginService.loginByPassword("x", ""));

        assertEquals(ErrCodeSys.PA_PARAM_FORMAT, blankLoginId.getErrCode());
        assertEquals(ErrCodeSys.PA_PARAM_FORMAT, blankPassword.getErrCode());
        // 参数闸先于机器凭证：OAuth2Client/验票器/同步/会话全部零交互
        verifyNoInteractions(oauth2ClientProvider, idpTokenVerifierProvider, syncHookProvider, sessionService);
    }

    @Test
    void syncHookRunsBeforeSessionIssuance() {
        IdpPrincipal principal = new IdpPrincipal();
        principal.setUsername(LOGIN_ID);
        LoginRes expected = new LoginRes();

        when(idpTokenVerifierProvider.getIfAvailable()).thenReturn(idpTokenVerifier);
        when(idpTokenVerifier.verify(ACCESS_TOKEN)).thenReturn(principal);
        when(syncHookProvider.getIfAvailable()).thenReturn(syncHook);
        when(sessionService.login(LOGIN_ID)).thenReturn(expected);

        LoginRes actual = loginService.loginByIdToken(ACCESS_TOKEN);

        assertSame(expected, actual);
        InOrder order = inOrder(syncHook, sessionService);
        order.verify(syncHook).afterIdpVerified(principal, ACCESS_TOKEN);
        order.verify(sessionService).login(LOGIN_ID);
    }
}