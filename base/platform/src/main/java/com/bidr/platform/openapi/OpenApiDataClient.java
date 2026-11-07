package com.bidr.platform.openapi;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestTemplate;

import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.Collections;

/**
 * Title: OpenApiDataClient
 * Description: 开放口<b>纯传输客户端</b>（方案B 棒4 §2「core 传输」层）——拼 baseUrl+path、
 * 发 GET/POST、按 {@link OpenApiEnvelope} 解信封返回 payload；<b>不感知凭据</b>（凭据由调用方
 * 经 extraHeaders 注入或经 {@link OpenApiAuthProvider} 写头，本类全程不改凭据）。
 *
 * <p><b>分层铁律</b>：platform（零 auth 依赖）← authorization-client（凭据实现）← 场景客户端
 * （identity-mirror 的 OpenApiMenuClient / IdentitySnapshotClient 等）。本类是三层的最底座，
 * 只做 HTTP＋信封，禁引任何 auth 模块类型。</p>
 *
 * <p><b>信封契约</b>：{@code status.code==0} 取 payload 返回；非 0 code 抛
 * {@link OpenApiEnvelopeException}（IllegalStateException 子类，携 code/msg——保
 * IdentitySnapshotClient 既有「非零信封抛 IllegalStateException 带 status.msg」契约）；信封
 * payload 为 null 抛 IllegalStateException（「空数据」与「拉取失败」不可混同）；响应体为空/
 * 反序列化失败抛 IllegalStateException；HTTP 层失败（非 2xx／超时／不可达）由 RestTemplate 抛
 * {@code RestClientException}，由调用方兜。</p>
 *
 * <p>🔴 <b>UTF-8 字节纪律</b>：请求体 {@code writeValueAsBytes}（JSON，UTF-8）；响应体以
 * {@code byte[]} 接收再 {@code new String(bytes, UTF_8)} 显式解码后手工反序列化——不走
 * RestTemplate 的 String 转换器（按响应头 charset 走、缺省 ISO-8859-1 的旧病根，同
 * OAuth2Client 既有纪律）。反序列化用自持宽容 ObjectMapper
 * （FAIL_ON_UNKNOWN_PROPERTIES=false）：服务端信封/负载加字段不炸消费侧。</p>
 *
 * <p><b>超时（挂起防护第一道闸）</b>：生产构造走 {@link SimpleClientHttpRequestFactory} 显式
 * connect/read 超时，缺省 3000/5000（照 IdentitySnapshotClient 先例值）。</p>
 *
 * <p><b>测试缝</b>：{@link RestTemplate} 经 {@link #OpenApiDataClient(String, RestTemplate)}
 * 注入可替换（跨模块公开——消费模块的线格式契约测试用 MockRestServiceServer 绑定注入的
 * RestTemplate，走真 HTTP 消息链）。</p>
 *
 * @author coder (棒4, 方案B 开放口三层沉淀 2026-11-07)
 * @since 2026/11/07
 */
public class OpenApiDataClient {

    /** 缺省连接超时（毫秒）——挂起防护（照 identity-mirror 既有缺省） */
    public static final int DEFAULT_CONNECT_TIMEOUT_MS = 3000;

    /** 缺省读超时（毫秒）——同上 */
    public static final int DEFAULT_READ_TIMEOUT_MS = 5000;

    private final String baseUrl;
    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;

    /** 便利构造：缺省超时 3000/5000 */
    public OpenApiDataClient(String baseUrl) {
        this(baseUrl, DEFAULT_CONNECT_TIMEOUT_MS, DEFAULT_READ_TIMEOUT_MS);
    }

    /**
     * 生产构造：显式 connect/read 超时（SimpleClientHttpRequestFactory 直连构造——勿引重件、
     * 不经容器装配，照 IdentitySnapshotClient 既定裁定）。
     */
    public OpenApiDataClient(String baseUrl, int connectTimeoutMs, int readTimeoutMs) {
        this(baseUrl, productionRestTemplate(connectTimeoutMs, readTimeoutMs));
    }

    /** 测试缝构造：RestTemplate 注入可替换（MockRestServiceServer 绑定形态） */
    public OpenApiDataClient(String baseUrl, RestTemplate restTemplate) {
        this.baseUrl = baseUrl == null ? "" : baseUrl;
        this.restTemplate = restTemplate;
        this.objectMapper = new ObjectMapper()
                .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
    }

    /** 生产 RestTemplate：直连构造＋显式 connect/read 超时（挂起防护） */
    static RestTemplate productionRestTemplate(int connectTimeoutMs, int readTimeoutMs) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(connectTimeoutMs);
        factory.setReadTimeout(readTimeoutMs);
        return new RestTemplate(factory);
    }

    /**
     * GET（解信封返回 payload）。
     *
     * @param path         端点路径（拼在 baseUrl 后，可含查询串）
     * @param extraHeaders 凭据等附加头（本类只透传不改；null＝无附加头）
     * @param type         信封泛型引用（{@code OpenApiEnvelope<T>}）
     * @return payload（信封成功且非 null）
     * @throws OpenApiEnvelopeException           信封非成功码（携 code/msg；IllegalStateException 子类）
     * @throws IllegalStateException              信封 payload 为空／响应体为空／反序列化失败
     * @throws org.springframework.web.client.RestClientException HTTP 失败／超时／不可达
     */
    public <T> T get(String path, HttpHeaders extraHeaders, ParameterizedTypeReference<OpenApiEnvelope<T>> type) {
        return exchange(HttpMethod.GET, path, null, extraHeaders, type);
    }

    /**
     * POST JSON（解信封返回 payload）。请求体以 UTF-8 JSON 字节发出（Content-Type:
     * application/json）。
     *
     * @param path  端点路径
     * @param body  请求体（Jackson 序列化；null＝空 body）
     * @param extraHeaders 凭据等附加头（本类只透传不改；null＝无附加头）
     * @param type  信封泛型引用
     * @return payload（信封成功且非 null）
     * @throws OpenApiEnvelopeException           信封非成功码
     * @throws IllegalStateException              请求体序列化失败／信封 payload 为空／响应体为空／反序列化失败
     * @throws org.springframework.web.client.RestClientException HTTP 失败／超时／不可达
     */
    public <T> T post(String path, Object body, HttpHeaders extraHeaders, ParameterizedTypeReference<OpenApiEnvelope<T>> type) {
        return exchange(HttpMethod.POST, path, body, extraHeaders, type);
    }

    /** GET/POST 公共段：拼 URL、组合头（凭据透传不改）、UTF-8 字节纪律收发、解信封。
     *  URL 以 {@link URI#create(String)} 直达（不经 RestTemplate 的模板再编码——查询串里
     *  已编码的 {@code %XX} 会被二次编码成 {@code %25XX}，双重编码线上必查无此人）：
     *  <b>path/查询参数的 URL 编码责任在调用方</b>（未编码的空格/中文等会 IllegalArgumentException）。 */
    private <T> T exchange(HttpMethod method, String path, Object body, HttpHeaders extraHeaders,
                           ParameterizedTypeReference<OpenApiEnvelope<T>> type) {
        String url = baseUrl + path;
        HttpHeaders headers = new HttpHeaders();
        headers.setAccept(Collections.singletonList(MediaType.APPLICATION_JSON));
        if (extraHeaders != null) {
            headers.putAll(extraHeaders);
        }
        HttpEntity<?> entity;
        if (body != null) {
            headers.setContentType(MediaType.APPLICATION_JSON);
            entity = new HttpEntity<>(writeJson(body), headers);
        } else {
            entity = new HttpEntity<>(headers);
        }
        ResponseEntity<byte[]> response = restTemplate.exchange(URI.create(url), method, entity, byte[].class);
        byte[] bytes = response.getBody();
        if (bytes == null) {
            throw new IllegalStateException("开放口响应体为空: " + url);
        }
        return unwrap(new String(bytes, StandardCharsets.UTF_8), url, type);
    }

    /**
     * 信封解包（两段）：<b>先 readTree 判 status.code，非 0 即抛</b>（不看 payload——服务端
     * 失败信封的 payload 形状无保证，错误形态各异，拿成功负载类型去反序列化必炸在形状上，
     * 对齐 OAuth2Client「先判码后取负载」既有形态）；code==0 才整棵反序列化为泛型负载面，
     * payload null → IllegalStateException。
     */
    private <T> T unwrap(String body, String url, ParameterizedTypeReference<OpenApiEnvelope<T>> type) {
        JsonNode root;
        try {
            root = objectMapper.readTree(body);
        } catch (IOException e) {
            throw new IllegalStateException("开放口响应反序列化失败: " + url + " - " + e.getMessage(), e);
        }
        if (root == null || root.isMissingNode() || root.isNull()) {
            throw new IllegalStateException("开放口响应体为空: " + url);
        }
        JsonNode status = root.get("status");
        JsonNode codeNode = status == null ? null : status.get("code");
        if (codeNode == null || codeNode.isNull()) {
            throw new OpenApiEnvelopeException(null,
                    status == null ? "无 status" : (status.has("msg") && !status.get("msg").isNull()
                            ? status.get("msg").asText() : "无 code"));
        }
        int code = codeNode.asInt();
        if (code != OpenApiEnvelope.SUCCESS_CODE) {
            throw new OpenApiEnvelopeException(code,
                    status.has("msg") && !status.get("msg").isNull() ? status.get("msg").asText() : null);
        }
        OpenApiEnvelope<T> envelope;
        try {
            envelope = objectMapper.readValue(body, objectMapper.getTypeFactory().constructType(type.getType()));
        } catch (IOException e) {
            throw new IllegalStateException("开放口响应反序列化失败: " + url + " - " + e.getMessage(), e);
        }
        T payload = envelope == null ? null : envelope.getPayload();
        if (payload == null) {
            throw new IllegalStateException("开放口信封 payload 为空: " + url);
        }
        return payload;
    }

    /** 请求体 → UTF-8 JSON 字节（Jackson writeValueAsBytes 即 UTF-8） */
    private byte[] writeJson(Object body) {
        try {
            return objectMapper.writeValueAsBytes(body);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("开放口请求体序列化失败: " + e.getMessage(), e);
        }
    }

    /** 配置基地址（调试/日志用；端点路径由各场景客户端自持） */
    public String getBaseUrl() {
        return baseUrl;
    }

    /** 内部 RestTemplate 只读面（跨模块契约测试断言超时承载用；注入只经构造，无 setter） */
    public RestTemplate getRestTemplate() {
        return restTemplate;
    }
}
