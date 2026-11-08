package com.bidr.identity.mirror.openapi;

import com.bidr.platform.openapi.OpenApiDataClient;
import com.bidr.platform.openapi.OpenApiEnvelopeException;
import com.bidr.platform.openapi.OpenApiAuthProvider;
import org.junit.Before;
import org.junit.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestTemplate;

import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

/**
 * Title: OpenApiMenuClientEnvelopeContractTest
 * Description: 子系统菜单树场景客户端的<b>线格式契约钉</b>（方案B 棒4）——照
 * {@code IdentitySnapshotClientEnvelopeContractTest} 形态：真实信封 JSON 字面量过
 * MockRestServiceServer 真消息链，钉死「GET /open-api/v1/menu/tree?user={工号} → 统一信封
 * payload → List&lt;MenuNodeRes&gt;（线格式与墨枢 MenuTreeRes 对齐）」。
 * <p>腿清单：</p>
 * <ol>
 *   <li>真实信封 JSON（含线上<b>多余字段</b> menuId/pid/ancestors 等——宽容反序列化吸收；
 *       含中文，无 charset 头——UTF-8 字节纪律）→ fetchTree 解包 → MenuNodeRes 逐字段＋
 *       children 递归断言；user 参数入 URL、凭据头来自注入的 OpenApiAuthProvider；</li>
 *   <li>user 为中文工号 → URL 编码入查询串；</li>
 *   <li>失败信封（鉴权失败形态，details 未知字段在场）→ OpenApiEnvelopeException
 *       （IllegalStateException 子类）携 code/msg；</li>
 *   <li>传输件超时已设（显式构造值——挂起防护第一道闸）。</li>
 * </ol>
 *
 * @author coder (棒4, 方案B 开放口三层沉淀 2026-11-07)
 * @since 2026/11/07
 */
public class OpenApiMenuClientEnvelopeContractTest {

    private static final String BASE = "http://hub:8080";

    /**
     * 真实成功信封 JSON 字面量——payload 线面照墨枢 MenuTreeRes 线上输出：九个消费字段
     * （menuType/path/component/title/icon/isFrame/isCache/key/children）＋线上多出的
     * menuId/pid/grandId/ancestors/clientType/query/appCode（钉「多余字段不炸消费侧」）。
     */
    private static final String OK_ENVELOPE_JSON =
            "{\"status\":{\"code\":0,\"msg\":\"SUCCESS\",\"details\":null},"
                    + "\"payload\":[{"
                    + "\"menuId\":1001,\"pid\":0,\"grandId\":0,\"ancestors\":\"0\","
                    + "\"key\":1001,\"menuType\":0,\"clientType\":\"W\","
                    + "\"title\":\"系统管理\",\"path\":\"/system\",\"component\":\"Layout\","
                    + "\"icon\":\"setting\",\"isFrame\":\"1\",\"isCache\":\"0\","
                    + "\"query\":\"\",\"appCode\":\"epc-ai\","
                    + "\"children\":[{"
                    + "\"menuId\":1002,\"pid\":1001,\"key\":1002,\"menuType\":1,"
                    + "\"title\":\"用户管理\",\"path\":\"user\",\"component\":\"system/user/index\","
                    + "\"icon\":\"user\",\"isFrame\":\"1\",\"isCache\":\"1\","
                    + "\"children\":[]}]}]}";

    /** 非零信封 JSON 字面量（鉴权失败形态——provider 侧 AuthPartner 拒绝） */
    private static final String FAIL_ENVELOPE_JSON =
            "{\"status\":{\"code\":1000,\"msg\":\"AC_PARTNER_NOT_EXISTED\",\"details\":null},"
                    + "\"payload\":{\"errLevel\":\"1\",\"errType\":\"BIZ\",\"errMsg\":\"partner 不存在\"}}";

    private MockRestServiceServer server;
    private OpenApiMenuClient client;

    @Before
    public void wire() {
        RestTemplate restTemplate = new RestTemplate();
        server = MockRestServiceServer.bindTo(restTemplate).build();
        OpenApiDataClient dataClient = new OpenApiDataClient(BASE, restTemplate);
        client = new OpenApiMenuClient(dataClient, fixedCredentialProvider());
    }

    /** 测试用凭据实现（凭据腿归 authorization-client，本测试只钉「注入面被调用、头原样到线」） */
    private static OpenApiAuthProvider fixedCredentialProvider() {
        return headers -> headers.set("x-api-key", "dGVzdC1jcmVkZW50aWFs");
    }

    /** 腿①：真实信封 → fetchTree 解包 → MenuNodeRes 字段面（含 children 递归＋中文）＋user 入 URL＋凭据头到线 */
    @Test
    public void 真实信封_fetchTree解包payload_线格式逐字段出面() {
        server.expect(requestTo(BASE + "/open-api/v1/menu/tree?user=E2026001"))
                .andExpect(method(HttpMethod.GET))
                .andExpect(header("x-api-key", "dGVzdC1jcmVkZW50aWFs"))
                .andRespond(withSuccess(OK_ENVELOPE_JSON, MediaType.APPLICATION_JSON));

        List<MenuNodeRes> tree = client.fetchTree("E2026001");

        assertEquals(1, tree.size());
        MenuNodeRes root = tree.get(0);
        assertEquals(Long.valueOf(1001L), root.getKey());
        assertEquals(Integer.valueOf(0), root.getMenuType());
        assertEquals("系统管理", root.getTitle());
        assertEquals("/system", root.getPath());
        assertEquals("Layout", root.getComponent());
        assertEquals("setting", root.getIcon());
        assertEquals("1", root.getIsFrame());
        assertEquals("0", root.getIsCache());
        assertEquals(1, root.getChildren().size());
        MenuNodeRes child = root.getChildren().get(0);
        assertEquals(Long.valueOf(1002L), child.getKey());
        assertEquals(Integer.valueOf(1), child.getMenuType());
        assertEquals("用户管理", child.getTitle());
        assertEquals("user", child.getPath());
        assertEquals("system/user/index", child.getComponent());
        assertEquals("1", child.getIsCache());
        assertTrue(child.getChildren().isEmpty());
        server.verify();
    }

    /** 腿②：中文工号 → URL 编码入查询串（UTF-8 编码纪律） */
    @Test
    public void 中文工号_URL编码入查询串() {
        server.expect(requestTo(BASE + "/open-api/v1/menu/tree?user=%E5%BC%A0%E4%B8%89"))
                .andRespond(withSuccess(OK_ENVELOPE_JSON, MediaType.APPLICATION_JSON));

        List<MenuNodeRes> tree = client.fetchTree("张三");

        assertEquals(1, tree.size());
        server.verify();
    }

    /** 腿③：失败信封（details 未知字段在场）→ OpenApiEnvelopeException（IllegalStateException 子类）携 code/msg */
    @Test
    public void 失败信封_抛异常携code与msg() {
        server.expect(requestTo(BASE + "/open-api/v1/menu/tree?user=E2026001"))
                .andRespond(withSuccess(FAIL_ENVELOPE_JSON, MediaType.APPLICATION_JSON));

        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> client.fetchTree("E2026001"));
        assertTrue("须是 OpenApiEnvelopeException: " + ex.getClass(),
                ex instanceof OpenApiEnvelopeException);
        assertEquals(Integer.valueOf(1000), ((OpenApiEnvelopeException) ex).getCode());
        assertTrue("异常须带 status.code: " + ex.getMessage(), ex.getMessage().contains("1000"));
        assertTrue("异常须带 status.msg: " + ex.getMessage(), ex.getMessage().contains("AC_PARTNER_NOT_EXISTED"));
    }

    /** 腿④：传输件超时已设（显式构造值——SimpleClientHttpRequestFactory 承载，照契约测试同款反射） */
    @Test
    public void 传输件_超时显式已设() throws Exception {
        OpenApiDataClient dataClient = new OpenApiDataClient(BASE, 2345, 6789);

        assertTrue(dataClient.getRestTemplate().getRequestFactory() instanceof SimpleClientHttpRequestFactory);
        java.lang.reflect.Field connect = SimpleClientHttpRequestFactory.class.getDeclaredField("connectTimeout");
        connect.setAccessible(true);
        java.lang.reflect.Field read = SimpleClientHttpRequestFactory.class.getDeclaredField("readTimeout");
        read.setAccessible(true);
        assertEquals(2345, connect.getInt(dataClient.getRestTemplate().getRequestFactory()));
        assertEquals(6789, read.getInt(dataClient.getRestTemplate().getRequestFactory()));
    }

    /** 腿⑤：authProvider 注入面被调用（凭据由 provider 写头，客户端不自持凭据——三层分工钉） */
    @Test
    public void 凭据由AuthProvider注入_客户端不自持() {
        server.expect(requestTo(BASE + "/open-api/v1/menu/tree?user=E2026001"))
                .andExpect(header("x-api-key", "dGVzdC1jcmVkZW50aWFs"))
                .andRespond(withSuccess(OK_ENVELOPE_JSON, MediaType.APPLICATION_JSON));

        client.fetchTree("E2026001");

        server.verify();
    }
}
