package com.bidr.llm.model;

import com.bidr.llm.provider.ModelConfigProvider;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.extern.slf4j.Slf4j;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.ResponseBody;

import java.io.IOException;
import java.net.Proxy;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 * Title: RawVisionChatModel
 * Description: 自建同步视觉（多模态）客户端，与 {@link RawSyncChatModel} 同源互补。
 * <p>
 * 为什么单独一类：{@link RawSyncChatModel} 明确只支持文本消息（非文本内容显式抛异常），
 * 图片要进请求必须走 OpenAI 兼容的 content 数组形态（{@code image_url/data URI}）——
 * 这条传输适配（RawSync javadoc 里"另行处理"的那部分）就落在本类。典型消费方：
 * 文档图注生成、视觉判读（visual_check 类工具）。
 * </p>
 * <p>
 * 机制与语义分界（同框架既定口径）：本类只负责把"提示 + 图片字节"按协议发出去并取回文本，
 * extraBody 逐键透传不解释语义——思考开关（如 {@code {"enable_thinking": false}}）等
 * 厂商扩展由调用方经 extraBody 决定；模型名/地址/超时经 {@link ModelConfigProvider} 按
 * purpose 每次调用读取，配置热变化自动生效。
 * </p>
 * <p>
 * 行为口径与 {@link RawSyncChatModel} 一致：非 2xx 带原始响应体抛出；4xx 立即失败不重试，
 * 5xx/网络异常按指数退避重试；输出被 max_tokens 截断（finish=length）显式 WARN；
 * 每次调用一行 trace（模型/用量/图片数/耗时）经 traceSink 透给业务。
 * </p>
 *
 * @author Sharp
 * @since 2026/9/20
 */
@Slf4j
public class RawVisionChatModel {

    private static final MediaType JSON_TYPE = MediaType.get("application/json; charset=utf-8");

    private static final long RETRY_BACKOFF_MS = 1000L;

    private final ModelConfigProvider provider;
    private final String purposeType;
    private final Proxy proxy;

    /** 请求体顶层透传的网关扩展参数（框架不解释语义）；保留字段 model/messages 以核心组装为准 */
    private final Map<String, Object> extraBody;

    /** 生成 token 上限：null/非正=不携带（模型默认） */
    private final Integer maxTokens;

    /** 单次生成最大尝试次数（含首次）：<=0 视同 1 */
    private final int maxAttempts;

    /** 每次调用一行 trace，可为 null */
    private final Consumer<String> traceSink;

    private final ObjectMapper om = new ObjectMapper();

    private volatile String clientSig;
    private volatile OkHttpClient client;

    /** 无附加参数的视觉模型（不带扩展参数、不带上限、不重试） */
    public RawVisionChatModel(ModelConfigProvider provider, String purposeType, Proxy proxy) {
        this(provider, purposeType, proxy, null, null, 1, null);
    }

    /**
     * @param extraBody   请求体顶层透传的扩展参数（如思考开关），null/空表示不携带
     * @param maxTokens   生成 token 上限，null/非正表示不携带（模型默认）
     * @param maxAttempts 最大尝试次数（含首次），<=0 视同 1；4xx 不重试
     * @param traceSink   每次调用一行审计轨迹，可为 null
     */
    public RawVisionChatModel(ModelConfigProvider provider, String purposeType, Proxy proxy,
                              Map<String, Object> extraBody, Integer maxTokens, int maxAttempts,
                              Consumer<String> traceSink) {
        this.provider = provider;
        this.purposeType = purposeType;
        this.proxy = proxy;
        this.extraBody = extraBody;
        this.maxTokens = maxTokens;
        this.maxAttempts = maxAttempts > 0 ? maxAttempts : 1;
        this.traceSink = traceSink;
    }

    /**
     * 视觉补全：提示 + 图片 → 模型文本输出。
     *
     * @param systemPrompt 系统提示，可为 null
     * @param userPrompt   用户提示（不可为空）
     * @param images       图片列表，可为 null/空（此时退化为纯文本请求）
     * @return 模型文本输出（思考内容不得混入）
     */
    public String complete(String systemPrompt, String userPrompt, List<VisionImage> images) {
        if (!hasText(userPrompt)) {
            throw new IllegalArgumentException("视觉补全缺用户提示");
        }
        RetryableFailure last = null;
        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            try {
                return callOnce(systemPrompt, userPrompt, images);
            } catch (RetryableFailure e) {
                last = e;
                log.warn("[{}-VISION] 第 {}/{} 次调用失败（可重试）：{}", purposeType, attempt, maxAttempts,
                        e.getMessage());
                if (attempt < maxAttempts) {
                    try {
                        Thread.sleep(RETRY_BACKOFF_MS * (1L << (attempt - 1)));
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        throw new RuntimeException("LLM 视觉调用等待重试被中断", ie);
                    }
                }
            }
        }
        throw last;
    }

    private String callOnce(String systemPrompt, String userPrompt, List<VisionImage> images) {
        String baseUrl = provider.getBaseUrl(purposeType);
        String apiKey = provider.getApiKey(purposeType, null);
        String modelName = provider.getModelName(purposeType);
        long timeoutSecs = provider.getTimeoutSeconds(purposeType);
        OkHttpClient http = clientOf(baseUrl, modelName, timeoutSecs, apiKey);

        String body;
        try {
            body = buildRequestBody(modelName, systemPrompt, userPrompt, images);
        } catch (Exception e) {
            throw new IllegalStateException("LLM 视觉请求体组装失败: " + e.getMessage(), e);
        }
        Request request = new Request.Builder()
                .url(trimEnd(baseUrl, '/') + "/chat/completions")
                .header("Authorization", "Bearer " + apiKey)
                .post(RequestBody.create(body, JSON_TYPE))
                .build();

        long startMs = System.currentTimeMillis();
        String raw;
        int code;
        try (okhttp3.Response resp = http.newCall(request).execute()) {
            code = resp.code();
            ResponseBody respBody = resp.body();
            raw = respBody == null ? "" : respBody.string();
        } catch (IOException e) {
            throw new RetryableFailure("LLM 视觉调用网络异常: " + e.getMessage(), e);
        }
        long costMs = System.currentTimeMillis() - startMs;

        if (code >= 400 && code < 500) {
            throw new IllegalStateException("LLM 网关返回 HTTP " + code + "（请求侧错误，不重试）："
                    + brief(raw, 500));
        }
        if (code < 200 || code >= 300) {
            throw new RetryableFailure("LLM 网关返回 HTTP " + code + "：" + brief(raw, 500));
        }

        JsonNode root = parse(raw);
        if (root.path("error").isObject()) {
            throw new IllegalStateException("LLM 网关返回错误: " + root.path("error"));
        }
        String text = root.path("choices").path(0).path("message").path("content").asText("");
        String finish = root.path("choices").path(0).path("finish_reason").asText(null);
        JsonNode usage = root.path("usage");
        if ("length".equals(finish)) {
            log.warn("[{}-VISION] LLM 输出被 max_tokens 截断（finish=length）：检查 token 预算，"
                    + "截断不应原样重试", purposeType);
        }

        int imageCount = images == null ? 0 : images.size();
        String line = "model=" + modelName + " tokens=" + usage.path("prompt_tokens").asInt(0)
                + "/" + usage.path("completion_tokens").asInt(0)
                + " 图片=" + imageCount + " finish=" + (finish == null ? "unknown" : finish)
                + " 耗时=" + costMs + "ms";
        log.info("[{}-VISION] LLM 视觉调用完成：{}", purposeType, line);
        if (traceSink != null) {
            traceSink.accept(line);
        }

        if (!hasText(text)) {
            throw new IllegalStateException("LLM 视觉调用返回空 content: " + usage);
        }
        return text;
    }

    /** 连接参数签名（含 Key）：变化即重建客户端，配置改了下次调用生效 */
    private OkHttpClient clientOf(String baseUrl, String modelName, long timeoutSecs, String apiKey) {
        String sig = baseUrl + "|" + modelName + "|" + timeoutSecs + "|" + apiKey;
        OkHttpClient cached = client;
        if (cached == null || !sig.equals(clientSig)) {
            cached = new OkHttpClient.Builder()
                    .proxy(proxy)
                    .connectTimeout(Duration.ofSeconds(Math.min(timeoutSecs, 30)))
                    .writeTimeout(Duration.ofSeconds(timeoutSecs))
                    .readTimeout(Duration.ofSeconds(timeoutSecs))
                    .callTimeout(Duration.ofSeconds(timeoutSecs))
                    .build();
            client = cached;
            clientSig = sig;
        }
        return cached;
    }

    /**
     * 请求体组装：带图时 user content 为数组（text + image_url/data URI），无图退化为纯文本。
     * extraBody 先写、核心字段后覆盖（防调用方误传保留字段写坏请求）。包级可见供同包测试直验。
     */
    String buildRequestBody(String modelName, String systemPrompt, String userPrompt,
                            List<VisionImage> images) throws Exception {
        ObjectNode root = om.createObjectNode();
        if (extraBody != null) {
            for (Map.Entry<String, Object> e : extraBody.entrySet()) {
                if (e.getKey() != null && e.getValue() != null) {
                    root.set(e.getKey(), om.valueToTree(e.getValue()));
                }
            }
        }
        root.put("model", modelName);
        if (maxTokens != null && maxTokens > 0) {
            root.put("max_tokens", maxTokens);
        }
        ArrayNode msgs = root.putArray("messages");
        if (hasText(systemPrompt)) {
            ObjectNode system = msgs.addObject();
            system.put("role", "system");
            system.put("content", systemPrompt);
        }
        ObjectNode user = msgs.addObject();
        user.put("role", "user");
        if (images == null || images.isEmpty()) {
            user.put("content", userPrompt);
        } else {
            ArrayNode content = user.putArray("content");
            ObjectNode text = content.addObject();
            text.put("type", "text");
            text.put("text", userPrompt);
            for (VisionImage image : images) {
                ObjectNode part = content.addObject();
                part.put("type", "image_url");
                ObjectNode url = part.putObject("image_url");
                url.put("url", "data:" + image.mime() + ";base64," + image.base64());
            }
        }
        return om.writeValueAsString(root);
    }

    private JsonNode parse(String raw) {
        if (!hasText(raw)) {
            throw new RetryableFailure("LLM 网关无响应体");
        }
        try {
            return om.readTree(raw);
        } catch (Exception e) {
            throw new IllegalStateException("LLM 网关响应非 JSON: " + brief(raw, 300), e);
        }
    }

    private static boolean hasText(String s) {
        return s != null && !s.trim().isEmpty();
    }

    private static String trimEnd(String s, char c) {
        String r = s == null ? "" : s;
        while (r.endsWith(String.valueOf(c))) {
            r = r.substring(0, r.length() - 1);
        }
        return r;
    }

    private static String brief(String s, int max) {
        if (s == null) {
            return "";
        }
        String t = s.replaceAll("\\s+", " ").trim();
        return t.length() <= max ? t : t.substring(0, max) + "…";
    }

    /** 可重试失败（5xx / 网络异常 / 空响应体）：退避后重试；4xx 与解析/业务性失败直抛 */
    private static class RetryableFailure extends RuntimeException {
        RetryableFailure(String message) {
            super(message);
        }

        RetryableFailure(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
