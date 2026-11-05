package com.bidr.authorization.controller.openapi;

import com.bidr.authorization.annotation.auth.Auth;
import com.bidr.authorization.annotation.auth.AuthPartner;
import com.bidr.authorization.constants.err.AccountErrCode;
import com.bidr.authorization.constants.token.TokenItem;
import com.bidr.authorization.holder.AccountContext;
import com.bidr.authorization.service.login.LoginService;
import com.bidr.authorization.service.oauth2.OAuth2TokenService;
import com.bidr.kernel.exception.ServiceException;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * Title: IdpPasswordLoginController
 * Description: IdP 账密换票端点（子系统密码透传腿）——{@code POST /open-api/v1/idp/password-login}，
 * {@code @Auth(AuthPartner)} 机器凭证鉴权（x-api-key: Base64(appKey:appSecret)）。
 *
 * <p>语义：子系统（identity-mirror 形态，无本地 ac_* 凭据面）登录页收到的账号密码，
 * 由子系统后端凭机器凭证代为问主系统验身。验身链与主系统本地登录<b>完全同源</b>
 * （{@code LoginService#authenticate}：查用户→状态闸→密码校验含失败计数与锁定→过期检查）；
 * 验过即按 {@code OAuth2TokenService#issueForUser} 签发 RS256 JWT——与授权码腿同一把私钥、
 * 同一 claim 形态，子系统侧 {@code IdpTokenVerifier} 本地验签零特殊化。</p>
 *
 * <p>app_id claim＝调用方接入方标识（x-api-key 解出的 appKey，AuthPartner 已烤入上下文）。
 * 无图形验证码（机器端点没有浏览器会话），防爆破由密码校验链内的失败计数＋账号锁定承担，
 * 外加机器凭证闸（错 key 连验身入口都到不了）。</p>
 *
 * @author Sharp
 * @since 2026/11/05
 */
@Api(tags = "open-api - IdP 账密换票")
@RestController
@RequestMapping(path = "/open-api/v1/idp")
@RequiredArgsConstructor
@Auth(AuthPartner.class)
public class IdpPasswordLoginController {

    private final LoginService loginService;
    private final OAuth2TokenService tokenService;

    /**
     * 账密验身换 IdP 票。
     *
     * @param loginId  用户标识（用户名/手机号/邮箱）
     * @param password 密码（前端已 MD5 形态，与主系统登录同口径）
     * @return {access_token(RS256 JWT), token_type:"Bearer", expires_in}
     */
    @ApiOperation(value = "账密换 IdP 票（子系统密码透传）",
            notes = "form: loginId + password（前端已 MD5 形态）。验身链与主系统登录完全同源"
                    + "（失败计数/锁定/过期继承）；成功＝{access_token(RS256 JWT), token_type, expires_in}")
    @RequestMapping(value = "/password-login", method = RequestMethod.POST)
    public Map<String, Object> passwordLogin(String loginId, String password) {
        if (StringUtils.isBlank(loginId) || StringUtils.isBlank(password)) {
            throw new ServiceException(AccountErrCode.AC_USER_NOT_EXISTED);
        }
        String appKey = (String) AccountContext.getExtraData(TokenItem.APP_KEY.name());
        if (StringUtils.isBlank(appKey)) {
            // AuthPartner.validate 全过才可达此处，appKey 必在；空＝上下文被旁路装配，fail-closed
            throw new ServiceException(AccountErrCode.AC_PARTNER_NOT_EXISTED);
        }
        return tokenService.issueForUser(loginService.authenticate(loginId, password), appKey, null);
    }
}
