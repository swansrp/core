package com.bidr.authorization.controller.login;

import com.bidr.authorization.annotation.auth.Auth;
import com.bidr.authorization.annotation.auth.AuthNone;
import com.bidr.authorization.service.login.SsoTokenIntrospectService;
import com.bidr.authorization.vo.login.TokenIntrospectRes;
import com.bidr.authorization.vo.token.TokenReq;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import lombok.extern.slf4j.Slf4j;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;

/**
 * Title: TokenIntrospectController
 * Description: Token 回查（introspection）端点（core 底座自带，M15 棒 0 纯加法）。
 * <p>
 * {@code POST /web/sso/introspect}，入参复用 core 现成 {@link TokenReq}；
 * 判定逻辑抽在 {@link SsoTokenIntrospectService}（便于纯 Mockito 单测）。
 * 分层判据（已拍板）：三态判定人人可查；身份明细仅当调用方自身带有效登录态才填充。
 * </p>
 * Copyright: Copyright (c) 2026 Company: Bidr Ltd.
 *
 * @author Sharp
 * @since 2026/10/14
 */
@Slf4j
@Api(tags = "系统基础 - SSO Token回查")
@RestController("TokenIntrospectController")
@RequestMapping(value = "/web/sso")
public class TokenIntrospectController {

    @Resource
    private SsoTokenIntrospectService ssoTokenIntrospectService;

    @Auth(AuthNone.class)
    @ApiOperation(value = "Token回查(introspection)",
            notes = "三态语义：1) token串不可解析→业务拒绝（PA_PARAM_FORMAT，'安全令牌格式错误'；可解码但 type 段未知同归此态）；"
                    + "2) 可解析但无效（不存在于服务端会话/游客态/已注销/归属不符）→valid=false，tokenType仍给出；"
                    + "3) 有效登录态→valid=true。"
                    + "分层判据：任何人可查三态；身份明细（customerNumber/userId/name/expireSeconds）"
                    + "仅当调用方自身在请求头携带有效登录态时才填充，否则留空；归属用户查不到时留空不报错。")
    @RequestMapping(value = "/introspect", method = RequestMethod.POST)
    public TokenIntrospectRes introspect(@RequestBody @Validated TokenReq req, HttpServletRequest request) {
        return ssoTokenIntrospectService.introspect(req.getToken(), request);
    }
}
