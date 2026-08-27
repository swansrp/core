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
import dev.langchain4j.model.output.Response;
import dev.langchain4j.model.output.TokenUsage;
import lombok.extern.slf4j.Slf4j;
import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.Proxy;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Title: RawSseStreamingChatModel
 * Description: 自建 OpenAI 兼容 SSE 流式客户端（绕开 langchain4j 0.33 流式层的两处硬伤）：
 * 直接以 okhttp 发起 stream:true 请求并逐行解析 SSE，同时抽取 delta.content 与
 * delta.reasoning_content——0.33 的 OpenAiStreamingChatModel 对 content 为 null 的
 * delta 直接丢弃，思考型模型思考阶段（仅 reasoning_content）在业务侧完全不可见；
 * 同时绕开 0.33 流式失败路径 InternalOpenAiHelper.removeTokenUsage NPE 吞 onError
 * 的缺陷：非 2xx / 非 SSE 响应一律带网关原始错误体回调 onError，不再有静默挂起。
 * 另设空闲读超时护栏（默认 120s）：网关半挂连接只建连不吐数据时，readLine 若以全量超时（600s）
 * 阻塞，会与门面层闩等待同时到期，先报「未收口」泛化错误且回落降级不触发；空闲护栏让静默挂起
 * 快速以可读错误收口（onError 必达，门面可回落同步模型）。正常推理模型思考期持续产出
 * reasoning_content 增量，数据不断不会误杀。
 * tool_calls 增量片段按 index 拼装回 ToolExecutionRequest，兼容工具循环链路。
 * 配置经 {@link ModelConfigProvider} 按用途取值，签名变化自动重建 HTTP 客户端（热刷新）。
 * 思考强度旋钮：构造期可携 thinking_budget（思考 token 上限，兼容模式顶层参数）——
 * null/非正即最强（不携带该参数，模型默认全功率思考），由业务侧按 Agent 配置传入；
 * 框架只暴露旋钮不内置语义（机制进框架、语义留业务）。
 *
 * @author Sharp
 * @since 2026/8/21
 */
@Slf4j
public class RawSseStreamingChatModel {

    /** SSE 事件流解析回调：reasoning 与 content 分离上抛，思考过程可实时透出 */
    public interface Listener {
        /** 思考阶段增量文本（delta.reasoning_content） */
        void onReasoning(String delta);

        /** 正式应答增量文本（delta.content） */
        void onToken(String delta);

        /** 流式正常收口（[DONE] 或流结束，响应含拼装后的工具调用/正文） */
        void onComplete(Response<AiMessage> response);

        /** HTTP 失败 / 非 SSE 响应 / 网络与解析异常（必带可读原因，绝不静默） */
        void onError(Throwable error);
    }

    private static final MediaType JSON_TYPE = MediaType.get("application/json; charset=utf-8");

    /** 空闲读超时上限（秒）：两次数据到达间隔超此值即判网关挂起快速失败（见类注释护栏说明） */
    private static final long DEFAULT_IDLE_CAP_SECONDS = 120;

    /** 空闲上限（可注入缩小供测试；生产用默认值，总时长仍由 callTimeout 控制） */
    private long idleCapSeconds = DEFAULT_IDLE_CAP_SECONDS;

    /** 本次生效的空闲读超时（秒）：发起时定值，供错误文案引用（护栏触发时说明静默时长门槛） */
    private volatile long idleSeconds = DEFAULT_IDLE_CAP_SECONDS;

    private final ModelConfigProvider provider;
    private final String purpose;
    private final Proxy proxy;
    /** 思考 token 上限（null/非正=最强，不携带 thinking_budget 参数） */
    private final Integer thinkingBudget;
    private final ObjectMapper om = new ObjectMapper();

    /** HTTP 客户端按配置签名缓存，配置热变化时重建（同 Refreshable 系列口径） */
    private volatile String clientSig;
    private volatile OkHttpClient client;

    public RawSseStreamingChatModel(ModelConfigProvider provider, String purpose, Proxy proxy) {
        this(provider, purpose, proxy, null);
    }

    /**
     * @param thinkingBudget 思考强度（思考 token 上限）：null/非正=最强（不携带参数），
     *                       正值写入请求体 thinking_budget，截断模型思考长尾
     */
    public RawSseStreamingChatModel(ModelConfigProvider provider, String purpose, Proxy proxy,
            Integer thinkingBudget) {
        this.provider = provider;
        this.purpose = purpose;
        this.proxy = proxy;
        this.thinkingBudget = thinkingBudget;
    }

    /** 测试注入：缩小空闲读超时上限以快速验证挂起连接的快速失败路径 */
    void setIdleCapSeconds(long secs) {
        this.idleCapSeconds = secs > 0 ? secs : DEFAULT_IDLE_CAP_SECONDS;
    }

    /**
     * 发起流式生成（异步，事件经 listener 回调）。toolSpecs 为 null/空时不携带 tools 参数
     */
    public void generate(List<ChatMessage> messages, List<ToolSpecification> toolSpecs, Listener listener) {
        final Listener sink = listener;
        try {
            String baseUrl = provider.getBaseUrl(purpose);
            String apiKey = provider.getApiKey(purpose, null);
            String modelName = provider.getModelName(purpose);
            long timeoutSecs = provider.getTimeoutSeconds(purpose);
            // 空闲读超时：取总超时与空闲上限的较小值——静默挂起快速失败，不再陪跑满全量超时；
            // 总时长仍由 callTimeout 控制（慢但持续吐数据的长生成不受影响）
            long idleSecs = Math.min(timeoutSecs, idleCapSeconds);
            idleSeconds = idleSecs;
            String sig = baseUrl + "|" + modelName + "|" + timeoutSecs + "|" + idleSecs + "|" + apiKey;
            if (client == null || !sig.equals(clientSig)) {
                client = new OkHttpClient.Builder()
                        .proxy(proxy)
                        .connectTimeout(Duration.ofSeconds(Math.min(timeoutSecs, 30)))
                        .writeTimeout(Duration.ofSeconds(timeoutSecs))
                        .readTimeout(Duration.ofSeconds(idleSecs))
                        // 全调用上限：流式整体时长受控（同 AGENT 用途超时口径）
                        .callTimeout(Duration.ofSeconds(timeoutSecs))
                        .build();
                clientSig = sig;
            }
            String body = buildRequestBody(modelName, messages, toolSpecs);
            String url = trimEnd(baseUrl, '/') + "/chat/completions";
            Request request = new Request.Builder()
                    .url(url)
                    .header("Authorization", "Bearer " + apiKey)
                    .header("Accept", "text/event-stream")
                    .post(RequestBody.create(body, JSON_TYPE))
                    .build();
            client.newCall(request).enqueue(new Callback() {
                @Override
                public void onFailure(Call call, java.io.IOException e) {
                    sink.onError(toError(e));
                }

                @Override
                public void onResponse(Call call, okhttp3.Response response) {
                    try (okhttp3.Response resp = response) {
                        handleResponse(resp, sink);
                    } catch (Exception e) {
                        sink.onError(toError(e));
                    }
                }
            });
        } catch (Exception e) {
            sink.onError(new RuntimeException("LLM SSE 调用发起失败: " + e.getMessage(), e));
        }
    }

    /**
     * 统一错误转译：读超时（空闲护栏触发，okhttp 把 SocketTimeoutException 包成通用
     * IOException("Read timed out")，按异常链类型 + 消息双重识别）给可定位文案，
     * 其余保持原始异常链（connect/callTimeout/解析异常等）
     */
    private RuntimeException toError(Throwable e) {
        Throwable root = e;
        while (root.getCause() != null && root.getCause() != root) {
            root = root.getCause();
        }
        boolean readTimeout = root instanceof java.net.SocketTimeoutException
                || String.valueOf(e.getMessage()).contains("Read timed out");
        String msg = readTimeout
                ? "LLM SSE 空闲读超时（" + idleSeconds + "s 无新数据）：疑似网关挂起或模型停止响应"
                : "LLM SSE 调用异常: " + e.getMessage();
        return new RuntimeException(msg, e);
    }

    /** HTTP 层收口：非 2xx / 非 SSE 内容类型均带原始响应体报错（网关真实错误可见，不再被库层吞没） */
    private void handleResponse(okhttp3.Response resp, Listener sink) throws Exception {
        String contentType = resp.header("Content-Type", "");
        if (!resp.isSuccessful() || contentType == null || !contentType.contains("text/event-stream")) {
            String errBody = resp.body() == null ? "" : resp.body().string();
            sink.onError(new RuntimeException("LLM 网关返回异常响应: HTTP " + resp.code()
                    + (contentType == null || contentType.isEmpty() ? "" : " · Content-Type " + contentType)
                    + " · " + brief(errBody, 500)));
            return;
        }
        parseSse(resp, sink);
    }

    /**
     * SSE 逐行解析：data 行取 JSON delta（content / reasoning_content / tool_calls 增量），
     * [DONE] 或流正常结束时拼装最终响应收口；流被截断（无 [DONE] 且零产出）时报错
     */
    private void parseSse(okhttp3.Response resp, Listener sink) throws Exception {
        if (resp.body() == null) {
            sink.onError(new RuntimeException("LLM SSE 响应无正文体（HTTP " + resp.code() + "）"));
            return;
        }
        StringBuilder content = new StringBuilder();
        StringBuilder reasoning = new StringBuilder();
        Map<Integer, ToolCallAcc> toolCalls = new HashMap<>();
        int[] usage = null;
        boolean doneSeen = false;
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(resp.body().byteStream(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.isEmpty() || line.startsWith(":")) {
                    continue; // SSE 空行 / 注释心跳行
                }
                if (!line.startsWith("data:")) {
                    continue; // event:/id:/retry: 等字段对 chat completions 无意义
                }
                String payload = line.substring(5).trim();
                if (payload.isEmpty()) {
                    continue;
                }
                if ("[DONE]".equals(payload)) {
                    doneSeen = true;
                    break;
                }
                JsonNode chunk;
                try {
                    chunk = om.readTree(payload);
                } catch (Exception e) {
                    log.warn("[{}-SSE] 无法解析的 data 行（已跳过）：{}", purpose, brief(payload, 200));
                    continue;
                }
                JsonNode usageNode = chunk.path("usage");
                if (usageNode.isObject()) {
                    usage = new int[]{usageNode.path("prompt_tokens").asInt(0),
                            usageNode.path("completion_tokens").asInt(0)};
                }
                JsonNode delta = chunk.path("choices").path(0).path("delta");
                if (delta.isMissingNode() || !delta.isObject()) {
                    continue;
                }
                // 思考阶段增量：0.33 库层在此被丢弃，本客户端独立上抛（前端 live 行实时可见）
                String r = textOf(delta, "reasoning_content");
                if (r != null && !r.isEmpty()) {
                    reasoning.append(r);
                    sink.onReasoning(r);
                }
                String c = textOf(delta, "content");
                if (c != null && !c.isEmpty()) {
                    content.append(c);
                    sink.onToken(c);
                }
                JsonNode tcs = delta.path("tool_calls");
                if (tcs.isArray()) {
                    for (JsonNode tc : tcs) {
                        int index = tc.path("index").asInt(0);
                        ToolCallAcc acc = toolCalls.computeIfAbsent(index, k -> new ToolCallAcc());
                        String id = textOf(tc, "id");
                        if (id != null && !id.isEmpty()) {
                            acc.id = id;
                        }
                        JsonNode fn = tc.path("function");
                        String name = textOf(fn, "name");
                        if (name != null && !name.isEmpty()) {
                            acc.name = name;
                        }
                        String args = textOf(fn, "arguments");
                        if (args != null) {
                            acc.arguments.append(args);
                        }
                    }
                }
            }
        }
        TokenUsage tokenUsage = usage == null ? null : new TokenUsage(usage[0], usage[1]);
        if (!toolCalls.isEmpty()) {
            List<ToolExecutionRequest> reqs = new ArrayList<>();
            for (Map.Entry<Integer, ToolCallAcc> e : new java.util.TreeMap<>(toolCalls).entrySet()) {
                ToolCallAcc acc = e.getValue();
                reqs.add(ToolExecutionRequest.builder()
                        .id(acc.id != null ? acc.id : "call_" + e.getKey())
                        .name(acc.name == null ? "" : acc.name)
                        .arguments(acc.arguments.toString())
                        .build());
            }
            sink.onComplete(Response.from(AiMessage.from(reqs), tokenUsage));
            return;
        }
        if (content.length() > 0 || doneSeen) {
            sink.onComplete(Response.from(AiMessage.from(content.toString()), tokenUsage));
            return;
        }
        sink.onError(new RuntimeException("LLM SSE 流提前结束（未见 [DONE] 且无任何产出），思考 "
                + reasoning.length() + " 字已丢弃，请检查网关/模型状态"));
    }

    /** delta/tool_calls 节点取文本字段（缺失或 null 返回 null） */
    private static String textOf(JsonNode node, String field) {
        JsonNode v = node.path(field);
        return v.isTextual() ? v.asText() : null;
    }

    /** 请求体组装：OpenAI chat completions（stream:true；messages 按消息类型映射，tools 由规格转换）。
     *  包级可见供同包测试直验请求体字段（thinking_budget 携带/缺省两态） */
    String buildRequestBody(String modelName, List<ChatMessage> messages,
            List<ToolSpecification> toolSpecs) throws Exception {
        ObjectNode root = om.createObjectNode();
        root.put("model", modelName);
        root.put("stream", true);
        if (thinkingBudget != null && thinkingBudget > 0) {
            root.put("thinking_budget", thinkingBudget);
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
            StringBuilder sb = new StringBuilder();
            for (Content c : ((UserMessage) m).contents()) {
                if (c instanceof TextContent) {
                    sb.append(((TextContent) c).text());
                }
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
                    tc.put("id", req.id());
                    tc.put("type", "function");
                    ObjectNode fn = tc.putObject("function");
                    fn.put("name", req.name());
                    fn.put("arguments", req.arguments() == null ? "" : req.arguments());
                }
            } else {
                n.put("content", am.text() == null ? "" : am.text());
            }
        } else if (m instanceof ToolExecutionResultMessage) {
            ToolExecutionResultMessage t = (ToolExecutionResultMessage) m;
            n.put("role", "tool");
            n.put("tool_call_id", t.id());
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

    /** tool_calls 增量片段累积器（SSE 分片按 index 拼回完整调用） */
    private static class ToolCallAcc {
        String id;
        String name;
        final StringBuilder arguments = new StringBuilder();
    }

    private static String trimEnd(String s, char c) {
        String r = s == null ? "" : s;
        while (r.endsWith(String.valueOf(c))) {
            r = r.substring(0, r.length() - 1);
        }
        return r;
    }

    /** 日志截断：SSE 原文/错误体压空白取头部，防日志膨胀 */
    private static String brief(String s, int max) {
        if (s == null) {
            return "";
        }
        String t = s.replaceAll("\\s+", " ").trim();
        return t.length() <= max ? t : t.substring(0, max) + "…";
    }
}
