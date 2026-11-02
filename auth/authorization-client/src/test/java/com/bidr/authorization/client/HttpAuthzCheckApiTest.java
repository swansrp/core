package com.bidr.authorization.client;

import com.bidr.kernel.utils.Base64Util;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;
import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Title: HttpAuthzCheckApiTest
 * Description: T2.3b /authz HTTP 面单测（mock RestTemplate＝任务钉死的测试缝）——
 * 钉四件事：①URL/查询参数拼装 ②x-api-key 头＝Base64(appKey:appSecret)（与 AuthPartner
 * 验签口径对称）③kernel Response 信封解包（status.code==0 → payload）
 * ④非成功信封/HTTP 异常上抛（fail 面交 RemoteAuthzChecker 按 failMode 处置）。
 *
 * @author coder (棒4, T2.3b 2026-11-03)
 * @since 2026-11-03
 */
public class HttpAuthzCheckApiTest {

    private static final String BASE = "http://hub:8080";

    private final AuthzClientProperties properties = new AuthzClientProperties();
    private final RestTemplate restTemplate = mock(RestTemplate.class);
    private HttpAuthzCheckApi api;

    @Before
    public void wire() {
        properties.setServerBaseUrl(BASE);
        properties.setAppKey("inkhub-partner");
        properties.setAppSecret("secret-1");
        api = new HttpAuthzCheckApi(properties, restTemplate);
    }

    private static HttpAuthzCheckApi.Envelope<Object> envelope(int code) {
        HttpAuthzCheckApi.Status status = new HttpAuthzCheckApi.Status();
        status.setCode(code);
        status.setMsg(code == 0 ? "SUCCESS" : "AC_PARTNER_NOT_EXISTED");
        HttpAuthzCheckApi.Envelope<Object> envelope = new HttpAuthzCheckApi.Envelope<>();
        envelope.setStatus(status);
        envelope.setPayload(new Object());
        return envelope;
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private void stubExchange(URI expectedUri, Object body) {
        when(restTemplate.exchange(eq(expectedUri), eq(HttpMethod.GET), any(HttpEntity.class),
                any(ParameterizedTypeReference.class)))
                .thenReturn(new ResponseEntity(body, HttpStatus.OK));
    }

    /** check：URL/查询参数/x-api-key 头/信封解包一腿钉死 */
    @Test
    public void check面_URL头与信封解包() {
        URI uri = UriComponentsBuilder.fromHttpUrl(BASE).path("/authz/check")
                .queryParam("userId", 100L).queryParam("scope", "t:7")
                .queryParam("permission", "kb.search")
                .build().encode().toUri();
        HttpAuthzCheckApi.Envelope<HttpAuthzCheckApi.CheckPayload> body =
                new HttpAuthzCheckApi.Envelope<>();
        HttpAuthzCheckApi.Status ok = new HttpAuthzCheckApi.Status();
        ok.setCode(0);
        body.setStatus(ok);
        HttpAuthzCheckApi.CheckPayload payload = new HttpAuthzCheckApi.CheckPayload();
        payload.setAllowed(true);
        payload.setVersion(3L);
        body.setPayload(payload);
        stubExchange(uri, body);

        assertTrue(api.check(100L, "t:7", "kb.search"));

        ArgumentCaptor<HttpEntity> entity = ArgumentCaptor.forClass(HttpEntity.class);
        org.mockito.Mockito.verify(restTemplate).exchange(eq(uri), eq(HttpMethod.GET),
                entity.capture(), any(ParameterizedTypeReference.class));
        HttpHeaders headers = entity.getValue().getHeaders();
        assertEquals(java.util.Collections.singletonList(
                Base64Util.encode("inkhub-partner" + ":" + "secret-1")),
                headers.get("x-api-key"));
    }

    @Test
    public void version面_信封解包() {
        URI uri = UriComponentsBuilder.fromHttpUrl(BASE).path("/authz/version")
                .build().encode().toUri();
        HttpAuthzCheckApi.Envelope<HttpAuthzCheckApi.VersionPayload> body =
                new HttpAuthzCheckApi.Envelope<>();
        HttpAuthzCheckApi.Status ok = new HttpAuthzCheckApi.Status();
        ok.setCode(0);
        body.setStatus(ok);
        HttpAuthzCheckApi.VersionPayload payload = new HttpAuthzCheckApi.VersionPayload();
        payload.setVersion(42L);
        body.setPayload(payload);
        stubExchange(uri, body);

        assertEquals(42L, api.authzVersion());
    }

    @Test
    public void menus面_信封解包() {
        URI uri = UriComponentsBuilder.fromHttpUrl(BASE).path("/authz/menus")
                .queryParam("userId", 100L).build().encode().toUri();
        HttpAuthzCheckApi.Envelope<HttpAuthzCheckApi.MenusPayload> body =
                new HttpAuthzCheckApi.Envelope<>();
        HttpAuthzCheckApi.Status ok = new HttpAuthzCheckApi.Status();
        ok.setCode(0);
        body.setStatus(ok);
        HttpAuthzCheckApi.MenusPayload payload = new HttpAuthzCheckApi.MenusPayload();
        payload.setMenus(Arrays.asList("kb.search", "epc.supervision"));
        payload.setVersion(7L);
        body.setPayload(payload);
        stubExchange(uri, body);

        List<String> menus = api.menus(100L);
        assertEquals(Arrays.asList("kb.search", "epc.supervision"), menus);
    }

    /** 非成功信封（如鉴权失败）→ IllegalStateException 上抛（判定失败面） */
    @Test
    public void 非成功信封_上抛() {
        URI uri = UriComponentsBuilder.fromHttpUrl(BASE).path("/authz/version")
                .build().encode().toUri();
        stubExchange(uri, envelope(401));
        assertThrows(IllegalStateException.class, api::authzVersion);
    }

    /** HTTP 层异常（超时/不可达）原样上抛，由 RemoteAuthzChecker 按 failMode 处置 */
    @Test
    @SuppressWarnings("unchecked")
    public void http异常_原样上抛() {
        when(restTemplate.exchange(any(URI.class), eq(HttpMethod.GET), any(HttpEntity.class),
                any(ParameterizedTypeReference.class)))
                .thenThrow(new RestClientException("connect refused"));
        assertThrows(RestClientException.class, api::authzVersion);
    }

    /** base-url 未配置＝误开形态，快速失败 */
    @Test
    public void baseUrl未配置_快速失败() {
        properties.setServerBaseUrl("");
        assertThrows(IllegalStateException.class, () -> api.authzVersion());
    }
}
