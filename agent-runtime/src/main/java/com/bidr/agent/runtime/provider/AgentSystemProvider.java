package com.bidr.agent.runtime.provider;

import com.bidr.agent.runtime.client.AgentRuntimeErrors;
import com.bidr.agent.runtime.client.AgentRuntimeRestClient;
import com.bidr.agent.runtime.client.AgentRuntimeSseClient;
import com.bidr.agent.runtime.client.Envelope;
import com.bidr.llm.agent.runtime.dto.AgentInfo;
import com.bidr.llm.agent.runtime.dto.CancelResult;
import com.bidr.llm.agent.runtime.dto.DeleteResult;
import com.bidr.llm.agent.runtime.dto.SessionInfo;
import com.bidr.llm.agent.runtime.dto.TurnItem;
import com.bidr.llm.agent.runtime.dto.TurnPage;
import com.bidr.llm.agent.runtime.dto.UploadUrlResult;
import com.bidr.llm.agent.runtime.spi.AgentRuntimeProvider;
import com.bidr.llm.agent.runtime.spi.RuntimeTurnLink;
import com.bidr.llm.agent.runtime.spi.SessionCreateCmd;
import com.bidr.llm.agent.runtime.spi.TurnOpenCmd;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.bidr.agent.runtime.client.AgentRuntimeSseClient.UpstreamStream;
import lombok.extern.slf4j.Slf4j;
import org.springframework.util.StringUtils;
import org.springframework.web.util.UriUtils;

import java.io.UnsupportedEncodingException;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Title: AgentSystemProvider
 * Description: <b>agent-system 开放协议</b>实现（/open/v1，SSE 单轮流）。
 * <p>
 * 该上游的帧与框架规范事件（{@code com.bidr.llm.agent.runtime.event.RuntimeEvents}）恰好同形，
 * 所以流上<b>原样透传、零解析</b>——这是本实现内部的优化，不是 SPI 的对外承诺：
 * 规范事件由框架定义，另一个实现必须自己把上游事件映射成规范事件。
 * 开流前错误（鉴权/会话失效/限流）在这里读 JSON 信封并按 D6 抛业务异常。
 *
 * @author sharp
 * @since 2026/9/23
 */
@Slf4j
public class AgentSystemProvider implements AgentRuntimeProvider {

    public static final String NAME = "agent-system";

    private final AgentRuntimeRestClient rest;
    private final AgentRuntimeSseClient sseClient;
    private final ObjectMapper mapper;

    public AgentSystemProvider(AgentRuntimeRestClient rest, AgentRuntimeSseClient sseClient) {
        this.rest = rest;
        this.sseClient = sseClient;
        this.mapper = new ObjectMapper()
                .setPropertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE)
                .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
    }

    @Override
    public String name() {
        return NAME;
    }

    @Override
    public List<AgentInfo> listAgents() {
        return rest.listAgents();
    }

    @Override
    public SessionInfo createSession(SessionCreateCmd cmd) {
        Map<String, Object> body = new HashMap<>(4);
        body.put("agent_code", cmd.getAgentCode());
        if (StringUtils.hasText(cmd.getTitle())) {
            body.put("name", cmd.getTitle());
        }
        if (StringUtils.hasText(cmd.getGroupKey())) {
            body.put("group_key", cmd.getGroupKey());
        }
        if (cmd.getMetadata() != null && !cmd.getMetadata().isEmpty()) {
            body.put("metadata", cmd.getMetadata());
        }
        return rest.createSession(body);
    }

    @Override
    public SessionInfo validateSession(String sessionId) {
        return rest.getSession(sessionId);
    }

    @Override
    public DeleteResult deleteSession(String sessionId) {
        return rest.deleteSession(sessionId);
    }

    @Override
    public TurnPage history(String sessionId, String before, Integer limit) {
        return rest.listMessages(sessionId, before, limit);
    }

    @Override
    public TurnItem turn(String sessionId, String turnId) {
        return rest.getMessage(sessionId, turnId);
    }

    @Override
    public UploadUrlResult createUploadUrl(String fileName, long fileSize, String mimeType) {
        return rest.createUploadUrl(fileName, fileSize, mimeType);
    }

    @Override
    public CancelResult cancel(String sessionId, String turnId) {
        return rest.cancelMessage(sessionId, turnId);
    }

    @Override
    public RuntimeTurnLink openTurn(TurnOpenCmd cmd) {
        Map<String, Object> body = new HashMap<>(2);
        body.put("content", cmd.getContent());
        body.put("files", cmd.getFiles() == null ? Collections.emptyList() : cmd.getFiles());
        String path = "/sessions/" + encode(cmd.getSessionId()) + "/messages:stream";
        return link(sseClient, "POST", path, rest.toJson(body), cmd.getIdempotencyKey());
    }

    @Override
    public RuntimeTurnLink attachTurn(String sessionId, String turnId) {
        String path = "/sessions/" + encode(sessionId) + "/messages/" + encode(turnId) + "/stream";
        return link(sseClient, "GET", path, null, null);
    }

    /**
     * 同步建链 + 开流前错误映射（成功才返回 link，供 relay 挂 emitter）
     */
    private RuntimeTurnLink link(AgentRuntimeSseClient client, String method, String path,
                                 String jsonBody, String idempotencyKey) {
        UpstreamStream upstream;
        try {
            upstream = client.open(method, path, jsonBody, idempotencyKey);
        } catch (Exception e) {
            log.warn("开上游流失败：{} {}（{}）", method, path, e.getMessage());
            throw AgentRuntimeErrors.of(-1, "Agent 平台流请求失败：" + e.getMessage());
        }
        if (!upstream.isEventStream()) {
            String body = upstream.getErrorBody();
            upstream.close();
            throw preStreamError(body, upstream.getStatus());
        }
        final UpstreamStream stream = upstream;
        return new RuntimeTurnLink() {
            @Override
            public String nextFrame() throws Exception {
                return stream.readFrameJson();
            }

            @Override
            public void close() {
                stream.close();
            }
        };
    }

    /**
     * 开流前响应体 → 业务异常：能解析出信封即用平台码（D6），否则按传输错误处理
     */
    private RuntimeException preStreamError(String body, int status) {
        if (StringUtils.hasText(body)) {
            try {
                Envelope<Object> envelope = mapper.readValue(body,
                        mapper.getTypeFactory().constructParametricType(Envelope.class, Object.class));
                if (envelope != null && envelope.getCode() != null) {
                    return AgentRuntimeErrors.of(envelope);
                }
            } catch (Exception ignored) {
                // 非信封：回落传输错误
            }
        }
        return AgentRuntimeErrors.of(-1, "Agent 平台流请求失败（HTTP " + status + "）");
    }

    private static String encode(String segment) {
        return UriUtils.encodePathSegment(segment, StandardCharsets.UTF_8);
    }
}