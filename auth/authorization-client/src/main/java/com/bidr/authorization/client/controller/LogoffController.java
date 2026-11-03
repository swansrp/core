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
        // 语义＝删票即完成。原第二行 verifyToken(null) 在删票之后验 null 必然 assertNotNull 炸
        // （TokenServiceImpl:78）⇒ 登出永远以「登录信息已过期」异常信封收场、前端不跳登录页
        // （6.60 真机 #39 实证：日志 LogoffController.java:34 栈）。删票后本无票可验，验票收尾
        // 无意义——2023 年出生时即如此，T3.1 重构 TokenService 后由潜伏转为必炸。
        tokenService.removeToken();
        Resp.notice("已退出登录");
    }
}
