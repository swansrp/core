package com.bidr.agent.runtime.client;

import com.bidr.agent.runtime.config.AgentRuntimeConfigProvider;
import com.bidr.agent.runtime.dto.SessionPage;
import com.bidr.llm.agent.runtime.dto.*;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.*;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.http.converter.StringHttpMessageConverter;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.util.StringUtils;
import org.springframework.web.client.ResponseErrorHandler;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;
import org.springframework.web.util.UriUtils;

import java.nio.charset.StandardCharsets;
import java.util.*;

/**
 * Title: AgentRuntimeRestClient
 * Description: 平台开放面（{@code /open/v1}）REST 客户端——只做「发请求 → 校信封 → 反序列化」，
 * 业务语义一律不在此层。上游 JSON 为 snake_case，经专属 ObjectMapper 映射到驼峰 DTO；
 * 信封 code ≠ 0 → {@link AgentRuntimeErrors} 按 D6 抛 {@code ServiceException}。
 * <p>
 * 传输用**自带 RestTemplate**（仅 JSON 转换器 + 显式超时）：不吃框架全局 RestTemplate 的拦截器与
 * XML 转换器口径（避免"没设 JSON 头被 XML 抢"的老坑），也便于整模块下沉时零外部依赖。
 *
 * @author sharp
 * @since 2026/9/22
 */
@Slf4j
public class AgentRuntimeRestClient {

    /**
     * 幂等键请求头（平台口径）
     */
    public static final String HEADER_IDEMPOTENCY_KEY = "X-Idempotency-Key";

    private final AgentRuntimeConfigProvider config;
    private final RestTemplate restTemplate;
    private final ObjectMapper mapper;

    public AgentRuntimeRestClient(AgentRuntimeConfigProvider config) {
        this.config = config;
        this.mapper = new ObjectMapper()
                .setPropertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE)
                .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(config.getConnectTimeoutMs());
        factory.setReadTimeout(config.getIdleTimeoutMs());
        this.restTemplate = new RestTemplate(factory);
        // String 转换器必须在前：本客户端按 String 取原始响应体再自行解信封（否则 Jackson 会尝试把 JSON 解成 String 报错）
        this.restTemplate.setMessageConverters(Arrays.asList(
                new StringHttpMessageConverter(StandardCharsets.UTF_8),
                new MappingJackson2HttpMessageConverter(this.mapper)));
        // 4xx/5xx **不抛**：平台的错误信封就在响应体里，必须读得到（否则会把 40402 这类语义错误吞成"不可达"）
        this.restTemplate.setErrorHandler(new ResponseErrorHandler() {
            @Override
            public boolean hasError(ClientHttpResponse response) {
                return false;
            }

            @Override
            public void handleError(ClientHttpResponse response) {
                // 不做任何处理：状态码与信封交给调用方判定
            }
        });
    }

    public List<AgentInfo> listAgents() {
        return Arrays.asList(get("/agents", null, AgentInfo[].class));
    }

    public SessionInfo createSession(Map<String, Object> body) {
        return post("/sessions", body, null, SessionInfo.class);
    }

    public SessionPage listSessions(String groupKey) {
        Map<String, Object> query = new HashMap<>(2);
        query.put("group_key", groupKey);
        return get("/sessions", query, SessionPage.class);
    }

    public SessionInfo getSession(String sessionId) {
        return get("/sessions/" + encode(sessionId), null, SessionInfo.class);
    }

    public DeleteResult deleteSession(String sessionId) {
        return exchange(HttpMethod.DELETE, "/sessions/" + encode(sessionId), null, null, DeleteResult.class);
    }

    public TurnPage listMessages(String sessionId, String before, Integer limit) {
        Map<String, Object> query = new HashMap<>(3);
        query.put("before", before);
        query.put("limit", limit);
        return get("/sessions/" + encode(sessionId) + "/messages", query, TurnPage.class);
    }

    public TurnItem getMessage(String sessionId, String messageId) {
        return get("/sessions/" + encode(sessionId) + "/messages/" + encode(messageId), null, TurnItem.class);
    }

    public CancelResult cancelMessage(String sessionId, String messageId) {
        return post("/sessions/" + encode(sessionId) + "/messages/" + encode(messageId) + "/cancel",
                null, null, CancelResult.class);
    }

    /**
     * 领附件预签名直传凭据（直传本身不经本平台，也不经本 relay 的 Transport 面）
     */
    public UploadUrlResult createUploadUrl(String fileName, long fileSize, String mimeType) {
        Map<String, Object> body = new HashMap<>(3);
        body.put("file_name", fileName);
        body.put("file_size", fileSize);
        body.put("mime_type", mimeType == null ? "" : mimeType);
        return post("/files/upload-url", body, null, UploadUrlResult.class);
    }

    /**
     * 上游线格式序列化（snake_case）：relay 组装发消息请求体等场合复用同一口径
     */
    public String toJson(Object value) {
        try {
            return mapper.writeValueAsString(value);
        } catch (Exception e) {
            throw new com.bidr.kernel.exception.ServiceException(
                    com.bidr.kernel.constant.err.ErrCodeSys.SYS_ERR_MSG, "序列化请求体失败：" + e.getMessage());
        }
    }

    private <T> T get(String path, Map<String, Object> query, Class<T> type) {
        return exchange(HttpMethod.GET, path, query, null, type);
    }

    private <T> T post(String path, Object body, String idempotencyKey, Class<T> type) {
        return exchange(HttpMethod.POST, path, null, body, idempotencyKey, type);
    }

    private <T> T exchange(HttpMethod method, String path, Map<String, Object> query, Object body, Class<T> type) {
        return exchange(method, path, query, body, null, type);
    }

    /**
     * 统一出口：鉴权头 + JSON 头 + 信封校验 + 反序列化
     */
    private <T> T exchange(HttpMethod method, String path, Map<String, Object> query, Object body,
                          String idempotencyKey, Class<T> type) {
        String url = buildUrl(path, query);
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setAccept(Collections.singletonList(MediaType.APPLICATION_JSON));
        headers.setBearerAuth(config.requireApiKey());
        if (StringUtils.hasText(idempotencyKey)) {
            headers.set(HEADER_IDEMPOTENCY_KEY, idempotencyKey);
        }

        String raw;
        try {
            ResponseEntity<String> response = restTemplate.exchange(
                    url, method, new HttpEntity<>(body, headers), String.class);
            raw = response.getBody();
        } catch (RestClientException e) {
            if (AgentRuntimeErrors.isUnauthorized(e)) {
                throw AgentRuntimeErrors.keyRejected(method + " " + path);
            }
            throw new com.bidr.kernel.exception.ServiceException(
                    com.bidr.kernel.constant.err.ErrCodeSys.SYS_ERR_MSG,
                    "Agent 平台不可达：" + method + " " + path + "（" + e.getMessage() + "）");
        }

        Envelope<T> envelope = parse(raw, type);
        if (!envelope.isSuccess()) {
            throw AgentRuntimeErrors.of(envelope);
        }
        return envelope.getData();
    }

    private <T> Envelope<T> parse(String raw, Class<T> type) {
        if (!StringUtils.hasText(raw)) {
            throw AgentRuntimeErrors.of(-1, "平台响应为空");
        }
        try {
            return mapper.readValue(raw,
                    mapper.getTypeFactory().constructParametricType(Envelope.class, type));
        } catch (Exception e) {
            log.warn("解析平台响应失败：{}", e.getMessage());
            throw AgentRuntimeErrors.of(-1, "平台响应不是合法信封 JSON");
        }
    }

    private String buildUrl(String path, Map<String, Object> query) {
        String base = config.requireBaseUrl() + config.getApiPrefix() + path;
        if (query == null || query.isEmpty()) {
            return base;
        }
        UriComponentsBuilder builder = UriComponentsBuilder.fromHttpUrl(base);
        query.forEach((key, value) -> {
            if (value != null) {
                builder.queryParam(key, value);
            }
        });
        return builder.build(true).toUriString();
    }

    private static String encode(String segment) {
        return UriUtils.encodePathSegment(segment, StandardCharsets.UTF_8);
    }
}