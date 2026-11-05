package com.bidr.authorization.client.oauth2;

import com.bidr.authorization.constants.common.RequestConst;
import com.bidr.kernel.utils.Base64Util;
import com.bidr.kernel.utils.JsonUtil;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestTemplate;

import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Collections;

/**
 * Title: OAuth2Client
 * Description: IdP OAuth2 授权码客户端（T3.1 棒0：自 epc 的 InkHubOAuth2Client 迁入 core）——
 * 授权码链接拼接 ＋ code 换 access_token。
 *
 * <p>🔴 契约对齐 IdP 侧 {@code OAuth2IdpController}：</p>
 * <ul>
 *   <li>authorize：GET {base}/web/oauth2/authorize?response_type=code&amp;client_id&amp;redirect_uri&amp;state
 *       ——需浏览器登录态，未登录 302 到 IdP 本地登录页；</li>
 *   <li>token：POST {base}/web/oauth2/token，<b>form 形态</b>（服务端 @RequestParam 读 body，
 *       不走 Basic 头）：grant_type=authorization_code + code + client_id + client_secret + redirect_uri；
 *       成功＝统一信封 payload={access_token(RS256 JWT), token_type, expires_in}；
 *       失败＝HTTP 400 {error, error_description}（RFC 6749 §5.2）。</li>
 * </ul>
 *
 * <p>🔴 UTF-8 纪律：响应体用 {@code byte[]} 接再显式 UTF-8 解码（RestTemplate 的 String 转换器
 * 按响应头 charset 走、缺省 ISO-8859-1 的旧病根）。</p>
 *
 * <p>消费方：宿主登录链（epc {@code InkHubOAuth2LoginController} 等）；本类不依赖 server 模块。</p>
 *
 * @author coder (棒0, T3.1 客户端件下沉 2026-11-03)
 * @since 2026-11-03
 */
@Slf4j
public class OAuth2Client {

    private final String baseUrl;
    private final String clientId;
    private final String clientSecret;
    private final String redirectUri;
    private final RestTemplate restTemplate;

    public OAuth2Client(OAuth2ClientProperties properties) {
        this(properties == null ? null : properties.getBaseUrl(),
                properties == null ? null : properties.getClientId(),
                properties == null ? null : properties.getClientSecret(),
                properties == null ? null : properties.getRedirectUri());
    }

    /** 直构形态（单测/手工接线用）：四项配置值直接给，裁尾斜杠与 secret trim 口径同配置形态 */
    public OAuth2Client(String baseUrl, String clientId, String clientSecret, String redirectUri) {
        String base = baseUrl == null ? "" : baseUrl;
        this.baseUrl = base.endsWith("/") ? base.substring(0, base.length() - 1) : base;
        this.clientId = clientId == null ? "" : clientId;
        this.clientSecret = clientSecret == null ? "" : clientSecret.trim();
        this.redirectUri = redirectUri == null ? "" : redirectUri;
        this.restTemplate = new RestTemplate();
    }

    /**
     * 拼授权码入口 URL（浏览器 302 目标）。state 原样透传（IdP 端 RFC 6749 §4.1.1 回传）。
     *
     * @param state 防 CSRF 的一次性随机串（空白＝不带 state 参数）
     * @return 授权页 URL（client_id／redirect_uri／state 均 UTF-8 URL 编码）
     */
    public String buildAuthorizeUrl(String state) {
        StringBuilder url = new StringBuilder(baseUrl)
                .append("/web/oauth2/authorize?response_type=code")
                .append("&client_id=").append(urlEncode(clientId))
                .append("&redirect_uri=").append(urlEncode(redirectUri));
        if (state != null && !state.isEmpty()) {
            url.append("&state=").append(urlEncode(state));
        }
        return url.toString();
    }

    /**
     * 授权码换 access_token（POST /web/oauth2/token，form 形态，secret 走 body——IdP 端入参契约）。
     *
     * @param code        authorize 302 回跳带回的授权码（单次有效）
     * @param redirectUri 必须与 authorize 时的 redirect_uri 全串一致（IdP 端等值复核）
     * @return token 响应（access_token/token_type/expires_in）
     * @throws OAuth2ExchangeException HTTP 非 2xx（解析 {error,error_description}）或信封/形状异常
     */
    public OAuth2TokenRes exchangeCode(String code, String redirectUri) {
        String url = baseUrl + "/web/oauth2/token";
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("grant_type", "authorization_code");
        form.add("code", code);
        form.add("client_id", clientId);
        form.add("client_secret", clientSecret);
        form.add("redirect_uri", redirectUri);
        return postForToken(url, form, null);
    }

    /**
     * 账密换 access_token（POST {base}/open-api/v1/idp/password-login，form 形态）——
     * 子系统密码透传腿：登录页收到的用户账密由子系统后端凭机器凭证代问主系统验身。
     *
     * <p>与 {@link #exchangeCode} 的鉴权形态不同：凭据是用户账密（loginId/password 走 body），
     * 调用方身份走机器凭证头 {@code x-api-key: Base64(appKey:appSecret)}（server 侧
     * {@code AuthPartner} 验签口径），不走 OAuth2 body 的 client_id/client_secret。
     * 成功响应与授权码腿同形（{access_token(RS256 JWT), token_type, expires_in}，统一信封
     * 或裸形态都兼容）；验身失败＝业务错误信封（message 取自主系统登录链原文案）。</p>
     *
     * @param loginId  用户标识（用户名/手机号/邮箱）
     * @param password 密码（前端已 MD5 形态，与主系统登录同口径）
     * @return token 响应（access_token/token_type/expires_in）
     * @throws OAuth2ExchangeException HTTP 非 2xx、信封错误或形状异常
     */
    public OAuth2TokenRes exchangePassword(String loginId, String password) {
        String url = baseUrl + "/open-api/v1/idp/password-login";
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("loginId", loginId);
        form.add("password", password);
        return postForToken(url, form,
                Base64Util.encode(clientId + ":" + clientSecret));
    }

    /**
     * token 端点公共请求/解析段（授权码腿与账密腿共用）：form POST ＋ 统一信封/裸形态
     * 双兼容解析 ＋ 错误文案多候选提取（OAuth2 {error,error_description} 与业务信封
     * {status:{message}} 都能出人话）。apiKey 非空＝带机器凭证头（账密腿）。
     */
    private OAuth2TokenRes postForToken(String url, MultiValueMap<String, String> form, String apiKey) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);
        headers.setAccept(Collections.singletonList(MediaType.APPLICATION_JSON));
        if (apiKey != null && !apiKey.isEmpty()) {
            headers.set(RequestConst.X_API_KEY, apiKey);
        }

        ResponseEntity<byte[]> resp;
        try {
            resp = restTemplate.postForEntity(url, new HttpEntity<>(form, headers), byte[].class);
        } catch (HttpStatusCodeException e) {
            // token 端点失败＝HTTP 400 + {error, error_description}（可能被统一信封包在 payload 里）
            String body = e.getResponseBodyAsByteArray() == null
                    ? "" : new String(e.getResponseBodyAsByteArray(), StandardCharsets.UTF_8);
            JsonNode err = parseJson(body);
            JsonNode payload = err == null ? null : err.get("payload");
            String error = textField(payload, "error");
            if (error.isEmpty()) {
                error = textField(err, "error");
            }
            String description = textField(payload, "error_description");
            if (description.isEmpty()) {
                description = textField(err, "error_description");
            }
            if (error.isEmpty() && description.isEmpty()) {
                // open-api 业务信封形态（账密腿业务拒绝走框架全局异常）：文案在 status.message / message
                description = firstText(err == null ? null : err.get("status"), err, "message");
                if (!description.isEmpty()) {
                    error = "business_error";
                }
            }
            throw new OAuth2ExchangeException("IdP token 端点拒绝: " + error
                    + (description.isEmpty() ? "" : " - " + description));
        } catch (RuntimeException e) {
            throw new OAuth2ExchangeException("IdP token 端点调用失败: " + e.getMessage(), e);
        }

        if (!resp.getStatusCode().is2xxSuccessful() || resp.getBody() == null) {
            throw new OAuth2ExchangeException("IdP token 端点 HTTP " + resp.getStatusCodeValue());
        }
        String body = new String(resp.getBody(), StandardCharsets.UTF_8);
        JsonNode root = parseJson(body);
        if (root == null) {
            throw new OAuth2ExchangeException("IdP token 端点响应解析为 null");
        }
        // 统一信封两形态都兼容：{status,payload}（正常包装）或裸 {access_token,...}（未包装）
        JsonNode payload = root.has("payload") && root.get("payload").isObject() ? root.get("payload") : root;
        if (root.get("status") != null && root.get("status").has("code")
                && root.get("status").get("code").asInt() != 0) {
            String message = firstText(root.get("status"), root, "message");
            throw new OAuth2ExchangeException("IdP token 端点信封错误码: "
                    + root.get("status").get("code").asInt()
                    + (message.isEmpty() ? "" : " - " + message));
        }
        String accessToken = textField(payload, "access_token");
        if (accessToken.isEmpty()) {
            throw new OAuth2ExchangeException("IdP token 端点响应缺 access_token");
        }
        OAuth2TokenRes res = new OAuth2TokenRes();
        res.setAccessToken(accessToken);
        String tokenType = textField(payload, "token_type");
        res.setTokenType(tokenType.isEmpty() ? "Bearer" : tokenType);
        res.setExpiresIn(payload.has("expires_in") && payload.get("expires_in").isNumber()
                ? payload.get("expires_in").asLong() : null);
        return res;
    }

    /** 配置里的回调地址（authorize 与 exchange 两处必须同值，取数口只此一处） */
    public String configuredRedirectUri() {
        return redirectUri;
    }

    /** 内部 RestTemplate（包可见：仅供同包单测用 MockRestServiceServer 绑定，不改注入形态） */
    RestTemplate restTemplate() {
        return restTemplate;
    }

    /** JsonNode 文本字段安全取值（缺节点/非文本 ⇒ 空串，不抛） */
    private static String textField(JsonNode node, String field) {
        if (node == null || !node.has(field) || node.get(field).isNull()) {
            return "";
        }
        return node.get(field).asText();
    }

    /** 多候选文本取值：依次从各节点×字段名找第一个非空文本（OAuth2 错误形态与业务信封混合解析用） */
    private static String firstText(JsonNode node, JsonNode fallback, String... fields) {
        for (String field : fields) {
            String value = textField(node, field);
            if (!value.isEmpty()) {
                return value;
            }
            value = textField(fallback, field);
            if (!value.isEmpty()) {
                return value;
            }
        }
        return "";
    }

    /** JSON 串 → JsonNode（坏串 ⇒ null 不抛） */
    private static JsonNode parseJson(String json) {
        try {
            if (json == null || json.isEmpty()) {
                return null;
            }
            return JsonUtil.readJson(json, JsonNode.class);
        } catch (Exception e) {
            return null;
        }
    }

    private static String urlEncode(String value) {
        try {
            return URLEncoder.encode(value, "UTF-8");
        } catch (UnsupportedEncodingException e) {
            throw new IllegalStateException(e);
        }
    }
}
