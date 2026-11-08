package com.bidr.platform.openapi;

import lombok.Data;
import org.junit.Before;
import org.junit.Test;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestTemplate;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

/**
 * Title: OpenApiDataClientTest
 * Description: 开放口纯传输层的线格式契约钉（方案B 棒4）——照 identity-mirror 既有
 * {@code IdentitySnapshotClientEnvelopeContractTest} 形态：真实信封 JSON 字面量过
 * MockRestServiceServer 的真消息链（byte[] 接收＋UTF-8 显式解码＋宽容 Jackson 反序列化）。
 * <p>腿清单：</p>
 * <ol>
 *   <li>成功信封（含中文，<b>无 charset 头</b>）→ get 解包 payload，中文不断码（UTF-8 字节纪律）；</li>
 *   <li>非零信封（带 {@code details} 未知字段）→ 抛 {@link OpenApiEnvelopeException} 且是
 *       {@code IllegalStateException}（IdentitySnapshotClient 既有契约），message 含 code+msg，
 *       未知字段不炸反序列化（宽容消费纪律）；</li>
 *   <li>extraHeaders 凭据透传不改（调用方头原样到线）；</li>
 *   <li>POST JSON：Content-Type=application/json、body 走 UTF-8 JSON 字节、响应解包同契约；</li>
 *   <li>超时已设：显式构造值＋缺省 3000/5000 两腿（SimpleClientHttpRequestFactory 承载）。</li>
 * </ol>
 *
 * @author coder (棒4, 方案B 开放口三层沉淀 2026-11-07)
 * @since 2026/11/07
 */
public class OpenApiDataClientTest {

    private static final String BASE = "http://hub:8080";

    private static final String OK_ENVELOPE_JSON =
            "{\"status\":{\"code\":0,\"msg\":\"SUCCESS\",\"details\":null},"
                    + "\"payload\":{\"name\":\"系统管理\",\"weight\":7}}";

    private static final String FAIL_ENVELOPE_JSON =
            "{\"status\":{\"code\":1000,\"msg\":\"AC_PARTNER_NOT_EXISTED\",\"details\":\"partner 不存在\"},"
                    + "\"payload\":{\"errLevel\":\"1\"}}";

    private static final ParameterizedTypeReference<OpenApiEnvelope<Payload>> TYPE =
            new ParameterizedTypeReference<OpenApiEnvelope<Payload>>() {
            };

    private MockRestServiceServer server;
    private OpenApiDataClient client;

    @Before
    public void wire() {
        RestTemplate restTemplate = new RestTemplate();
        server = MockRestServiceServer.bindTo(restTemplate).build();
        client = new OpenApiDataClient(BASE, restTemplate);
    }

    /** 腿①：成功信封（无 charset 头＋中文）→ 解包 payload，中文不断码 */
    @Test
    public void 成功信封_get解包payload_中文不断码() {
        server.expect(requestTo(BASE + "/open-api/v1/things"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess(OK_ENVELOPE_JSON, MediaType.APPLICATION_JSON));

        Payload payload = client.get("/open-api/v1/things", null, TYPE);

        assertEquals("系统管理", payload.getName());
        assertEquals(Integer.valueOf(7), payload.getWeight());
        server.verify();
    }

    /** 腿②：非零信封（details 未知字段在场）→ OpenApiEnvelopeException 且是 IllegalStateException，携 code+msg */
    @Test
    public void 非零信封_抛OpenApiEnvelopeException_且是IllegalStateException() {
        server.expect(requestTo(BASE + "/open-api/v1/things"))
                .andRespond(withSuccess(FAIL_ENVELOPE_JSON, MediaType.APPLICATION_JSON));

        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> client.get("/open-api/v1/things", null, TYPE));
        assertTrue("须是 OpenApiEnvelopeException（非零信封专用形态）: " + ex.getClass(),
                ex instanceof OpenApiEnvelopeException);
        assertEquals(Integer.valueOf(1000), ((OpenApiEnvelopeException) ex).getCode());
        assertTrue("异常 message 须带 status.code: " + ex.getMessage(), ex.getMessage().contains("1000"));
        assertTrue("异常 message 须带 status.msg: " + ex.getMessage(), ex.getMessage().contains("AC_PARTNER_NOT_EXISTED"));
    }

    /** 腿③：extraHeaders 凭据透传不改（本类全程不动凭据——调用方写什么就到线什么） */
    @Test
    public void 调用方附加头_透传到线_不被改动() {
        server.expect(requestTo(BASE + "/open-api/v1/things"))
                .andExpect(header("x-api-key", "base64-credential-by-caller"))
                .andExpect(header("X-Custom", "keep-me"))
                .andRespond(withSuccess(OK_ENVELOPE_JSON, MediaType.APPLICATION_JSON));

        HttpHeaders extra = new HttpHeaders();
        extra.set("x-api-key", "base64-credential-by-caller");
        extra.set("X-Custom", "keep-me");
        client.get("/open-api/v1/things", extra, TYPE);

        server.verify();
    }

    /** 腿④：POST JSON——Content-Type=application/json、UTF-8 JSON 字节 body、响应解包同契约 */
    @Test
    public void post_JSON请求体_解包响应payload() {
        server.expect(requestTo(BASE + "/open-api/v1/things"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.name").value("系统管理"))
                .andExpect(jsonPath("$.weight").value(7))
                .andRespond(withSuccess(OK_ENVELOPE_JSON, MediaType.APPLICATION_JSON));

        Payload payload = client.post("/open-api/v1/things", new Payload("系统管理", 7), null, TYPE);

        assertEquals("系统管理", payload.getName());
        server.verify();
    }

    /** 腿⑤：显式构造超时已设（SimpleClientHttpRequestFactory 承载） */
    @Test
    public void 显式构造_超时已设() throws Exception {
        OpenApiDataClient dataClient = new OpenApiDataClient(BASE, 1234, 5678);

        assertTrue(dataClient.getRestTemplate().getRequestFactory() instanceof SimpleClientHttpRequestFactory);
        assertEquals(1234, timeoutField(dataClient, "connectTimeout"));
        assertEquals(5678, timeoutField(dataClient, "readTimeout"));
    }

    /** 腿⑥：便利构造缺省超时 3000/5000（挂起防护第一道闸缺省值） */
    @Test
    public void 便利构造_缺省超时3000_5000() throws Exception {
        OpenApiDataClient dataClient = new OpenApiDataClient(BASE);

        assertEquals(3000, timeoutField(dataClient, "connectTimeout"));
        assertEquals(5000, timeoutField(dataClient, "readTimeout"));
    }

    /**
     * 读 SimpleClientHttpRequestFactory 的 connectTimeout/readTimeout（spring-web 5.3 只有
     * setter 无 getter，反射读私有 int 字段——字段名 Spring 2.5 起稳定，照
     * IdentitySnapshotClientEnvelopeContractTest 同款）。
     */
    private static int timeoutField(OpenApiDataClient dataClient, String fieldName) throws Exception {
        java.lang.reflect.Field field = SimpleClientHttpRequestFactory.class.getDeclaredField(fieldName);
        field.setAccessible(true);
        return field.getInt(dataClient.getRestTemplate().getRequestFactory());
    }

    /** 测试负载 DTO（lombok getter/setter——Jackson 反序列化标准形态） */
    @Data
    private static class Payload {
        private String name;
        private Integer weight;

        Payload() {
        }

        Payload(String name, Integer weight) {
            this.name = name;
            this.weight = weight;
        }
    }
}
