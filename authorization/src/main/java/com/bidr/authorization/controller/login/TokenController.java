package com.bidr.authorization.controller.login;

import com.bidr.authorization.annotation.auth.Auth;
import com.bidr.authorization.annotation.auth.AuthNone;
import com.bidr.authorization.service.token.TokenService;
import com.bidr.authorization.utils.token.AuthTokenUtil;
import com.bidr.authorization.vo.token.TokenRes;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import lombok.RequiredArgsConstructor;
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
@Api(value = "TOKEN操作", tags = "登录操作")
@RestController("TokenController")
@RequestMapping(value = "/web")
@RequiredArgsConstructor
public class TokenController {

    private final TokenService tokenService;

    @Auth(AuthNone.class)
    @ApiOperation(value = "获取token", notes = "获取token,确保服务正常")
    @RequestMapping(value = "/token", method = RequestMethod.GET)
    public TokenRes fetchToken() {
        return new TokenRes(tokenService.fetchToken());
    }

    @Auth(AuthNone.class)
    @ApiOperation(value = "检查token是否有效", notes = "检查token是否有效")
    @RequestMapping(value = "/token", method = RequestMethod.POST)
    public boolean verifyToken(String token) {
        return tokenService.verifyToken(AuthTokenUtil.resolveToken(token));
    }
}
