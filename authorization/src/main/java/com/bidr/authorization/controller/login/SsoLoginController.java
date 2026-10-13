package com.bidr.authorization.controller.login;

import com.bidr.authorization.annotation.auth.Auth;
import com.bidr.authorization.annotation.auth.AuthNone;
import com.bidr.authorization.service.login.LoginFillTokenInf;
import com.bidr.authorization.service.login.impl.SsoLoginServiceImpl;
import com.bidr.authorization.service.token.TokenService;
import com.bidr.authorization.vo.login.LoginRes;
import com.bidr.kernel.utils.FuncUtil;
import com.bidr.kernel.utils.JsonUtil;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;

import javax.annotation.Resource;
import java.util.List;
import java.util.Map;

/**
 * Title: SsoLoginController
 * Description: SSO 登录签发口（core 底座自带，M15 棒 0 纯加法）。
 * <p>
 * 独立 bean 名 + 独立路径 {@code POST /web/sso/login}：不继承 {@code LoginController}
 * （继承会带出重复映射风险），也不复用 /web/auth（框架侧同名 bean 不可依赖）。
 * 收尾与 {@code LoginController.afterLogin} 行为等价（fillToken 链 + 登录留痕日志）。
 * </p>
 * Copyright: Copyright (c) 2026 Company: Bidr Ltd.
 *
 * @author Sharp
 * @since 2026/10/14
 */
@Slf4j
@Api(tags = "系统基础 - SSO单点登录")
@RestController("SsoLoginController")
@RequestMapping(value = "/web/sso")
public class SsoLoginController {

    @Resource
    private SsoLoginServiceImpl ssoLoginService;
    @Resource
    private TokenService tokenService;

    @Autowired(required = false)
    private List<LoginFillTokenInf> fillTokenInfList;

    /**
     * SSO 登录签发。
     *
     * @param id_token 仅为与既有 bidr 形态 SSO 口契约形状兼容而声明；真实身份取自
     *                 {@link com.bidr.authorization.service.login.SsoUserHandler} SPI 的线程态，不参与解析
     * @return 登录态与 token（与 /web/login 出参同形）
     */
    @Auth(AuthNone.class)
    @ApiOperation(value = "SSO单点登录签发",
            notes = "身份来源=部署形态注册的 SsoUserHandler（bidr形态＝idm验签后的线程态；自接其它IdP＝自写实现）。"
                    + "三态语义：1) 身份解析成功→按 ac_user命中登录 / ac_account主数据注册 / 均无则建SSO类型新用户 三分支签发登录态；"
                    + "2a) SsoUserHandler bean 缺席→业务拒绝（SYS_ERR_MSG，文案含'未接入SSO身份源'）；"
                    + "2b) handler 在但解析不出用户名→业务拒绝（SYS_ERR_MSG，文案含'SSO身份源未返回用户身份'）；"
                    + "2c) 注册多个实现且无法唯一生效→业务拒绝（SYS_ERR_MSG，文案含'SsoUserHandler 实现冲突'，有 @Primary 者可正常消歧）；"
                    + "2d) SPI 实现自身装配故障不属于以上三态，按框架口径上浮为系统错误（不伪装成业务拒绝）；"
                    + "3) id_token 形参仅为形状兼容，缺失或为空不影响判定。")
    @RequestMapping(value = "/login", method = RequestMethod.POST)
    public LoginRes login(String id_token) {
        LoginRes res = ssoLoginService.login();
        afterLogin(res);
        return res;
    }

    private void afterLogin(LoginRes res) {
        if (FuncUtil.isNotEmpty(fillTokenInfList)) {
            for (LoginFillTokenInf loginFillTokenInf : fillTokenInfList) {
                loginFillTokenInf.fillToken(res);
            }
        }
        Map<String, Object> tokenValue = tokenService.getTokenValue();
        log.info("[登录信息]用户: {}, 登录token: {}, 内容: {}", res.getCustomerNumber(), res.getAccessToken(),
                JsonUtil.toJson(tokenValue, false, false, true));
    }
}
