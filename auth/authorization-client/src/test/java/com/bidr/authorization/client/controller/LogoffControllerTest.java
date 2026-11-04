package com.bidr.authorization.client.controller;

import com.bidr.authorization.service.token.TokenService;
import com.bidr.kernel.constant.err.ErrCodeSys;
import com.bidr.kernel.exception.ServiceException;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.MockitoJUnitRunner;

/**
 * 登出**返回协议**守卫（#39，2026-11-04 曾误判为死代码、当天回滚后立此针）：
 *
 * <p>`logoff()` ＝ `removeToken()`（删票）＋ `verifyToken(null)` **故意**抛
 * {@code SYS_SESSION_TIME_OUT}（401）。这个 401 是登出对前端的返回协议——多处调用点
 * （framework `TopHeader`、新 IA `InkHubTopNav`）的 `logoff()` 是裸调、自身不跳转，全靠
 * request 拦截器的 SESSION_TIME_OUT 分支跳登录页。删掉第二行会让登出"点了没反应"。</p>
 *
 * <p>因此本针钉的是**两行都必须存在且顺序为删票在前**：删票使会话真正失效，401 驱动跳转。
 * 谁把 401 改成 200 SUCCESS（历史上就发生过一次），本用例立刻红。</p>
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
    public void logoffDeletesTokenThenDeliberatelyEmits401() {
        // verifyToken(null) 的真实实现＝Validator.assertNotNull → 抛 SYS_SESSION_TIME_OUT
        Mockito.when(tokenService.verifyToken(null))
                .thenThrow(new ServiceException(ErrCodeSys.SYS_SESSION_TIME_OUT));

        ServiceException ex = Assert.assertThrows(ServiceException.class, () -> controller.logoff());

        Assert.assertSame("登出的返回协议必须是 401 SESSION_TIME_OUT（前端靠它跳登录页）",
                ErrCodeSys.SYS_SESSION_TIME_OUT, ex.getErrCode());
        Mockito.verify(tokenService).removeToken();
        // 🔴 这条断言就是「第二行是设计不是死代码」的钉子：删掉即红
        Mockito.verify(tokenService).verifyToken(null);
        Mockito.verifyNoMoreInteractions(tokenService);
    }
}