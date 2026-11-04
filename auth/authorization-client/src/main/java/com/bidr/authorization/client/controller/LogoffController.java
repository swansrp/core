package com.bidr.authorization.client.controller;

import com.bidr.authorization.service.token.TokenService;
import com.bidr.kernel.config.response.Resp;
import io.swagger.annotations.Api;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;

/**
 * Title: LogoffController
 * Description: Copyright: Copyright (c) 2023
 *
 * <p>T3.1 棒0：自 authorization(server) 同 URL 迁入 client（M-36 迁移不并存，server 原件已删）。
 * 语义等价 {@code LoginServiceImpl#logoff()} 的两行（删票 ＋ 验票收尾）——client 不能依赖
 * server 的 {@code LoginService}，故直接用 {@link TokenService} 表达；提示文案逐字保持
 * （kernel {@code VoidEndpointNoticeConventionTest} 按字面比对）。</p>
 *
 * @author Sharp
 * @since 2023/08/25 14:53
 */
@Api(tags = "系统基础 - 登录操作")
@RestController("LogoffController")
@RequestMapping(value = "/web/logoff")
@RequiredArgsConstructor
public class LogoffController {

    private final TokenService tokenService;

    @RequestMapping(value = "", method = RequestMethod.POST)
    public void logoff() {
        // 🔴 这两行是**刻意设计，不是死代码，勿删勿"优化"**（2026-11-04 曾误判为死代码改成
        // 200 SUCCESS，当天回滚——见对账单 #39）：
        //   ① removeToken()：删服务端票（真正让会话失效）；
        //   ② verifyToken(null)：**故意**验空票，让 Validator.assertNotNull 抛
        //      {@code SYS_SESSION_TIME_OUT}（401）。这个 401 就是登出的**返回协议**——
        //      前端多处（framework TopHeader、新 IA InkHubTopNav 等）的 logoff() 是裸调、
        //      自身不做跳转，全靠 request 拦截器的 SESSION_TIME_OUT 分支跳登录页。
        //      改成 200 会让这些调用点"点了没反应"（页面停在已注销态）。
        // 副作用（已知并接受）：异常会走 ResponseExceptionHandler → 日志留栈／通知面留一条
        // ERROR；且下一行 Resp.notice 因此**不可达**（401 即返回）。降噪归对账单 #46，
        // 不在此处改动语义。
        tokenService.removeToken();
        tokenService.verifyToken(null);
        Resp.notice("已退出登录");
    }
}
