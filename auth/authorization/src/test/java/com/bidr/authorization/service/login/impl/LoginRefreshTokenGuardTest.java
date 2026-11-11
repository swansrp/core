package com.bidr.authorization.service.login.impl;

import com.bidr.authorization.annotation.auth.Auth;
import com.bidr.authorization.annotation.auth.AuthNone;
import com.bidr.authorization.constants.token.TokenType;
import com.bidr.authorization.controller.login.LoginController;
import com.bidr.authorization.service.token.TokenService;
import com.bidr.authorization.utils.token.AuthTokenUtil;
import com.bidr.authorization.vo.token.TokenReq;
import com.bidr.kernel.exception.ServiceException;
import org.junit.Assert;
import org.junit.Test;
import org.mockito.Mockito;

/**
 * Title: LoginRefreshTokenGuardTest
 * Description: refresh 端点的两条安全腿（离线，纯 Mockito，不起 Spring 容器）。
 * <p>
 * 背景：/web/login/refresh 为支持 access token 过期后续期已放开鉴权
 * （{@code @Auth(AuthNone.class)}，见 LoginController#refresh）。端点自证票型必须守住两条：
 * <ol>
 *   <li><b>入口面</b>：注解必须是 AuthNone——否则过期 access token 的持有者反而进不了续期口；</li>
 *   <li><b>票型面</b>：只认刷新票。不设闸的话，任何存活 access token（含 24h 内泄露票）
 *       都能换出 7 天刷新票，续期端点就成了提权器。</li>
 * </ol>
 * 票型腿配正向对照：刷新票必须越过类型闸触达下一道存在性校验，否则「拒绝 access 票」
 * 的负向断言可能只是所有票都被拒的假绿。
 * </p>
 * Copyright: Copyright (c) 2026 Company: Bidr Ltd.
 *
 * @author Sharp
 * @since 2026-11-10
 */
public class LoginRefreshTokenGuardTest {

    @Test
    public void refreshEndpointIsPublic() throws Exception {
        Auth auth = LoginController.class.getMethod("refresh", TokenReq.class).getAnnotation(Auth.class);
        Assert.assertNotNull("refresh 缺 @Auth：默认归一为 AuthLogin，过期 access token 无法续期", auth);
        Assert.assertArrayEquals("refresh 必须放开鉴权（以票自证）",
                new Class<?>[]{AuthNone.class}, auth.value());
    }

    @Test
    public void refreshLoginRejectsAccessToken() {
        TokenService tokenService = Mockito.mock(TokenService.class);
        Mockito.when(tokenService.isTokenExist(Mockito.any())).thenReturn(true);
        LoginServiceImpl service = newService(tokenService);
        String accessToken = AuthTokenUtil.getToken("raw-token", TokenType.WEB_ACCESS_TOKEN, "U0001");

        Assert.assertThrows(ServiceException.class, () -> service.refreshLogin(accessToken));
        // 负向腿的判别力：类型闸必须在存在性校验之前短路，否则「拒绝」可能来自别处
        Mockito.verify(tokenService, Mockito.never()).isTokenExist(Mockito.any());
    }

    @Test
    public void refreshLoginPassesRefreshTokenType() {
        TokenService tokenService = Mockito.mock(TokenService.class);
        Mockito.when(tokenService.isTokenExist(Mockito.any())).thenReturn(false);
        LoginServiceImpl service = newService(tokenService);
        String refreshToken = AuthTokenUtil.getToken("raw-token", TokenType.WEB_REFRESH_TOKEN, "U0001");

        Assert.assertThrows(ServiceException.class, () -> service.refreshLogin(refreshToken));
        // 正向对照腿：刷新票越过类型闸，倒在存在性校验（此处 mock 为不存在）
        Mockito.verify(tokenService).isTokenExist(Mockito.any());
    }

    @Test
    public void refreshLoginPassesAppRefreshTokenType() {
        TokenService tokenService = Mockito.mock(TokenService.class);
        Mockito.when(tokenService.isTokenExist(Mockito.any())).thenReturn(false);
        LoginServiceImpl service = newService(tokenService);
        String appRefreshToken = AuthTokenUtil.getToken("raw-token", TokenType.APP_REFRESH_TOKEN, "U0001");

        Assert.assertThrows(ServiceException.class, () -> service.refreshLogin(appRefreshToken));
        // APP 刷新票属可续期票型，同样越过类型闸
        Mockito.verify(tokenService).isTokenExist(Mockito.any());
    }

    @Test
    public void isRefreshTokenTypeRejectsNull() {
        Assert.assertFalse(LoginServiceImpl.isRefreshTokenType(null));
    }

    /** 被测路径在存活性校验之前不触达其余依赖，故只装 tokenService；构造参数序同 @RequiredArgsConstructor。 */
    private LoginServiceImpl newService(TokenService tokenService) {
        return new LoginServiceImpl(null, null, null, null, tokenService, null, null, null, null, null, null);
    }
}
