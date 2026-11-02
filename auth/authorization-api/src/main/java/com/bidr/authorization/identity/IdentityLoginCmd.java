package com.bidr.authorization.identity;

/**
 * Title: IdentityLoginCmd
 * Description: 登录命令——三态票据（code / token / state），哪个非空用哪个。
 * 语义来自 plsintec FeishuSsoService.ssoLogin(String code)（OAuth 回调 code 票）
 * 与 bidr 票登录（勘误：id_token 实际由框架 SSO 过滤器 ambient 解析进 IdmHolder，
 * 并非 InkHubLoginService.login() 的方法参数直传，见 IdentityBridge.login 勘误段）；
 * state 形态为扩展预留。
 *
 * @author Sharp
 * @since 2026-10-31
 */
public class IdentityLoginCmd {

    /** OAuth 授权回调码（ssoLogin 形态） */
    public String code;

    /** 直传票据（id_token / CAS ticket 等形态） */
    public String token;

    /** 登录态承载（state 换取形态，扩展预留） */
    public String state;

    /**
     * 三态取票据：code → token → state 取第一个非空白值，全空返回 null
     * （票据全空视为非法请求，由桥/内核自行决定拒绝方式）。
     */
    public String credential() {
        if (code != null && !code.trim().isEmpty()) {
            return code;
        }
        if (token != null && !token.trim().isEmpty()) {
            return token;
        }
        if (state != null && !state.trim().isEmpty()) {
            return state;
        }
        return null;
    }
}
