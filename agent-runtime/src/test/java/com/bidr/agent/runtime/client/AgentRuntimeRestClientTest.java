package com.bidr.agent.runtime.client;

import com.bidr.llm.agent.runtime.dto.AgentInfo;
import com.bidr.llm.agent.runtime.dto.SessionInfo;
import com.bidr.agent.runtime.support.TestConfig;
import com.bidr.agent.runtime.support.UpstreamStub;
import com.bidr.kernel.exception.ServiceException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Title: AgentRuntimeRestClientTest
 * Description: REST 客户端口径：snake_case → 驼峰归一、信封校验、D6 错误码映射、上游线格式序列化。
 *
 * @author sharp
 * @since 2026/9/22
 */
class AgentRuntimeRestClientTest {

    private UpstreamStub stub;
    private AgentRuntimeRestClient client;

    @BeforeEach
    void setUp() throws IOException {
        stub = new UpstreamStub();
        client = new AgentRuntimeRestClient(TestConfig.provider(stub.baseUrl()));
    }

    @AfterEach
    void tearDown() {
        stub.close();
    }

    @Test
    @DisplayName("上游 snake_case 归一为驼峰（agent_code → agentCode）")
    void mapsSnakeCaseToCamelCase() {
        stub.onJson("/open/v1/agents", UpstreamStub.envelope(0, "success",
                "[{\"agent_code\":\"epc-demo\",\"agent_name\":\"演示\",\"is_system\":true}]"));

        List<AgentInfo> agents = client.listAgents();

        assertEquals(1, agents.size());
        assertEquals("epc-demo", agents.get(0).getAgentCode());
        assertEquals("演示", agents.get(0).getAgentName());
        assertTrue(agents.get(0).getSystem());
        assertTrue(stub.hasHeaderValueContaining("Bearer sk-rt-unit-test"), "未带上鉴权头");
    }

    @Test
    @DisplayName("会话语义类平台码直通（40402 带进 status.code）")
    void passesThroughSessionMissingCode() {
        stub.onJson("/open/v1/sessions/unknown", UpstreamStub.envelope(40402, "会话不存在或已删除", "null"), 404);

        ServiceException error = assertThrows(ServiceException.class, () -> client.getSession("unknown"));

        assertEquals(40402, error.getErrCode().getErrCode());
        // 码前缀进消息：框架 request 在 showErr=false 时只把 details 抛给前端，前端按 "[<code>] " 解析
        assertEquals("[40402] 会话不存在或已删除", error.getMessage());
    }

    @Test
    @DisplayName("密钥被拒（HTTP 401）→ 内部错误 + 配置指引，不冒充会话语义")
    void mapsKeyRejectedToInternalWithGuidance() {
        // 注：POST 带体时 JDK 遇 401 会抛 HttpRetryException 拿不到信封，故这一类按状态码语义兜底
        stub.onJson("/open/v1/sessions", UpstreamStub.envelope(40101, "Key invalid", "null"), 401);

        ServiceException error = assertThrows(ServiceException.class,
                () -> client.createSession(new HashMap<>()));

        assertEquals(101, error.getErrCode().getErrCode());
        assertTrue(error.getMessage().contains("HTTP 401"), "实际消息：" + error.getMessage());
        assertTrue(error.getMessage().contains("AGENT_RUNTIME_API_KEY"), "实际消息：" + error.getMessage());
    }

    @Test
    @DisplayName("请求体按上游线格式序列化（snake_case）")
    void serializesWireBodyAsSnakeCase() {
        Map<String, Object> body = new HashMap<>(2);
        body.put("file_name", "a.png");
        String json = client.toJson(body);

        assertTrue(json.contains("\"file_name\":\"a.png\""), json);
    }

    @Test
    @DisplayName("会话详情反序列化（含 metadata/group_key）")
    void mapsSessionDetail() {
        stub.onJson("/open/v1/sessions/s-1", UpstreamStub.envelope(0, "success",
                "{\"session_id\":\"s-1\",\"agent_code\":\"epc-demo\",\"name\":\"标题\",\"group_key\":\"epc:1\","
                        + "\"metadata\":{\"k\":\"v\"},\"created_at\":\"2026-09-22T00:00:00Z\"}"));

        SessionInfo session = client.getSession("s-1");

        assertEquals("s-1", session.getSessionId());
        assertEquals("epc:1", session.getGroupKey());
        assertEquals("v", session.getMetadata().get("k"));
    }
}