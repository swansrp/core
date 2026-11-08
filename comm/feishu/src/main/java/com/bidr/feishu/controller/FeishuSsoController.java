package com.bidr.feishu.controller;

import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Conditional;
import com.bidr.authorization.annotation.auth.Auth;
import com.bidr.authorization.annotation.auth.AuthNone;
import com.bidr.authorization.service.login.impl.IdentityBridgeLoginService;
import com.bidr.authorization.vo.login.LoginRes;
import com.bidr.authorization.identity.IdentityLoginCmd;
import com.bidr.kernel.constant.err.ErrCodeSys;
import com.bidr.kernel.utils.FuncUtil;
import com.bidr.kernel.validate.Validator;
import com.bidr.feishu.bridge.FeishuIdentityBridge;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import javax.annotation.Resource;

/**
 * Title: FeishuSsoController
 * Description: 飞书单点登录控制器：获取授权页地址、授权回调换取系统登录态。
 * T1.1 棒5 起走 SPI 装配——/authorize 直调飞书身份桥（{@link FeishuIdentityBridge#authorizeUrl}），
 * /callback 改调内核 SPI 登录装配（{@link IdentityBridgeLoginService#login}＝桥验票换
 * BridgeUserPrincipal＋内核凭 principal 铸产品登录态），原 FeishuSsoService 换身份/落库半边退役。
 * 🔴 对外契约零变化：GET /web/feishu/sso/authorize?state= → 授权页地址（String）；
 * GET /web/feishu/sso/callback?code= → LoginRes——入参/出参/鉴权注解（AuthNone）逐字保持，
 * 验票失败文案（"飞书授权失败: …"）由桥内同源代码保持。
 * Copyright: Copyright (c) 2025 Company: Bidr Ltd.
 *
 * @author Sharp
 * @since 2025/01/01 00:00
 */
@Slf4j
@Api(tags = "系统基础 - 飞书单点登录")
@RestController("FeishuSsoController")
@RequestMapping(value = "/web/feishu/sso")
@ConditionalOnProperty(prefix = "feishu", name = "app-id")
@ConditionalOnExpression("'${feishu.direct:false}' != 'true'")
public class FeishuSsoController {

    @Resource
    private FeishuIdentityBridge feishuIdentityBridge;

    @Resource
    private IdentityBridgeLoginService identityBridgeLoginService;

    @Auth(AuthNone.class)
    @ApiOperation("获取飞书授权页地址")
    @GetMapping("/authorize")
    public String authorize(@RequestParam(value = "state", required = false) String state) {
        // state：回跳来路透传（飞书授权后原样回带），供回调页恢复发起登录前的页面
        return feishuIdentityBridge.authorizeUrl(state);
    }

    @Auth(AuthNone.class)
    @ApiOperation("飞书授权回调登录")
    @GetMapping("/callback")
    public LoginRes callback(@RequestParam(value = "code", required = false) String code) {
        Validator.assertTrue(FuncUtil.isNotEmpty(code), ErrCodeSys.SYS_ERR_MSG, "飞书授权码不能为空");
        IdentityLoginCmd cmd = new IdentityLoginCmd();
        cmd.code = code;
        return identityBridgeLoginService.login(cmd);
    }
}
