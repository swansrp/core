package com.bidr.authorization.controller.login;

import com.bidr.authorization.annotation.auth.Auth;
import com.bidr.authorization.annotation.auth.AuthNone;
import com.bidr.authorization.dto.openapi.OpenApiTokenRcv;
import com.bidr.authorization.dto.openapi.OpenApiTokenRes;
import com.bidr.authorization.dto.openapi.SignDTO;
import com.bidr.authorization.service.login.OpenApiLoginService;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Profile;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;

/**
 * Title: OpenApiController
 * Description: Copyright: Copyright (c) 2023
 *
 * @author Sharp
 * @since 2023/05/30 16:52
 */
@Api(tags = "外部对接系统 - 登录操作")
@RestController("OpenApiController")
@RequestMapping(value = "/open-api")
@RequiredArgsConstructor
public class OpenApiController {

    private final OpenApiLoginService openApiLoginService;

    @Auth(AuthNone.class)
    @ApiOperation(value = "获取token", notes = "获取接入token")
    @RequestMapping(value = "/token", method = RequestMethod.POST)
    public OpenApiTokenRes fetchToken(@RequestBody @Validated OpenApiTokenRcv sign) {
        return openApiLoginService.getToken(sign);
    }

    @Auth(AuthNone.class)
    @ApiOperation(value = "测试获取生成签名")
    @Profile(value = {"dev", "test", "preview"})
    @RequestMapping(value = "/sign", method = RequestMethod.GET)
    public SignDTO testSign(String appKey) {
        return openApiLoginService.sign(appKey);
    }
}
