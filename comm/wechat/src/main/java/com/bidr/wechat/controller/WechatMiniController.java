package com.bidr.wechat.controller;

import com.bidr.authorization.annotation.auth.Auth;
import com.bidr.authorization.vo.login.LoginRes;
import com.bidr.wechat.service.WechatMiniService;
import com.bidr.wechat.vo.auth.AuthRes;
import com.bidr.wechat.vo.login.WechatMiniLoginReq;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiImplicitParam;
import io.swagger.annotations.ApiImplicitParams;
import io.swagger.annotations.ApiOperation;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;

/**
 * Title: WechatMiniController
 * Description: Copyright: Copyright (c) 2019 Company: BHFAE
 *
 * @author Sharp
 * @since 2020/2/3 15:02
 */
@Slf4j
@RestController
@RequestMapping(value = "/wechat/mini")
@Api(value = "微信-小程序", tags = "微信-小程序")
public class WechatMiniController {

    @Resource
    private WechatMiniService wechatMiniService;

    @Auth
    @ApiOperation(value = "微信小程序登录", notes = "微信小程序登录")
    @RequestMapping(value = "/login", method = RequestMethod.POST)
    public LoginRes login(@RequestBody WechatMiniLoginReq req) {
        return wechatMiniService.login(req);
    }

    @ApiOperation(value = "微信小程序code鉴权", notes = "微信小程序code鉴权")
    @RequestMapping(value = "/auth", method = RequestMethod.POST)
    @ApiImplicitParams({
            @ApiImplicitParam(paramType = "query", dataType = "String", name = "wechatCode", value = "微信小程序接入码",
                    required = true)})
    public AuthRes authCode(@RequestParam(value = "wechatCode") String wechatCode) {
        return wechatMiniService.buildMiniAuthLoginRes(wechatCode);
    }
}
