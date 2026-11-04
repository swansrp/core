package com.bidr.authorization.envelope;

import com.bidr.kernel.config.response.Response;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

/**
 * 统一信封的<b>真序列化</b>契约腿（对账单 #27）：provider 形态用 kernel {@link Response}
 * 的实例序列化产出，而不是手写 JSON 字面量——服务端信封一旦改名/改形（status→code、
 * payload→data、包多一层…），本测试立即红，消费侧（authz client／identity-mirror）不会静默解空。
 *
 * <p>腿清单：</p>
 * <ol>
 *   <li>成功：{@code new Response<>(payload)} 真序列化 → Envelope 反序列化 → code=0 且字段面还原；</li>
 *   <li>失败：{@code new Response<>(ServiceException)} 真序列化 → Envelope 解出非 0 code 且
 *       {@code assertSuccess} 抛 IllegalStateException 带 status.msg；</li>
 *   <li>payload 缺失（服务端 raw null）→ assertSuccess 抛 payload 为空；</li>
 *   <li>失败文案保留调用方 face 前缀（client/mirror 各自文案不被统一改写）。</li>
 * </ol>
 *
 * @author sharp
 * @since 2026-11-04
 */
public class EnvelopeContractTest {

    private final ObjectMapper mapper = new ObjectMapper();

    private Map<String, Object> samplePayload() {
        Map<String, Object> p = new LinkedHashMap<>();
        p.put("allowed", true);
        p.put("version", 42L);
        return p;
    }

    @Test
    public void providerResponseSerializesIntoConsumableEnvelope() throws Exception {
        // provider 侧真实返回类型
        Response<Map<String, Object>> provider = new Response<>(samplePayload());
        String wire = mapper.writeValueAsString(provider);

        // 断言线格式确实是 {status:{...}, payload:...}（不是别的包裹形态）
        assertTrue("线格式必须含 status 段，实际：" + wire, wire.contains("\"status\""));
        assertTrue("线格式必须含 payload 段，实际：" + wire, wire.contains("\"payload\""));

        Envelope<Map<String, Object>> parsed =
                mapper.readValue(wire, new TypeReference<Envelope<Map<String, Object>>>() {
                });
        parsed.assertSuccess("契约腿");
        assertEquals(Integer.valueOf(Envelope.SUCCESS_CODE), parsed.getStatus().getCode());
        assertEquals(42L, ((Number) parsed.getPayload().get("version")).longValue());
        assertEquals(true, parsed.getPayload().get("allowed"));
    }

    @Test
    public void providerFailureEnvelopeSurfacesStatusMsg() throws Exception {
        Response<Object> provider = new Response<>(
                new com.bidr.kernel.exception.ServiceException(
                        com.bidr.kernel.constant.err.ErrCodeSys.PA_DATA_NOT_EXIST));
        String wire = mapper.writeValueAsString(provider);

        Envelope<Object> parsed = mapper.readValue(wire, new TypeReference<Envelope<Object>>() {
        });
        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> parsed.assertSuccess("/authz/check"));
        assertTrue("失败文案必须带调用方 face 前缀，实际：" + ex.getMessage(),
                ex.getMessage().startsWith("/authz/check 返回非成功信封"));
    }

    @Test
    public void missingPayloadRejected() throws Exception {
        String wire = "{\"status\":{\"code\":0,\"msg\":\"SUCCESS\",\"details\":null},\"payload\":null}";
        Envelope<Object> parsed = mapper.readValue(wire, new TypeReference<Envelope<Object>>() {
        });
        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> parsed.assertSuccess("身份快照端点"));
        assertEquals("身份快照端点 信封 payload 为空", ex.getMessage());
    }
}