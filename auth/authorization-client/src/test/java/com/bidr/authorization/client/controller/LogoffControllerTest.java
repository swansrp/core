package com.bidr.authorization.client.controller;

import com.bidr.authorization.service.token.TokenService;
import com.bidr.kernel.exception.NoticeException;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.MockitoJUnitRunner;

/**
 * 登出端点回归针（#39，6.60 真机实证）：logoff 必须＝删票后以 {@link NoticeException}
 * （「已退出登录」）正常收场——不得再调 {@code verifyToken(null)}。历史写法在删票后验 null，
 * TokenServiceImpl 的 assertNotNull 必炸 ⇒ 登出永远以「登录信息已过期」异常信封收场、
 * 前端不跳登录页（2023 年出生即如此，T3.1 重构 TokenService 后由潜伏转为必炸）。
 *
 * @author sharp
 * @since 2026-11-04
 */
@RunWith(MockitoJUnitRunner.class)
public class LogoffControllerTest {

    @Mock
    private TokenService tokenService;

    @InjectMocks
    private LogoffController controller;

    @Test
    public void logoffRemovesTokenAndFinishesAsNotice() {
        NoticeException ex = Assert.assertThrows(NoticeException.class, () -> controller.logoff());
        Assert.assertEquals("已退出登录", ex.getMessage());
        Mockito.verify(tokenService).removeToken();
        Mockito.verify(tokenService, Mockito.never()).verifyToken(Mockito.any());
        Mockito.verifyNoMoreInteractions(tokenService);
    }
}
