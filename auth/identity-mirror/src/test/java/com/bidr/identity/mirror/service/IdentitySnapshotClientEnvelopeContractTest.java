package com.bidr.identity.mirror.service;

import com.bidr.authorization.constants.common.RequestConst;
import com.bidr.identity.mirror.IdentityMirrorProperties;
import com.bidr.identity.mirror.service.dto.SnapshotRes;
import com.bidr.kernel.utils.Base64Util;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.Before;
import org.junit.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestTemplate;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

/**
 * Title: IdentitySnapshotClientEnvelopeContractTest
 * Description: T2.3d 修复轮线格式契约钉（review 棒3 C-1/I-2）——J3 接缝的防再裂契约：
 * hub 宿主对 com.bidr 包内控制器统一包 Response 信封（kernel ResponseExceptionHandler），
 * 本测试用<b>真实信封 JSON 字面量</b>过真 Jackson 反序列化（MockRestServiceServer 走
 * RestTemplate 真消息转换器链），钉死「status.code==0 → 解包取 payload 出面」的线格式——
 * 若有人把 {@code fetchSnapshot} 改回裸 {@code SnapshotRes.class} 反序列化（信封 status/payload
 * 键被默认 ObjectMapper 静默丢弃 → 快照恒空＋关系镜像全删＋水位写 0），本测试必红。
 * <p>腿清单：</p>
 * <ol>
 *   <li>成功信封 JSON → fetchSnapshot 解包 → payload 面（watermark/users/tenants/userTenants）逐字段断言；</li>
 *   <li>非零信封（鉴权失败形态）→ IllegalStateException 带 status.msg；</li>
 *   <li>code==0 但 payload 为空 → IllegalStateException；</li>
 *   <li>信封 JSON → 裸 Jackson（不经过 RestTemplate）反序列化 Envelope 面——线格式契约双保险；</li>
 *   <li>生产 RestTemplate 显式 connect/read 超时已设（自定义值＋缺省值 3000/5000 两腿）——挂起防护第一道闸。</li>
 * </ol>
 *
 * @author coder (修复轮1, 2026-11-03)
 * @since 2026-11-03
 */
public class IdentitySnapshotClientEnvelopeContractTest {

    private static final String BASE = "http://hub:8080";

    /** 真实成功信封 JSON 字面量（kernel Response{status{code,msg,details}, payload} 全局包裹后的线格式） */
    private static final String OK_ENVELOPE_JSON =
            "{\"status\":{\"code\":0,\"msg\":\"SUCCESS\",\"details\":null},"
                    + "\"payload\":{\"watermark\":1730000000000,"
                    + "\"users\":[{\"id\":101,\"customerNumber\":\"E2026001\",\"name\":\"张三\"}],"
                    + "\"tenants\":[{\"id\":7,\"tenantCode\":\"t-7\",\"tenantName\":\"七号租户\"}],"
                    + "\"userTenants\":[{\"userId\":101,\"tenantId\":7}]}}";

    /** 非零信封 JSON 字面量（ServiceException 经全局包裹的鉴权失败形态） */
    private static final String FAIL_ENVELOPE_JSON =
            "{\"status\":{\"code\":1000,\"msg\":\"AC_PARTNER_NOT_EXISTED\",\"details\":null},"
                    + "\"payload\":{\"errLevel\":\"1\",\"errType\":\"BIZ\",\"errMsg\":\"partner 不存在\"}}";

    private IdentityMirrorProperties properties;
    private MockRestServiceServer server;
    private IdentitySnapshotClient client;

    @Before
    public void wire() {
        properties = new IdentityMirrorProperties();
        properties.setBaseUrl(BASE);
        properties.setAppKey("inkhub-partner");
        properties.setAppSecret("secret-1");
        RestTemplate restTemplate = new RestTemplate();
        server = MockRestServiceServer.bindTo(restTemplate).build();
        client = new IdentitySnapshotClient(properties, restTemplate);
    }

    /** 腿①：真实信封 JSON 过真 Jackson 消息转换器 → 解包 → payload 逐字段出面 */
    @Test
    public void 真实信封_fetchSnapshot解包payload出面() {
        server.expect(requestTo(BASE + IdentitySnapshotClient.SNAPSHOT_PATH))
                .andExpect(method(HttpMethod.GET))
                .andExpect(header(RequestConst.X_API_KEY, Base64Util.encode("inkhub-partner:secret-1")))
                .andRespond(withSuccess(OK_ENVELOPE_JSON, MediaType.APPLICATION_JSON));

        SnapshotRes snapshot = client.fetchSnapshot();

        assertEquals(1730000000000L, snapshot.getWatermark());
        assertEquals(1, snapshot.getUsers().size());
        assertEquals(Long.valueOf(101L), snapshot.getUsers().get(0).getId());
        assertEquals("E2026001", snapshot.getUsers().get(0).getCustomerNumber());
        assertEquals("张三", snapshot.getUsers().get(0).getName());
        assertEquals(1, snapshot.getTenants().size());
        assertEquals(Long.valueOf(7L), snapshot.getTenants().get(0).getId());
        assertEquals("t-7", snapshot.getTenants().get(0).getTenantCode());
        assertEquals("七号租户", snapshot.getTenants().get(0).getTenantName());
        assertEquals(1, snapshot.getUserTenants().size());
        assertEquals(Long.valueOf(101L), snapshot.getUserTenants().get(0).getUserId());
        assertEquals(Long.valueOf(7L), snapshot.getUserTenants().get(0).getTenantId());
        server.verify();
    }

    /** 腿②：非零信封（鉴权失败）→ IllegalStateException 带 status.code＋status.msg */
    @Test
    public void 非零信封_抛异常带statusMsg() {
        server.expect(requestTo(BASE + IdentitySnapshotClient.SNAPSHOT_PATH))
                .andRespond(withSuccess(FAIL_ENVELOPE_JSON, MediaType.APPLICATION_JSON));

        IllegalStateException ex = assertThrows(IllegalStateException.class, client::fetchSnapshot);
        assertTrue("异常须带 status.code: " + ex.getMessage(), ex.getMessage().contains("1000"));
        assertTrue("异常须带 status.msg: " + ex.getMessage(), ex.getMessage().contains("AC_PARTNER_NOT_EXISTED"));
    }

    /** 腿③：code==0 但 payload 为空 → IllegalStateException（空快照与「拉取失败」不可混同） */
    @Test
    public void 成功码但payload为空_抛异常() {
        server.expect(requestTo(BASE + IdentitySnapshotClient.SNAPSHOT_PATH))
                .andRespond(withSuccess(
                        "{\"status\":{\"code\":0,\"msg\":\"SUCCESS\",\"details\":null},\"payload\":null}",
                        MediaType.APPLICATION_JSON));

        assertThrows(IllegalStateException.class, client::fetchSnapshot);
    }

    /** 腿④：裸 Jackson（不经 RestTemplate）反序列化信封面——线格式契约双保险 */
    @Test
    public void 信封JSON_裸Jackson反序列化_字段面() throws Exception {
        IdentitySnapshotClient.Envelope<SnapshotRes> envelope = new ObjectMapper().readValue(
                OK_ENVELOPE_JSON, new TypeReference<IdentitySnapshotClient.Envelope<SnapshotRes>>() {
                });

        assertEquals(0, envelope.getStatus().getCode().intValue());
        assertEquals("SUCCESS", envelope.getStatus().getMsg());
        assertEquals(1730000000000L, envelope.getPayload().getWatermark());
        assertEquals("E2026001", envelope.getPayload().getUsers().get(0).getCustomerNumber());
        assertEquals("t-7", envelope.getPayload().getTenants().get(0).getTenantCode());
        assertEquals(Long.valueOf(7L), envelope.getPayload().getUserTenants().get(0).getTenantId());
    }

    /** 腿⑤：生产 RestTemplate 显式超时（自定义值）——SimpleClientHttpRequestFactory 承载 */
    @Test
    public void 生产RestTemplate_超时显式已设() throws Exception {
        properties.setConnectTimeoutMs(1234);
        properties.setReadTimeoutMs(5678);

        RestTemplate restTemplate = IdentitySnapshotClient.productionRestTemplate(properties);

        assertTrue(restTemplate.getRequestFactory() instanceof SimpleClientHttpRequestFactory);
        assertEquals(1234, timeoutField(restTemplate, "connectTimeout"));
        assertEquals(5678, timeoutField(restTemplate, "readTimeout"));
    }

    /** 腿⑥：生产 RestTemplate 缺省超时 3000/5000（Properties 默认值链路） */
    @Test
    public void 生产RestTemplate_默认超时3000_5000() throws Exception {
        RestTemplate restTemplate = IdentitySnapshotClient.productionRestTemplate(new IdentityMirrorProperties());

        assertEquals(3000, timeoutField(restTemplate, "connectTimeout"));
        assertEquals(5000, timeoutField(restTemplate, "readTimeout"));
    }

    /**
     * 读 SimpleClientHttpRequestFactory 的 connectTimeout/readTimeout（spring-web 5.3 只有
     * setter 无 getter，反射读私有 int 字段——字段名 Spring 2.5 起稳定）。
     */
    private static int timeoutField(RestTemplate restTemplate, String fieldName) throws Exception {
        java.lang.reflect.Field field = SimpleClientHttpRequestFactory.class.getDeclaredField(fieldName);
        field.setAccessible(true);
        return field.getInt(restTemplate.getRequestFactory());
    }
}
