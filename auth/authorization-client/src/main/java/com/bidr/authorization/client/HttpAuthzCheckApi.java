package com.bidr.authorization.client;

import com.bidr.authorization.constants.common.RequestConst;
import com.bidr.authorization.envelope.Envelope;
import com.bidr.kernel.utils.Base64Util;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;
import java.util.List;

/**
 * Title: HttpAuthzCheckApi
 * Description: server 侧 /authz 三端点的 HTTP 实现（T2.3b，实现 {@link AuthzCheckApi}）——
 * GET {server-base-url}/authz/check|version|menus，请求头
 * {@code x-api-key: Base64Util.encode(appKey:appSecret)}（与 server 侧 {@code AuthPartner}
 * 验签、棒3 IdentitySnapshotClient 三方同口径）。
 *
 * <p><b>响应信封</b>：宿主（InkHub）应用对 base-package 内控制器统一包
 * {@code {status:{code,msg,details}, payload:...}}（kernel ResponseExceptionHandler），
 * 本类自带同构 DTO 解包——成功判定 {@code status.code==0}，非 0 抛 IllegalStateException
 * （带 server 返回 msg）。</p>
 *
 * <p><b>测试缝</b>：{@link RestTemplate} 经构造注入可替换（单测 mock 之，五腿判定链见
 * {@code RemoteAuthzCheckerTest}）。HTTP 通道＝直连 RestTemplate（低频判定＋消费方有缓存，
 * 无连接池诉求；失败上抛 {@link RestClientException} 由调用方按 failMode 处置）。
 * 生产构造显式 connect/read 超时（{@link #productionRestTemplate}）——hub 挂起防护：
 * failMode 对挂起不可达，超时是第一道闸。</p>
 *
 * <p>🔴 client 纪律：本模块禁依赖 authorization(server)；判定数据一律经本接口走 HTTP。</p>
 *
 * @author coder (棒4, T2.3b 2026-11-03)
 * @since 2026-11-03
 */
@Slf4j
public class HttpAuthzCheckApi implements AuthzCheckApi {

    /** server 侧 AuthzApiController 固定路径 */
    public static final String CHECK_PATH = "/authz/check";
    public static final String VERSION_PATH = "/authz/version";
    public static final String MENUS_PATH = "/authz/menus";

    private final AuthzClientProperties properties;
    private final RestTemplate restTemplate;

    /** 生产构造：SimpleClientHttpRequestFactory 显式 connect/read 超时（挂起防护，见 {@link #productionRestTemplate}） */
    public HttpAuthzCheckApi(AuthzClientProperties properties) {
        this(properties, productionRestTemplate(properties));
    }

    /** 测试缝构造：RestTemplate 可注入替换（mock / fake transport），不受超时改动影响 */
    public HttpAuthzCheckApi(AuthzClientProperties properties, RestTemplate restTemplate) {
        this.properties = properties;
        this.restTemplate = restTemplate;
    }

    /**
     * 生产 RestTemplate：直连构造＋{@link SimpleClientHttpRequestFactory} 显式
     * connect/read 超时（my.authz.client.connect-timeout-ms / read-timeout-ms，缺省 3000/5000）。
     * hub 挂起防护：failMode 对「挂起」不可达（假死既不抛 RestClientException 也不返回，
     * 请求线程被逐个占满故障横向传染），<b>超时是第一道闸</b>。
     */
    static RestTemplate productionRestTemplate(AuthzClientProperties properties) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(properties.getConnectTimeoutMs());
        factory.setReadTimeout(properties.getReadTimeoutMs());
        return new RestTemplate(factory);
    }

    @Override
    public boolean check(Long userId, String scope, String permission) {
        URI uri = builder(CHECK_PATH)
                .queryParam("userId", userId)
                .queryParam("scope", scope)
                .queryParam("permission", permission)
                .build().encode().toUri();
        Envelope<CheckPayload> envelope = exchange(uri, "check",
                new ParameterizedTypeReference<Envelope<CheckPayload>>() {
                });
        return envelope.getPayload().isAllowed();
    }

    @Override
    public long authzVersion() {
        URI uri = builder(VERSION_PATH).build().encode().toUri();
        Envelope<VersionPayload> envelope = exchange(uri, "version",
                new ParameterizedTypeReference<Envelope<VersionPayload>>() {
                });
        return envelope.getPayload().getVersion();
    }

    @Override
    public List<String> menus(Long userId) {
        URI uri = builder(MENUS_PATH)
                .queryParam("userId", userId)
                .build().encode().toUri();
        Envelope<MenusPayload> envelope = exchange(uri, "menus",
                new ParameterizedTypeReference<Envelope<MenusPayload>>() {
                });
        return envelope.getPayload().getMenus();
    }

    private UriComponentsBuilder builder(String path) {
        String base = properties.getServerBaseUrl();
        if (!StringUtils.hasText(base)) {
            throw new IllegalStateException("my.authz.client.server-base-url 未配置，/authz 调用拒绝执行");
        }
        return UriComponentsBuilder.fromHttpUrl(base).path(path);
    }

    private <T> Envelope<T> exchange(URI uri, String face, ParameterizedTypeReference<Envelope<T>> type) {
        HttpHeaders headers = new HttpHeaders();
        headers.set(RequestConst.X_API_KEY, Base64Util.encode(
                properties.getAppKey() + ":" + properties.getAppSecret()));
        ResponseEntity<Envelope<T>> response =
                restTemplate.exchange(uri, HttpMethod.GET, new HttpEntity<>(headers), type);
        Envelope<T> envelope = response.getBody();
        if (envelope == null) {
            throw new IllegalStateException("/authz/" + face + " 响应体为空: " + uri);
        }
        envelope.assertSuccess("/authz/" + face);
        return envelope;
    }

    // ---------------- 响应 payload DTO（信封本体＝authorization-api 共享 Envelope，见对账单 #27） ----------------

    @lombok.Data
    static class CheckPayload {
        private boolean allowed;
        private long version;
    }

    @lombok.Data
    static class VersionPayload {
        private long version;
    }

    @lombok.Data
    static class MenusPayload {
        private List<String> menus;
        private long version;
    }
}
