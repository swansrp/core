package com.bidr.wechat.controller;

import com.bidr.authorization.annotation.auth.Auth;
import com.bidr.authorization.annotation.msg.MsgCodeVerify;
import com.bidr.authorization.vo.login.LoginRes;
import com.bidr.authorization.vo.msg.MsgVerificationReq;
import com.bidr.kernel.config.response.Resp;
import com.bidr.wechat.service.account.SyncAccountService;
import com.bidr.wechat.service.auth.WechatAuthService;
import com.bidr.wechat.vo.auth.AuthRealNameReq;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiImplicitParam;
import io.swagger.annotations.ApiImplicitParams;
import io.swagger.annotations.ApiOperation;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;

import javax.annotation.Resource;

/**
 * Title: WechatRegisterController
 * Description: Copyright: Copyright (c) 2019 Company: BHFAE
 *
 * @author Sharp
 * @date 2020/7/10 16:21
 */
@Slf4j
@RestController
@Auth
@RequestMapping(value = "/wechat/reg")
@Api(value = "微信-绑定账号", tags = "微信-绑定账号")
public class WechatRegisterController {
    @Resource
    private SyncAccountService syncAccountService;
    @Resource
    private WechatAuthService wechatAuthService;

    @ApiOperation(value = "微信绑定手机号", notes = "微信绑定手机号")
    @RequestMapping(value = "/phone", method = RequestMethod.POST)
    @MsgCodeVerify("BIND_PHONE_NUMBER_MSG_CODE")
    public LoginRes bindPhoneNumber(MsgVerificationReq req) {
        return wechatAuthService.bindPhoneNumber(req.getPhoneNumber());
    }

    @Auth
    @ApiOperation(value = "实名认证", notes = "实名认证")
    @ApiImplicitParams({
            @ApiImplicitParam(paramType = "query", dataType = "string", name = "idCardImgUrl", value = "身份证正面照片",
                              required =
                                      true)})
    @RequestMapping(value = "/realName", method = RequestMethod.POST)
    public void realName(AuthRealNameReq req) {
        syncAccountService.realName(req);
        Resp.notice("实名认证成功");
    }
}
