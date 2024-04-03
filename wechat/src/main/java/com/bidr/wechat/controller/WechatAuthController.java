package com.bidr.wechat.controller;

import com.bidr.authorization.service.login.LoginFillTokenInf;
import com.bidr.authorization.vo.login.LoginRes;
import com.bidr.kernel.utils.FuncUtil;
import com.bidr.wechat.service.auth.WechatAuthJdkService;
import com.bidr.wechat.service.auth.WechatAuthService;
import com.bidr.wechat.vo.auth.*;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiImplicitParam;
import io.swagger.annotations.ApiImplicitParams;
import io.swagger.annotations.ApiOperation;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;

import javax.annotation.Resource;
import java.util.List;

/**
 * Title: WechatAuthController
 * Description: Copyright: Copyright (c) 2019 Company: BHFAE
 *
 * @author Sharp
 * @since 2020/6/19 10:50
 */
@Slf4j
@RestController
@RequestMapping(value = "/wechat/auth")
@Api(value = "微信-公众号鉴权", tags = "微信-公众号鉴权")
public class WechatAuthController {

    @Resource
    private WechatAuthService wechatAuthService;
    @Resource
    private WechatAuthJdkService wechatAuthJdkService;

    @Autowired(required = false)
    private List<LoginFillTokenInf> fillTokenInfList;


    @ApiOperation(value = "获取OAuth2登录url", notes = "获取OAuth2登录url")
    @ApiImplicitParams({@ApiImplicitParam(paramType = "query", dataType = "string", name = "redirectUrl", value = "重定向url", required = true), @ApiImplicitParam(paramType = "query", dataType = "string", name = "state", value = "状态")})
    @RequestMapping(value = "", method = RequestMethod.GET)
    public AuthUrlRes getAuthUrl(AuthUrlReq req) {
        return wechatAuthService.getOAuthUrl(req);
    }

    @ApiOperation(value = "使用鉴权码登录", notes = "使用鉴权码登录")
    @ApiImplicitParams({@ApiImplicitParam(paramType = "query", dataType = "string", name = "code", value = "鉴权码", required = true)})
    @RequestMapping(value = "", method = RequestMethod.POST)
    public LoginRes getAuthUrl(AuthReq req) {
        LoginRes res = wechatAuthService.login(req);
        afterLogin(res);
        return res;
    }

    @ApiOperation(value = "获取js sdk鉴权信息", notes = "获取js sdk鉴权信息")
    @ApiImplicitParams({@ApiImplicitParam(paramType = "query", dataType = "string", name = "baseUrl", value = "应用域名", required = true)})
    @RequestMapping(value = "/jsApi", method = RequestMethod.GET)
    public AuthJdkApiRes getJsApiTicket(AuthJdkApiReq req) {
        return wechatAuthJdkService.getAuthJdkApiSignature(req);
    }

    protected void afterLogin(LoginRes res) {
        if (FuncUtil.isNotEmpty(fillTokenInfList)) {
            for (LoginFillTokenInf loginFillTokenInf : fillTokenInfList) {
                loginFillTokenInf.fillToken(res);
            }
        }
    }

}
