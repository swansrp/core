package com.bidr.llm.model;

import com.bidr.llm.provider.ModelConfigProvider;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.agent.tool.ToolParameters;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.Content;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.TextContent;
import dev.langchain4j.data.message.ToolExecutionResultMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.output.FinishReason;
import dev.langchain4j.model.output.Response;
import dev.langchain4j.model.output.TokenUsage;
import lombok.extern.slf4j.Slf4j;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.ResponseBody;

import java.io.IOException;
import java.net.Proxy;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 * Title: RawSyncChatModel
 * Description: 自建同步 OpenAI 兼容客户端（支持 function calling），与 {@link RawSseStreamingChatModel}
 * 同源互补——那条覆盖流式主路径，本类覆盖**同步链路**（典型：{@code ToolAgentRunner} 的工具循环，
 * 它调用的是同步 {@code generate(messages, toolSpecs)}）。
 * <p>
 * 为什么自建：{@code RefreshableChatModel} → langchain4j {@code OpenAiChatModel} 的请求体字段集固定，
 * 网关扩展参数（如思考开关 {@code enable_thinking}）没有入口——langchain4j 0.33 无 {@code customParameters}
 * （官方该能力要 1.2.0-beta8+ / Java 17），builder 只有 {@code customHeaders}，且 openai4j 的
 * {@code ChatCompletionRequest} 为 final 无额外字段槽位。本类自己拼体、自己发请求，把"能不能带扩展参数"
 * 的控制权还给业务。
 * </p>
 * <p>
 * 机制与语义分界（同框架既定口径）：{@link #extraBody} 只负责**透传**，框架不解释任何厂商语义——
 * 思考开关怎么配、配成什么值由业务决定（如 {@code {"enable_thinking": false}}、{@code {"thinking_budget": 8192}}、
 * {@code {"reasoning_effort": "low"}}）。{@code maxTokens} 是"保下限"的既有踩坑（思考型模型在小上限下
 * 思考耗尽 token 会返回空 content）。
 * </p>
 * <p>
 * 与库原生客户端的行为差异（自建收益，也是选它的理由）：
 * <ul>
 *   <li>非 2xx 一律带网关原始响应体抛出，错误不经过库层包装、不被吞没；</li>
 *   <li>4xx 立即失败不重试，5xx/网络异常按指数退避重试（次数由 maxAttempts 定）；</li>
 *   <li>思考 token 数（{@code usage.completion_tokens_details.reasoning_tokens}）经 trace 回调透给业务，
 *       可核对"该关思考的任务确实没思考"（如批量核查任务的成本审计）；</li>
 *   <li>{@code finish_reason} 进 trace 与 {@link Response}（截断 {@code length} 额外打 WARN）——
 *       思考/输出被 max_tokens 掐断是最常见的"返回失败"，必须显式可见而非表现为空内容/坏 JSON；
 *       注意截断不应原样重试（重试还是截断），正解是关思考或按思考基线调预算；</li>
 *   <li>网关未回 {@code tool_calls[].id} 时按序合成 id，避免工具结果回填时协议报错。</li>
 * </ul>
 * </p>
 * <p>
 * 仅支持文本消息：非文本内容（图片等）明确抛异常而非静默丢弃（多模态链路的传输适配另行处理）。
 * 配置同 {@link RawSseStreamingChatModel} 口径：每次调用读 provider，连接参数签名变化自动重建客户端。
 * </p>
 *
 * @author Sharp
 * @since 2026/9/17
 */
@Slf4j
public class RawSyncChatModel implements ChatLanguageModel {

    private static final MediaType JSON_TYPE = MediaType.get("application/json; charset=utf-8");

    /** 网络/服务端错误的退避基数（毫秒）：第 n 次重试前等 base * 2^(n-1) */
    private static final long RETRY_BACKOFF_MS = 1000L;

    private final ModelConfigProvider provider;
    private final String purposeType;
    private final Proxy proxy;

    /** 请求体顶层透传的网关扩展参数（框架不解释语义）；保留字段 model/messages/tools 以核心组装为准 */
    private final Map<String, Object> extraBody;

    /** 生成 token 上限：null/非正=不携带（模型默认），正值写入请求体 max_tokens */
    private final Integer maxTokens;

    /** 单次生成最大尝试次数（含首次）：<=0 视同 1 */
    private final int maxAttempts;

    /** 每次调用一行 trace（模型/思考 token/用量/工具数/耗时），可为 null */
    private final Consumer<String> traceSink;

    private final ObjectMapper om = new ObjectMapper();

    /** HTTP 客户端按连接参数签名缓存，配置热变化时重建（同 RawSse 口径） */
    private volatile String clientSig;
    private volatile OkHttpClient client;

    /** 无附加参数的同步模型（不带扩展参数、不带上限、不重试） */
    public RawSyncChatModel(ModelConfigProvider provider, String purposeType, Proxy proxy) {
        this(provider, purposeType, proxy, null, null, 1, null);
    }

    /**
     * @param extraBody   请求体顶层透传的扩展参数（如思考开关），null/空表示不携带
     * @param maxTokens   生成 token 上限，null/非正表示不携带（模型默认）
     * @param maxAttempts 最大尝试次数（含首次），<=0 视同 1；4xx 不重试
     * @param traceSink   每次调用一行审计轨迹，可为 null
     */
    public RawSyncChatModel(ModelConfigProvider provider, String purposeType, Proxy proxy,
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

    @Override
    public Response<AiMessage> generate(List<ChatMessage> messages) {
        return doGenerate(messages, null);
    }

    @Override
    public Response<AiMessage> generate(List<ChatMessage> messages, List<ToolSpecification> toolSpecifications) {
        return doGenerate(messages, toolSpecifications);
    }

    @Override
    public Response<AiMessage> generate(List<ChatMessage> messages, ToolSpecification toolSpecification) {
        List<ToolSpecification> specs = new ArrayList<>();
        specs.add(toolSpecification);
        return doGenerate(messages, specs);
    }

    /** 带重试的生成：服务端错误/网络异常退避重试，4xx 与业务性失败（空产出等）立即抛出 */
    private Response<AiMessage> doGenerate(List<ChatMessage> messages, List<ToolSpecification> specs) {
        RetryableFailure last = null;
        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            try {
                return callOnce(messages, specs);
            } catch (RetryableFailure e) {
                last = e;
                log.warn("[{}-SYNC] 第 {}/{} 次调用失败（可重试）：{}", purposeType, attempt, maxAttempts,
                        e.getMessage());
                if (attempt < maxAttempts) {
                    try {
                        Thread.sleep(RETRY_BACKOFF_MS * (1L << (attempt - 1)));
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        throw new RuntimeException("LLM 同步调用等待重试被中断", ie);
                    }
                }
            }
        }
        throw last;
    }

    private Response<AiMessage> callOnce(List<ChatMessage> messages, List<ToolSpecification> specs) {
        String baseUrl = provider.getBaseUrl(purposeType);
        String apiKey = provider.getApiKey(purposeType, null);
        String modelName = provider.getModelName(purposeType);
        long timeoutSecs = provider.getTimeoutSeconds(purposeType);
        OkHttpClient http = clientOf(baseUrl, modelName, timeoutSecs, apiKey);

        String body;
        try {
            body = buildRequestBody(modelName, messages, specs);
        } catch (Exception e) {
            throw new IllegalStateException("LLM 同步请求体组装失败: " + e.getMessage(), e);
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
            throw new RetryableFailure("LLM 同步调用网络异常: " + e.getMessage(), e);
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
        JsonNode message = root.path("choices").path(0).path("message");
        String text = message.path("content").asText(null);
        List<ToolExecutionRequest> toolCalls = toolCallsOf(message);
        JsonNode usage = root.path("usage");
        int reasoningTokens = reasoningTokensOf(usage);
        String finish = root.path("choices").path(0).path("finish_reason").asText(null);
        if ("length".equals(finish)) {
            // 截断是"返回失败"的最常见形态（思考/输出超出 max_tokens）：显式 WARN，
            // 提示去查思考开关与预算，而不是让下游拿到空内容/坏 JSON 自己猜
            log.warn("[{}-SYNC] LLM 输出被 max_tokens 截断（finish=length）：优先检查思考开关与 token 预算，"
                    + "截断不应原样重试", purposeType);
        }

        trace(modelName, usage, reasoningTokens, toolCalls.size(), finish, costMs,
                toolCalls.isEmpty() ? null : text);

        if (toolCalls.isEmpty() && !hasText(text)) {
            throw new IllegalStateException("LLM 返回空 content（思考型模型可能被思考耗尽 token）: " + usage);
        }
        // 0.33 的 AiMessage 不允许同时携带正文与工具调用（构造器直接抛），工具轮取工具调用，
        // 模型前言文本经 trace 留存（循环协议要求 ai 消息携带 tool_calls 才能回填结果）
        AiMessage ai = toolCalls.isEmpty() ? AiMessage.from(text) : AiMessage.from(toolCalls);
        return Response.from(ai, tokenUsageOf(usage), finishReasonOf(finish));
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
                    // 同步读超时取全量超时：思考型模型在思考阶段可能长时间无字节，空闲护栏会误杀
                    .readTimeout(Duration.ofSeconds(timeoutSecs))
                    .callTimeout(Duration.ofSeconds(timeoutSecs))
                    .build();
            client = cached;
            clientSig = sig;
        }
        return cached;
    }

    /**
     * 请求体组装：OpenAI chat completions（messages 按类型映射，tools 由规格转换，max_tokens 按上限、
     * extraBody 逐键透传顶层）。包级可见供同包测试直验字段
     */
    String buildRequestBody(String modelName, List<ChatMessage> messages,
                            List<ToolSpecification> toolSpecs) throws Exception {
        ObjectNode root = om.createObjectNode();
        // 扩展参数先写、核心字段后覆盖：调用方误传保留字段时以核心组装为准（防请求被写坏）
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
        for (ChatMessage m : messages) {
            msgs.add(messageNode(m));
        }
        if (toolSpecs != null && !toolSpecs.isEmpty()) {
            ArrayNode tools = root.putArray("tools");
            for (ToolSpecification spec : toolSpecs) {
                ObjectNode tool = tools.addObject();
                tool.put("type", "function");
                ObjectNode fn = tool.putObject("function");
                fn.put("name", spec.name());
                if (spec.description() != null) {
                    fn.put("description", spec.description());
                }
                if (spec.parameters() != null) {
                    fn.set("parameters", parametersNode(spec.parameters()));
                }
            }
        }
        return om.writeValueAsString(root);
    }

    /** langchain4j 消息 → OpenAI 消息 JSON（含 AI 工具调用轮与工具结果回填轮） */
    private ObjectNode messageNode(ChatMessage m) {
        ObjectNode n = om.createObjectNode();
        if (m instanceof SystemMessage) {
            n.put("role", "system");
            n.put("content", ((SystemMessage) m).text());
        } else if (m instanceof UserMessage) {
            UserMessage um = (UserMessage) m;
            StringBuilder sb = new StringBuilder();
            for (Content c : um.contents()) {
                if (!(c instanceof TextContent)) {
                    // 静默丢弃非文本内容会产出"看起来正常的错答案"，宁可显式失败
                    throw new UnsupportedOperationException("同步工具模型仅支持文本消息内容，"
                            + "不支持 " + c.getClass().getSimpleName() + "（多模态链路需单独适配传输）");
                }
                sb.append(((TextContent) c).text());
            }
            n.put("role", "user");
            n.put("content", sb.toString());
        } else if (m instanceof AiMessage) {
            AiMessage am = (AiMessage) m;
            n.put("role", "assistant");
            if (am.hasToolExecutionRequests()) {
                if (am.text() != null && !am.text().isEmpty()) {
                    n.put("content", am.text());
                }
                ArrayNode tcs = n.putArray("tool_calls");
                for (ToolExecutionRequest req : am.toolExecutionRequests()) {
                    ObjectNode tc = tcs.addObject();
                    tc.put("id", req.id() == null ? "" : req.id());
                    tc.put("type", "function");
                    ObjectNode fn = tc.putObject("function");
                    fn.put("name", req.name());
                    fn.put("arguments", req.arguments() == null ? "{}" : req.arguments());
                }
            } else {
                n.put("content", am.text() == null ? "" : am.text());
            }
        } else if (m instanceof ToolExecutionResultMessage) {
            ToolExecutionResultMessage t = (ToolExecutionResultMessage) m;
            n.put("role", "tool");
            n.put("tool_call_id", t.id() == null ? "" : t.id());
            n.put("content", t.text() == null ? "" : t.text());
        } else {
            throw new IllegalArgumentException("不支持的消息类型: " + m.getClass().getSimpleName());
        }
        return n;
    }

    /** langchain4j 0.33 ToolParameters → JSON Schema 节点（properties 本身即 schema 片段，直接序列化） */
    private ObjectNode parametersNode(ToolParameters params) {
        ObjectNode n = om.createObjectNode();
        n.put("type", params.type() == null ? "object" : params.type());
        n.set("properties", om.valueToTree(params.properties()));
        if (params.required() != null && !params.required().isEmpty()) {
            ArrayNode req = n.putArray("required");
            params.required().forEach(req::add);
        }
        return n;
    }

    /** 响应中的工具调用：网关未回 id 时按序合成（回填工具结果需 id 对齐，缺失会触发协议报错） */
    static List<ToolExecutionRequest> toolCallsOf(JsonNode message) {
        List<ToolExecutionRequest> calls = new ArrayList<>();
        JsonNode array = message.path("tool_calls");
        if (!array.isArray()) {
            return calls;
        }
        int index = 0;
        for (JsonNode call : array) {
            String name = call.path("function").path("name").asText("");
            if (name.isEmpty()) {
                continue;
            }
            String id = call.path("id").asText("");
            calls.add(ToolExecutionRequest.builder()
                    .id(id.isEmpty() ? "call_" + index : id)
                    .name(name)
                    .arguments(call.path("function").path("arguments").asText("{}"))
                    .build());
            index++;
        }
        return calls;
    }

    /** 思考 token 数（兼容 details 内嵌与顶层两种网关口径）；无该字段的模型返回 0 */
    private static int reasoningTokensOf(JsonNode usage) {
        JsonNode details = usage.path("completion_tokens_details");
        if (details.path("reasoning_tokens").isNumber()) {
            return details.path("reasoning_tokens").asInt();
        }
        return usage.path("reasoning_tokens").asInt(0);
    }

    /** 网关 finish_reason → langchain4j FinishReason（缺失返回 null，由 Response 自行处理）；
     *  包级可见供测试直验映射 */
    static FinishReason finishReasonOf(String raw) {
        if (raw == null) {
            return null;
        }
        switch (raw) {
            case "stop":
                return FinishReason.STOP;
            case "length":
                return FinishReason.LENGTH;
            case "tool_calls":
            case "function_call":
                return FinishReason.TOOL_EXECUTION;
            case "content_filter":
                return FinishReason.CONTENT_FILTER;
            default:
                return FinishReason.OTHER;
        }
    }

    private static TokenUsage tokenUsageOf(JsonNode usage) {
        if (usage.isMissingNode() || usage.isNull()) {
            return null;
        }
        return new TokenUsage(usage.path("prompt_tokens").asInt(0), usage.path("completion_tokens").asInt(0),
                usage.path("total_tokens").asInt(0));
    }

    /** 每次调用一行审计轨迹（业务可落库核对思考纪律/成本/截断）；preambleText 非空表示工具轮丢弃的模型前言 */
    private void trace(String modelName, JsonNode usage, int reasoningTokens, int toolCount,
                       String finish, long costMs, String preambleText) {
        String line = "model=" + modelName + " tokens=" + usage.path("prompt_tokens").asInt(0)
                + "/" + usage.path("completion_tokens").asInt(0)
                + " 思考tokens=" + reasoningTokens + " 工具=" + toolCount
                + " finish=" + (finish == null ? "unknown" : finish) + " 耗时=" + costMs + "ms";
        log.info("[{}-SYNC] LLM 同步调用完成：{}", purposeType, line);
        if (traceSink != null) {
            traceSink.accept(line + (hasText(preambleText) ? " 前言=" + brief(preambleText, 60) : ""));
        }
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

    /** 日志/错误文案截断：压空白取头部，防日志膨胀 */
    private static String brief(String s, int max) {
        if (s == null) {
            return "";
        }
        String t = s.replaceAll("\\s+", " ").trim();
        return t.length() <= max ? t : t.substring(0, max) + "…";
    }

    /** 可重试失败（5xx / 网络异常 / 空响应体）：退避后重试；4xx 与解析/业务性失败走普通异常直抛 */
    private static class RetryableFailure extends RuntimeException {
        RetryableFailure(String message) {
            super(message);
        }

        RetryableFailure(String message, Throwable cause) {
            super(message, cause);
        }
    }
}