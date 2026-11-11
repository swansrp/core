package com.bidr.authorization.controller.oauth2;

import com.bidr.authorization.annotation.auth.Auth;
import com.bidr.authorization.annotation.auth.AuthNone;
import com.bidr.authorization.bo.token.TokenInfo;
import com.bidr.authorization.config.IdentityIdpProperties;
import com.bidr.authorization.dao.entity.AcPartner;
import com.bidr.authorization.dao.entity.AcUser;
import com.bidr.authorization.dao.repository.AcUserTenantService;
import com.bidr.authorization.dao.repository.AcUserService;
import com.bidr.authorization.service.oauth2.OAuth2AuthorizationCode;
import com.bidr.authorization.service.oauth2.OAuth2AuthorizationCodeService;
import com.bidr.authorization.service.oauth2.OAuth2ClientService;
import com.bidr.authorization.service.oauth2.OAuth2TokenException;
import com.bidr.authorization.service.oauth2.OAuth2TokenService;
import com.bidr.authorization.service.token.TokenService;
import com.bidr.authorization.utils.jwt.IdpJwksUtil;
import com.bidr.authorization.utils.jwt.IdpJwtUtil;
import com.bidr.authorization.utils.token.AuthTokenUtil;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Title: OAuth2IdpController
 * Description: 产品内部 IdP 三端点（T1.2 棒 6 纯加法，RFC 6749 授权码腿 demo 形态）：
 * <ul>
 *   <li>{@code GET/POST /web/oauth2/authorize}——授权码签发口：要求已登录（判据＝
 *       原 M15 出票口同款（该口已随 T1.3 棒 8 下线）：{@link AuthTokenUtil#extractToken} +
 *       {@link TokenService#verifyToken}，不依赖线程态）。未登录 ⇒ 302 本地登录页
 *       {@code {context-path}/#/login?redirect_uri=<原 authorize 地址>}（SPA Login 页约定参数名
 *       {@code redirect_uri}、值不带前导斜杠，登录后由 Login 页 replace 回跳）；
 *       client/redirect 校验<b>失败不 302</b>（RFC 6749 §4.1.2.1——未核验的 redirect_uri
 *       不得作为跳转目标），就地 400 文案；response_type≠code ⇒ 302 回已核验的
 *       redirect_uri 带 {@code error=unsupported_response_type}；通过 ⇒ 签发 code
 *       （单次有效 5 分钟）302 回 {@code redirect_uri?code=..&state=..}；</li>
 *   <li>{@code POST /web/oauth2/token}——授权码换 token（form 形态）：编排收口在
 *       {@link OAuth2TokenService#grantByAuthorizationCode}，错误转标准 OAuth2 400 JSON
 *       {@code {"error","error_description"}}；</li>
 *   <li>{@code GET /web/oauth2/jwks.json}——RSA 公钥 JWK（kty/n/e/kid/use/alg），kid 与票头
 *       kid 同一真相；钥匙未配置 ⇒ 503（启动已 warn，端点保持关闭）。</li>
 * </ul>
 * 🔴 三端点全 {@link AuthNone} 豁免拦截器登录闸（authorize 的登录态自判要 302 而非业务拒绝
 * JSON；token/jwks 是服务器间/无会话消费口），鉴权各自自理。
 *
 * @author Sharp
 * @since 2026-11-02
 */
@Slf4j
@Api(tags = "系统基础 - 内部IdP(OAuth2授权码)")
@RestController("OAuth2IdpController")
@RequestMapping(path = "/web/oauth2")
@RequiredArgsConstructor
public class OAuth2IdpController {

    /**
     * SPA 登录页 hash 路由后缀（framework router 本地登录页；redirect_uri 为 Login 页约定回跳参数名）。
     * 🔴 前缀必须随 context-path：SPA 挂在 {context-path}/ 下（6.60／zz 都是 /inkhub/），
     * 写死 "/#/login" 会让浏览器落在站根、hash 在 302 里丢失 ⇒ 登录页根本打不开。
     */
    private static final String LOGIN_PAGE_HASH_SUFFIX = "/#/login";

    private static final String RESPONSE_TYPE_CODE = "code";

    private final OAuth2ClientService clientService;
    private final OAuth2AuthorizationCodeService codeService;
    private final OAuth2TokenService tokenService;
    private final TokenService tokenServiceSession;
    private final AcUserService acUserService;
    private final AcUserTenantService acUserTenantService;
    private final IdentityIdpProperties idpProperties;

    /**
     * 浏览器会话 cookie 名（登录成功时由 {@code SessionCookieUtil} 在 afterLogin 链统一种下，
     * 覆盖密码登录/注册/短信/扫码/改密/refresh/SSO 换取/幽灵登录全部登录成功腿）。
     *
     * <p>存在的唯一理由＝authorize 是**整页导航**：浏览器无法携带 SPA 存在 localStorage 里的
     * Authorization 头，只认头的实现会把每个已登录用户都当成未登录、302 回登录页——登录后再跳
     * authorize 依旧无头，形成死循环。因此本端点额外接受 cookie 回退（header 优先，语义不变）。</p>
     */
    public static final String SESSION_COOKIE = "INKHUB_TOKEN";

    @Auth(AuthNone.class)
    @ApiOperation(value = "授权码签发（authorize）",
            notes = "参数 response_type=code + client_id + redirect_uri + state(可选) + scope(可选)。"
                    + "要求已登录：未登录 302 本地登录页带回跳；client 未登记/redirect_uri 未登记"
                    + "＝400 不跳转；response_type 非 code＝302 回 redirect_uri 带 error；"
                    + "通过＝302 redirect_uri?code=..&state=..（code 单次有效，默认 300 秒）")
    @RequestMapping(value = "/authorize", method = {RequestMethod.GET, RequestMethod.POST})
    public void authorize(@RequestParam(value = "response_type", required = false) String responseType,
                          @RequestParam(value = "client_id", required = false) String clientId,
                          @RequestParam(value = "redirect_uri", required = false) String redirectUri,
                          @RequestParam(value = "state", required = false) String state,
                          @RequestParam(value = "scope", required = false) String scope,
                          HttpServletRequest request, HttpServletResponse response) throws IOException {
        // 1) 登录态先判（原 M15 出票口同款判据：手抽 token＋redis 核验，不依赖线程态）
        //    header 优先；无头时回退会话 cookie（整页导航带不上 header，见 SESSION_COOKIE 注释）
        TokenInfo caller = AuthTokenUtil.extractToken(request);
        if (caller == null) {
            caller = AuthTokenUtil.resolveToken(readSessionCookie(request));
        }
        boolean login = caller != null && tokenServiceSession.verifyToken(caller);
        if (!login) {
            redirect302(response, loginBackUrl(request));
            return;
        }

        // 2) client 闸 ＋ redirect 闸：未核验的 redirect_uri 不得跳转（RFC 6749 §4.1.2.1），就地 400
        AcPartner partner = clientService.loadPartner(clientId);
        if (partner == null) {
            badRequest(response, "client_id 未登记（不在ac_partner），拒绝授权");
            return;
        }
        try {
            clientService.checkRedirectUriRegistered(partner, redirectUri);
        } catch (Exception e) {
            badRequest(response, e.getMessage());
            return;
        }

        // 3) response_type 闸：错值可安全 302（redirect_uri 已核验），标准 error 码回传
        if (!RESPONSE_TYPE_CODE.equals(responseType)) {
            String sep = redirectUri.contains("?") ? "&" : "?";
            redirect302(response, redirectUri + sep + "error=unsupported_response_type"
                    + appendState(state));
            return;
        }

        // 4) 从当前登录态固化 code 绑定上下文（工号→ac_user 取登录名/展示名/租户绑定）
        AcUser user = acUserService.getByCustomerNumber(caller.getCustomerNumber());
        if (user == null || StringUtils.isBlank(user.getUserName())) {
            badRequest(response, "当前账号无登录名（ac_user.user_name），拒绝授权");
            return;
        }
        OAuth2AuthorizationCode context = new OAuth2AuthorizationCode();
        context.setClientId(clientId.trim());
        context.setRedirectUri(redirectUri.trim());
        context.setUserId(user.getUserId());
        context.setCustomerNumber(user.getCustomerNumber());
        context.setUserName(user.getUserName());
        context.setName(StringUtils.isNotBlank(user.getNickName()) ? user.getNickName() : user.getName());
        context.setTenantIds(acUserTenantService.listTenantIdsByUserId(user.getUserId()));
        context.setScope(scope);

        // 5) 签发 code（单次有效＋TTL）并 302 回已核验的 redirect_uri
        String code = codeService.issue(context);
        String sep = redirectUri.contains("?") ? "&" : "?";
        redirect302(response, redirectUri + sep + "code=" + urlEncode(code) + appendState(state));
        log.info("[IdP授权]authorize 通过: client={}, user={}, 回跳已核验白名单地址", clientId,
                user.getCustomerNumber());
    }

    @Auth(AuthNone.class)
    @ApiOperation(value = "授权码换token（token）",
            notes = "form 形态：grant_type=authorization_code + code + client_id + client_secret"
                    + " + redirect_uri。成功＝{access_token(RS256 JWT), token_type:\"Bearer\","
                    + " expires_in}；失败＝HTTP 400 {error, error_description}（RFC 6749 §5.2 标准错误码）")
    @RequestMapping(value = "/token", method = RequestMethod.POST)
    public Map<String, Object> token(
            @RequestParam(value = "grant_type", required = false) String grantType,
            @RequestParam(value = "code", required = false) String code,
            @RequestParam(value = "client_id", required = false) String clientId,
            @RequestParam(value = "client_secret", required = false) String clientSecret,
            @RequestParam(value = "redirect_uri", required = false) String redirectUri,
            HttpServletResponse response) {
        try {
            return tokenService.grantByAuthorizationCode(grantType, code, clientId, clientSecret,
                    redirectUri);
        } catch (OAuth2TokenException e) {
            response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            Map<String, Object> err = new LinkedHashMap<>(4);
            err.put("error", e.getError());
            err.put("error_description", e.getMessage());
            return err;
        }
    }

    @Auth(AuthNone.class)
    @ApiOperation(value = "JWKS 公钥集（jwks.json）",
            notes = "RSA 公钥 JWK：{keys:[{kty:\"RSA\", n, e, kid, use:\"sig\", alg:\"RS256\"}]}；"
                    + "kid＝公钥指纹（SHA-256 前 8 字节 hex），与 token header 的 kid 同一真相。"
                    + "identity.idp 未配置＝503（端点保持关闭）")
    @RequestMapping(value = "/jwks.json", method = RequestMethod.GET)
    public Map<String, Object> jwks(HttpServletResponse response) throws IOException {
        String publicKey = idpProperties.resolvedPublicKey();
        if (!idpProperties.isConfigured() || StringUtils.isBlank(publicKey)) {
            response.setStatus(HttpServletResponse.SC_SERVICE_UNAVAILABLE);
            response.setContentType("text/plain;charset=UTF-8");
            response.getWriter().write("identity.idp 签发钥匙未配置，JWKS 端点不可用");
            return null;
        }
        return IdpJwksUtil.buildJwks(publicKey, IdpJwtUtil.computeKid(publicKey));
    }

    /** 读登录会话 cookie（无则返回 null；只在本端点用于 authorize 的整页导航回退） */
    private static String readSessionCookie(HttpServletRequest request) {
        Cookie[] cookies = request.getCookies();
        if (cookies == null) {
            return null;
        }
        for (Cookie cookie : cookies) {
            if (SESSION_COOKIE.equals(cookie.getName())) {
                return cookie.getValue();
            }
        }
        return null;
    }

    /**
     * SPA 登录页回跳地址：默认 {context-path}/#/login?redirect_uri=<原 authorize 地址（去前导斜杠，Login 页约定）>。
     * 配置 {@code identity.idp.login-page-base}（env MY_IDP_LOGIN_PAGE_BASE）⇒ 302 到
     * {base}/#/login?...（绝对地址）——供「SPA 不与后端同源挂载」的本地 dev 拓扑用（run-local 注入）：
     * pre/生产 nginx 把 SPA 挂在 {ctx}/ 下，同源相对形态即可落登录页，**不配即现行为**。
     */
    private String loginBackUrl(HttpServletRequest request) {
        String back = request.getRequestURI();
        if (back != null && back.startsWith("/")) {
            back = back.substring(1);
        }
        String query = request.getQueryString();
        if (StringUtils.isNotBlank(query)) {
            back = back + "?" + query;
        }
        String base = idpProperties.getLoginPageBase();
        String prefix = StringUtils.isNotBlank(base)
                ? base.replaceAll("/+$", "")
                : request.getContextPath();
        return prefix + LOGIN_PAGE_HASH_SUFFIX + "?redirect_uri=" + urlEncode(back);
    }

    /** state 可选回传（RFC 6749 §4.1.1 原值透传，encodeURIComponent 等价物） */
    private static String appendState(String state) {
        return StringUtils.isBlank(state) ? "" : "&state=" + urlEncode(state);
    }

    private static void redirect302(HttpServletResponse response, String location) {
        // 直写 Location 不走 sendRedirect：避免容器补 context path 改写跳转目标
        response.setStatus(HttpServletResponse.SC_FOUND);
        response.setHeader("Location", location);
    }

    private static void badRequest(HttpServletResponse response, String message) throws IOException {
        response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
        response.setContentType("text/plain;charset=UTF-8");
        response.getWriter().write(message);
    }

    private static String urlEncode(String value) {
        try {
            return URLEncoder.encode(value, "UTF-8");
        } catch (UnsupportedEncodingException e) {
            // JVM 必带 UTF-8，此分支不可达
            throw new IllegalStateException(e);
        }
    }
}
