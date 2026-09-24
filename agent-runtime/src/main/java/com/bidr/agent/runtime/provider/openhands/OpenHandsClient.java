package com.bidr.agent.runtime.provider.openhands;

import com.bidr.agent.runtime.client.AgentRuntimeErrors;
import com.bidr.agent.runtime.config.AgentRuntimeConfigProvider;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.http.converter.StringHttpMessageConverter;
import org.springframework.http.converter.FormHttpMessageConverter;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.ResponseErrorHandler;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriUtils;
import org.springframework.http.client.ClientHttpResponse;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Title: OpenHandsClient
 * Description: OpenHands agent-server 的 REST 薄客户端（v1.49.4 实测口径）。
 * <p>
 * 与 agent-system 的差异都关在这里：无 {@code {code,msg,data}} 信封（成功 2xx + 裸对象，
 * 失败用 HTTP 状态码 + {@code {"detail":…}}），字段是驼峰，会话叫 conversation、事件叫 event。
 * 本类把两侧的**错误语义对齐到同一套平台码**（404→40402、401→按密钥被拒、422→40010），
 * 让上层（relay/前端）感知不到上游换过。
 *
 * @author sharp
 * @since 2026/9/23
 */
@Slf4j
public class OpenHandsClient {

    private final AgentRuntimeConfigProvider config;
    private final RestTemplate rest;
    private final ObjectMapper mapper;

    public OpenHandsClient(AgentRuntimeConfigProvider config) {
        this.config = config;
        this.mapper = new ObjectMapper().configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(config.getConnectTimeoutMs());
        factory.setReadTimeout(config.getIdleTimeoutMs());
        this.rest = new RestTemplate(factory);
        this.rest.setMessageConverters(Arrays.asList(
                new StringHttpMessageConverter(StandardCharsets.UTF_8),
                new MappingJackson2HttpMessageConverter(this.mapper),
                // 附件代收转推走 multipart，需表单转换器（其余调用仍只认 JSON/文本）
                new FormHttpMessageConverter()));
        // 非 2xx 不抛：detail 体要读出来才能映射错误码
        this.rest.setErrorHandler(new ResponseErrorHandler() {
            @Override
            public boolean hasError(ClientHttpResponse response) {
                return false;
            }

            @Override
            public void handleError(ClientHttpResponse response) {
                // 交由 call() 统一判定
            }
        });
    }

    public ObjectMapper mapper() {
        return mapper;
    }

    public String base() {
        return config.requireBaseUrl();
    }

    /**
     * 一次调用：成功返回 JSON 节点；失败按对齐后的平台码抛异常
     *
     * @param method HTTP 方法
     * @param path   绝对路径（不含 host），如 /api/conversations
     * @param body   请求体（可空）
     */
    public JsonNode call(HttpMethod method, String path, Object body) {        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setAccept(Collections.singletonList(MediaType.APPLICATION_JSON));
        String key = config.requireApiKey();
        headers.set("X-Session-API-Key", key);
        long t0 = System.currentTimeMillis();
        ResponseEntity<String> resp;
        try {
            resp = rest.exchange(URI.create(base() + path), method,
                    new HttpEntity<>(body, headers), String.class);
        } catch (RestClientException e) {
            if (AgentRuntimeErrors.isUnauthorized(e)) {
                throw AgentRuntimeErrors.keyRejected(method + " " + path);
            }
            throw AgentRuntimeErrors.of(-1, "Agent 运行时不可达：" + method + " " + path
                    + "（" + e.getMessage() + "）");
        }
        int status = resp.getStatusCode().value();
        String raw = resp.getBody();
        if (status >= 200 && status < 300) {
            if (raw == null || raw.trim().isEmpty()) {
                return mapper.nullNode();
            }
            try {
                return mapper.readTree(raw);
            } catch (Exception e) {
                throw AgentRuntimeErrors.of(-1, "Agent 运行时响应不是合法 JSON（" + method + " " + path + "）");
            }
        }
        throw mapError(status, raw, method, path, t0);
    }

    /**
     * 附件代收转推：{@code POST /api/conversations/{cid}/file/upload?path=<绝对路径>}
     * （multipart 字段名固定为 {@code file}，实测 openapi 定义）。
     *
     * @param absolutePath 沙箱内绝对路径（调用方负责清洗，禁止目录穿越）
     */
    public JsonNode upload(String conversationId, String absolutePath, String fileName, byte[] content) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.MULTIPART_FORM_DATA);
        headers.setAccept(Collections.singletonList(MediaType.APPLICATION_JSON));
        headers.set("X-Session-API-Key", config.requireApiKey());
        ByteArrayResource resource = new ByteArrayResource(content == null ? new byte[0] : content) {
            @Override
            public String getFilename() {
                return fileName;
            }
        };
        MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
        body.add("file", resource);
        String path = "/api/conversations/" + enc(conversationId) + "/file/upload?path=" + enc(absolutePath);
        String url = base() + "/api/conversations/" + enc(conversationId) + "/file/upload"
                + "?path=" + enc(absolutePath);
        ResponseEntity<String> resp;
        try {
            resp = rest.exchange(URI.create(url), HttpMethod.POST, new HttpEntity<>(body, headers), String.class);
        } catch (RestClientException e) {
            if (AgentRuntimeErrors.isUnauthorized(e)) {
                throw AgentRuntimeErrors.keyRejected("POST " + path);
            }
            throw AgentRuntimeErrors.of(-1, "Agent 运行时不可达：POST " + path + "（" + e.getMessage() + "）");
        }
        int status = resp.getStatusCode().value();
        String raw = resp.getBody();
        if (status >= 200 && status < 300) {
            try {
                return raw == null || raw.trim().isEmpty() ? mapper.nullNode() : mapper.readTree(raw);
            } catch (Exception e) {
                throw AgentRuntimeErrors.of(-1, "Agent 运行时响应不是合法 JSON（POST " + path + "）");
            }
        }
        throw mapError(status, raw, HttpMethod.POST, path, System.currentTimeMillis());
    }

    /**
     * 上游 HTTP 状态 → 平台码（对齐 D6，让上层只认一套码）
     */
    private RuntimeException mapError(int status, String raw, HttpMethod method, String path, long t0) {
        String detail = raw == null ? "" : raw;
        try {
            JsonNode node = mapper.readTree(raw);
            if (node.hasNonNull("detail")) {
                detail = node.get("detail").isArray()
                        ? node.get("detail").toString() : node.get("detail").asText();
            }
        } catch (Exception ignored) {
            // 非 JSON：原文当 detail
        }
        log.warn("OpenHands 调用失败 HTTP {} {} {}（{}ms）：{}",
                status, method, path, System.currentTimeMillis() - t0, detail.length() > 200 ? detail.substring(0, 200) : detail);
        if (status == 401 || status == 403) {
            return AgentRuntimeErrors.keyRejected("HTTP " + status + " " + detail);
        }
        if (status == 404) {
            return AgentRuntimeErrors.of(AgentRuntimeErrors.CODE_SESSION_MISSING, detail);
        }
        if (status == 409) {
            return AgentRuntimeErrors.of(AgentRuntimeErrors.CODE_CONFLICT, detail);
        }
        if (status == 422 || status == 400) {
            return AgentRuntimeErrors.of(40010, detail);
        }
        return AgentRuntimeErrors.of(-1, "Agent 运行时返回 HTTP " + status + "：" + detail);
    }

    // ==================== 常用调用 ====================

    public JsonNode get(String path) {
        return call(HttpMethod.GET, path, null);
    }

    public JsonNode post(String path, Object body) {
        return call(HttpMethod.POST, path, body == null ? Collections.emptyMap() : body);
    }

    public JsonNode delete(String path) {
        return call(HttpMethod.DELETE, path, null);
    }

    /**
     * Agent 档案清单（OpenHands 的"Agent"= agent profile：{@code {profiles[], active_agent_profile_id}}）
     */
    public JsonNode agentProfiles() {
        return get("/api/agent-profiles");
    }

    /**
     * 可委派子 agent 类型（只读发现；OpenHands 侧无内置时返回空清单——不是错误）
     */
    public JsonNode subAgents() {
        Map<String, Object> body = new HashMap<>();
        body.put("load_user", true);
        body.put("load_project", true);
        body.put("load_builtin", true);
        return post("/api/sub-agents", body);
    }

    /**
     * 按名字取 agent profile id（建会话必填）：名字命中优先，否则用上游当前激活档案。
     * 两者都没有即未接线（返回 null，由调用方给可诊断的错误）。
     */
    public String resolveAgentProfileId(String profileName) {
        JsonNode profiles = agentProfiles();
        JsonNode list = profiles.path("profiles");
        if (list.isArray()) {
            for (JsonNode profile : list) {
                if (profileName != null && profileName.equals(profile.path("name").asText(null))) {
                    return profile.path("id").asText(null);
                }
            }
        }
        String active = profiles.path("active_agent_profile_id").asText(null);
        if (active != null) {
            return active;
        }
        return list.isArray() && list.size() > 0 ? list.get(0).path("id").asText(null) : null;
    }

    /**
     * 建会话（{@code conversation_id} 可由调用方指定 → 工作目录可与会话同名，天然按会话隔离）
     */
    public JsonNode createConversation(Map<String, Object> body) {
        return post("/api/conversations", body);
    }

    public JsonNode conversation(String conversationId) {
        return get("/api/conversations/" + enc(conversationId));
    }

    public JsonNode deleteConversation(String conversationId) {
        return delete("/api/conversations/" + enc(conversationId));
    }

    /**
     * 中断当前执行（实测：**空闲时也返回 200**，故没有"终态再取消"的 409 需要容忍）
     */
    public void interrupt(String conversationId) {
        post("/api/conversations/" + enc(conversationId) + "/interrupt", null);
    }

    /**
     * 最终答复全文（上游取 FinishAction 或最后一条 agent MessageEvent）
     */
    public String finalResponse(String conversationId) {
        return get("/api/conversations/" + enc(conversationId) + "/agent_final_response")
                .path("response").asText("");
    }

    /**
     * 全量事件（时间升序，按 {@code next_page_id} 翻页；单页上限 100）
     */
    public List<JsonNode> allEvents(String conversationId) {
        List<JsonNode> events = new ArrayList<>();
        String pageId = null;
        do {
            StringBuilder query = new StringBuilder("/api/conversations/").append(enc(conversationId))
                    .append("/events/search?limit=100&sort_order=TIMESTAMP");
            if (pageId != null) {
                query.append("&page_id=").append(enc(pageId));
            }
            JsonNode page = get(query.toString());
            JsonNode items = page.path("items");
            if (items.isArray()) {
                for (JsonNode item : items) {
                    events.add(item);
                }
            }
            pageId = page.path("next_page_id").asText(null);
        } while (pageId != null && !pageId.isEmpty());
        return events;
    }

    /**
     * 会话级 socket 地址（{@code /sockets/session/{cid}}：结构化信封帧，durable 帧自带 {@code seq}）。
     * <p>
     * ⚠️ 不用旧端点 {@code /sockets/events/{cid}}：那个直接推磁盘记录、重放靠时间戳比较。
     * 本端点还支持 {@code ?after_seq=N} 断点续传（上游游标），我方挂流走 REST 前缀归约、
     * 故不带该参数；将来做"属主实例宕机后接管"时，它正是落上游游标的位置。
     */
    public URI sessionSocketUri(String conversationId) {
        String http = base();
        String ws = http.startsWith("https") ? http.replaceFirst("^https", "wss") : http.replaceFirst("^http", "ws");
        return URI.create(ws + "/sockets/session/" + enc(conversationId));
    }

    /**
     * 发起一轮（异步执行，立即返回 {@code {success:true}}）；返回体**无事件 id**，
     * 轮次 id 只能由流里的那条用户 MessageEvent 给出（与历史侧同口径）
     */
    public void postMessage(String conversationId, String content) {
        Map<String, Object> text = new HashMap<>(3);
        text.put("type", "text");
        text.put("text", content);
        Map<String, Object> body = new HashMap<>(3);
        body.put("role", "user");
        body.put("content", Collections.singletonList(text));
        // run 默认 false（只落消息不跑循环），必须显式置真
        body.put("run", true);
        post("/api/conversations/" + enc(conversationId) + "/events", body);
    }

    private static String enc(String segment) {
        return UriUtils.encodePathSegment(segment, StandardCharsets.UTF_8);
    }
}