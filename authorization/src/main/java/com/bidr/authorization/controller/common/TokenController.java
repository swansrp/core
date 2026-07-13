package com.bidr.authorization.controller.common;

import com.bidr.authorization.annotation.auth.Auth;
import com.bidr.authorization.annotation.auth.AuthNone;
import com.bidr.authorization.annotation.auth.AuthToken;
import com.bidr.authorization.service.token.TokenService;
import com.bidr.authorization.utils.token.AuthTokenUtil;
import com.bidr.authorization.vo.token.TokenReq;
import com.bidr.authorization.vo.token.TokenRes;
import com.bidr.kernel.utils.StringUtil;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;

/**
 * Title: TokenController
 * Description: Copyright: Copyright (c) 2022 Company: Sharp Ltd.
 *
 * @author Sharp
 * @since 2023/04/26 16:53
 */
@Api(tags = "系统基础 - TOKEN操作")
@RestController("TokenController")
@RequestMapping(value = "/web/token")
@RequiredArgsConstructor
public class TokenController {

    private final TokenService tokenService;

    @Auth(AuthNone.class)
    @ApiOperation(value = "获取token", notes = "获取token,确保服务正常")
    @RequestMapping(value = "", method = RequestMethod.GET)
    public TokenRes fetchToken() {
        return new TokenRes(tokenService.fetchToken());
    }

    @Auth(AuthNone.class)
    @ApiOperation(value = "检查token是否有效", notes = "检查token是否有效")
    @RequestMapping(value = "", method = RequestMethod.POST)
    public String verifyToken(@RequestBody @Validated TokenReq req) {
        boolean res = tokenService.verifyToken(AuthTokenUtil.decode(req.getToken()));
        return StringUtil.convertSwitch(res);
    }

    @Auth(AuthToken.class)
    @ApiOperation(value = "获取扫码登录Token", notes = "获取扫码登录用的临时Token，用于生成二维码")
    @RequestMapping(value = "/qrCode", method = RequestMethod.GET)
    public TokenRes getQrLoginToken() {
        String customerNumber = tokenService.getCurrentUserId();
        return new TokenRes(tokenService.fetchToken(customerNumber));
    }
}
