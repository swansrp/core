package com.bidr.llm.flow.executor;

import com.bidr.llm.flow.FlowContext;
import com.bidr.llm.flow.FlowEngine;
import com.bidr.llm.flow.FlowGraph;
import com.bidr.llm.flow.FlowNodeMeta;
import com.bidr.llm.flow.FlowNodeMeta.ConfigField;
import com.bidr.llm.flow.LlmHistoryMessage;
import com.bidr.llm.model.RefreshableChatModel;
import com.bidr.llm.model.RefreshableStreamingChatModel;
import com.bidr.llm.provider.ModelConfigProvider;
import com.bidr.llm.sse.SseEventSender;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.StreamingResponseHandler;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.chat.StreamingChatLanguageModel;
import dev.langchain4j.model.output.Response;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.net.InetSocketAddress;
import java.net.Proxy;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Title: LlmNodeExecutor
 * Description: llm 结点——模板渲染（{@code {{var}}} 取变量池）后调用大模型：
 * <ul>
 *     <li>template：提示词模板（提示词入库的载体，前端画布可编辑）；</li>
 *     <li>templateVar：可选，变量名——该变量有值时优先作为模板全文（前端调试后门动态注入）；</li>
 *     <li>role：模板文本的角色 user/system（默认 user）；</li>
 *     <li>includeHistory：是否按消息级追加对话历史（默认 false，历史也可在模板内文本渲染）；</li>
 *     <li>stream：true 时流式调用，delta 直推 SSE 并挂起，模型回调线程 resume 续跑（默认 false）；</li>
 *     <li>userVar：末尾追加的 user 消息变量名（默认 question，空值不追加）；</li>
 *     <li>outputVar：模型全文写入的变量名（默认 llmAnswer）；</li>
 *     <li>baseUrl/apiKey/modelName：结点级模型覆盖，任一配置即用覆盖组合发起调用，
 *         未覆盖字段实时继承系统默认（数据库系统参数 {@code LlmParam} 优先，回落 llm.* yaml）——
 *         同一链路可按结点切换模型/密钥/端点。</li>
 * </ul>
 * 历史消息元素实现 {@link LlmHistoryMessage} 即可被识别（业务侧零转换）。
 * 非流式且无历史时退化为单串调用（模型只收到一条 user 消息，路由类链路路径）。
 *
 * @author Sharp
 * @since 2026/8/16
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class LlmNodeExecutor implements FlowNodeExecutor {

    /**
     * 携带的对话历史上限（约5轮 user/assistant）
     */
    private static final int MAX_HISTORY_ITEMS = 10;

    /**
     * 模板占位符：{{var}}，变量缺失渲染为空串
     */
    private static final Pattern VAR_PATTERN = Pattern.compile("\\{\\{(\\w+)\\}\\}");

    private final ObjectProvider<ChatLanguageModel> modelProvider;

    private final ObjectProvider<StreamingChatLanguageModel> streamingModelProvider;

    /**
     * 引擎经 ObjectProvider 延迟引用（引擎构造注入全部执行器，直接依赖会循环）
     */
    private final ObjectProvider<FlowEngine> engineProvider;

    /**
     * 结点级覆盖的继承基座：全局 ModelConfigProvider（数据库系统参数优先的默认配置），空覆盖项实时继承
     */
    private final ObjectProvider<ModelConfigProvider> configProviderProvider;

    /**
     * 结点级覆盖模型的构建参数，与全局默认模型保持一致（llm.chat.max-attempts / llm.proxy.*）
     */
    @Value("${llm.chat.max-attempts:1}")
    private int maxAttempts;
    @Value("${llm.proxy.enable:false}")
    private boolean proxyEnable;
    @Value("${llm.proxy.host:}")
    private String proxyHost;
    @Value("${llm.proxy.port:0}")
    private int proxyPort;

    /**
     * 结点级覆盖模型缓存：key = baseUrl|apiKey|modelName（graph 配置变化即新 key 重建），
     * 内层 Refreshable 包装令继承的系统默认值热刷新
     */
    private final Map<String, RefreshableChatModel> chatOverrides = new ConcurrentHashMap<>();

    /**
     * 结点级覆盖流式模型缓存，语义同 {@link #chatOverrides}
     */
    private final Map<String, RefreshableStreamingChatModel> streamingOverrides = new ConcurrentHashMap<>();

    /**
     * 结点级覆盖模型组合上限：超出整体清空重建（组合来自 graph 静态配置，量级有限）
     */
    private static final int MAX_OVERRIDE_MODELS = 64;

    @Override
    public String type() {
        return "llm";
    }

    /**
     * 工作台元数据：十个配置项与画布属性面板一一对应，defaultValue 即画布新增结点的初始 config
     */
    @Override
    public FlowNodeMeta nodeMeta() {
        FlowNodeMeta meta = FlowNodeMeta.of(type(), type(), "大模型调用：渲染模板生成提示词");
        meta.setFields(Arrays.asList(
                ConfigField.textarea("template", "提示词模板", "双花括号引用变量，可用占位符见下方列表", 14),
                ConfigField.text("templateVar", "模板变量名", "空=template 直存"),
                ConfigField.select("role", "消息角色",
                                new FlowNodeMeta.Option("user", "user"), new FlowNodeMeta.Option("system", "system"))
                        .defaultValue("user"),
                ConfigField.switchField("includeHistory", "携带对话历史", "把 history 变量编入对话上下文").defaultValue(false),
                ConfigField.switchField("stream", "流式输出", "ask 链勾选：回答经 SSE 逐字下发").defaultValue(true),
                ConfigField.text("userVar", "用户变量名", "空=仅模板内容"),
                ConfigField.text("outputVar", "输出变量名", "默认 answer").defaultValue("answer"),
                ConfigField.text("baseUrl", "服务地址", "空=系统默认（系统参数优先）"),
                ConfigField.text("apiKey", "密钥", "空=系统默认；配置后此结点用独立密钥"),
                ConfigField.text("modelName", "模型名", "空=系统默认；按结点切换模型")
        ));
        return meta;
    }

    @Override
    public boolean execute(FlowGraph.FlowNode node, FlowContext context) {
        Map<String, Object> config = node.getConfig();
        String rendered = renderTemplate(resolveTemplate(config, context), context);
        // 轨迹调试变量：渲染后提示词全文（引擎埋点读取构建 detail 后清除）
        context.setVariable(FlowContext.TRACE_LLM_PROMPT, rendered);
        boolean stream = boolConfig(config, "stream", false);
        boolean includeHistory = boolConfig(config, "includeHistory", false);
        String role = strConfig(config, "role", "user");
        String userVar = strConfig(config, "userVar", "question");
        String outputVar = strConfig(config, "outputVar", "llmAnswer");
        if (stream) {
            startStreaming(node, context, rendered, role, includeHistory, userVar, outputVar);
            return false;
        }
        ChatLanguageModel model = resolveModel(config);
        String answer;
        if (includeHistory || "system".equals(role)) {
            Response<AiMessage> response = model
                    .generate(buildMessages(rendered, role, includeHistory, userVar, context));
            answer = response.content() == null ? "" : response.content().text();
        } else {
            // 路由类链路路径：退化为单串调用（模型只收到一条 user 消息）
            answer = model.generate(rendered);
        }
        context.setVariable(outputVar, answer);
        context.setVariable(FlowContext.TRACE_LLM_ANSWER, answer);
        return true;
    }

    /**
     * 流式调用：delta 直推 SSE；onComplete 写输出变量后由引擎续跑，onError 发 error 事件终止链路
     */
    private void startStreaming(FlowGraph.FlowNode node, FlowContext context, String rendered,
                                String role, boolean includeHistory, String userVar, String outputVar) {
        SseEventSender sender = context.getSseSender();
        if (sender == null) {
            throw new IllegalStateException("stream=true 的 llm 结点需要 SSE 上下文（流式链路）");
        }
        StringBuilder buffer = new StringBuilder();
        String nodeId = node.getId();
        resolveStreamingModel(node.getConfig()).generate(
                buildMessages(rendered, role, includeHistory, userVar, context),
                new StreamingResponseHandler<AiMessage>() {
                    @Override
                    public void onNext(String token) {
                        if (token == null) {
                            return;
                        }
                        synchronized (buffer) {
                            buffer.append(token);
                        }
                        sender.send(SseEventSender.EVENT_DELTA, token);
                    }

                    @Override
                    public void onComplete(Response<AiMessage> response) {
                        AiMessage message = response == null ? null : response.content();
                        String finalText = message == null ? null : message.text();
                        String fullContent;
                        synchronized (buffer) {
                            // onComplete 未带全文时以累积片段兜底
                            fullContent = StringUtils.hasText(finalText) ? finalText : buffer.toString();
                        }
                        context.setVariable(outputVar, fullContent);
                        // 轨迹调试变量：模型回答全文（resume 时引擎补记到挂起结点的 detail）
                        context.setVariable(FlowContext.TRACE_LLM_ANSWER, fullContent);
                        engineProvider.getObject().resume(nodeId, context);
                    }

                    @Override
                    public void onError(Throwable error) {
                        log.warn("流程流式生成失败", error);
                        String message = error == null ? "生成失败" : String.valueOf(error.getMessage());
                        sender.send(SseEventSender.EVENT_ERROR, message);
                        sender.complete();
                        // 轨迹收尾：挂起结点补记失败原因，整条轨迹标记 error
                        engineProvider.getObject().markTraceError(context, nodeId, message);
                    }
                });
    }

    /**
     * 双模板来源：templateVar 变量有值时优先（调试后门），否则用 config.template
     */
    private String resolveTemplate(Map<String, Object> config, FlowContext context) {
        String templateVar = strConfig(config, "templateVar", "");
        if (StringUtils.hasText(templateVar)) {
            String override = context.getString(templateVar);
            if (StringUtils.hasText(override)) {
                return override;
            }
        }
        return strConfig(config, "template", "");
    }

    /**
     * 消息组装：模板文本按 role 入列 → 历史（includeHistory）→ userVar 追加当前问题
     */
    private List<ChatMessage> buildMessages(String rendered, String role, boolean includeHistory,
                                            String userVar, FlowContext context) {
        List<ChatMessage> messages = new ArrayList<>();
        if ("system".equals(role)) {
            messages.add(SystemMessage.from(rendered));
        } else {
            messages.add(UserMessage.from(rendered));
        }
        if (includeHistory) {
            appendHistory(messages, context.get("history", List.class));
        }
        String question = context.getString(userVar);
        if (StringUtils.hasText(question)) {
            messages.add(UserMessage.from(question));
        }
        return messages;
    }

    /**
     * 只保留 user/assistant 正文（{@link LlmHistoryMessage} 元素），其余角色静默丢弃
     */
    private void appendHistory(List<ChatMessage> messages, List<?> history) {
        if (history == null || history.isEmpty()) {
            return;
        }
        int from = Math.max(0, history.size() - MAX_HISTORY_ITEMS);
        for (Object item : history.subList(from, history.size())) {
            if (!(item instanceof LlmHistoryMessage)) {
                continue;
            }
            LlmHistoryMessage historyItem = (LlmHistoryMessage) item;
            if ("user".equals(historyItem.getRole())) {
                messages.add(UserMessage.from(historyItem.getContent()));
            } else if ("assistant".equals(historyItem.getRole())) {
                messages.add(AiMessage.from(historyItem.getContent()));
            }
        }
    }

    /**
     * 模板渲染：{{var}} 替换为变量池取值（缺失为空串，渲染不中断）
     */
    private String renderTemplate(String template, FlowContext context) {
        if (!StringUtils.hasText(template)) {
            return "";
        }
        Matcher matcher = VAR_PATTERN.matcher(template);
        StringBuffer rendered = new StringBuffer();
        while (matcher.find()) {
            matcher.appendReplacement(rendered, Matcher.quoteReplacement(renderVariable(matcher.group(1), context)));
        }
        matcher.appendTail(rendered);
        return rendered.toString();
    }

    /**
     * 变量渲染：对话列表渲染为多行文本，其余取字符串值
     */
    private String renderVariable(String name, FlowContext context) {
        Object value = context.getVariable(name);
        if (value == null) {
            return "";
        }
        if (value instanceof List) {
            return renderHistoryText((List<?>) value);
        }
        return String.valueOf(value);
    }

    /**
     * 历史文本："用户: …／助手: …"多行，空列表渲染为（无），供模板内联
     */
    private String renderHistoryText(List<?> items) {
        if (items.isEmpty()) {
            return "（无）";
        }
        StringBuilder text = new StringBuilder();
        for (Object item : items) {
            if (item instanceof LlmHistoryMessage) {
                LlmHistoryMessage historyItem = (LlmHistoryMessage) item;
                text.append("user".equals(historyItem.getRole()) ? "用户" : "助手")
                        .append(": ").append(historyItem.getContent()).append('\n');
            } else if (item != null) {
                text.append(String.valueOf(item)).append('\n');
            }
        }
        return text.toString().trim();
    }

    /**
     * 取应用中唯一的非流式模型 Bean；装配期不强求存在，调用时缺失则报错提示
     */
    private ChatLanguageModel requireModel() {
        ChatLanguageModel model = modelProvider.getIfUnique();
        if (model == null) {
            throw new IllegalStateException("未找到唯一的 ChatLanguageModel Bean，请检查 llm 模块配置");
        }
        return model;
    }

    /**
     * 取应用中唯一的流式模型 Bean；装配期不强求存在，调用时缺失则报错提示
     */
    private StreamingChatLanguageModel requireStreamingModel() {
        StreamingChatLanguageModel model = streamingModelProvider.getIfUnique();
        if (model == null) {
            throw new IllegalStateException("未找到唯一的 StreamingChatLanguageModel Bean，请检查 llm 模块配置");
        }
        return model;
    }

    /**
     * 结点级模型解析：baseUrl/apiKey/modelName 任一配置即按覆盖组合取/建模型（缓存），
     * 三项全空走全局默认 Bean
     */
    private ChatLanguageModel resolveModel(Map<String, Object> config) {
        String overrideKey = endpointOverrideKey(config);
        if (overrideKey == null) {
            return requireModel();
        }
        if (chatOverrides.size() >= MAX_OVERRIDE_MODELS) {
            chatOverrides.clear();
        }
        return chatOverrides.computeIfAbsent(overrideKey, key -> {
            logOverrideBuild(config);
            return new RefreshableChatModel(nodeOverrideProvider(config), "NODE", maxAttempts, buildProxy(), () -> null);
        });
    }

    /**
     * 结点级流式模型解析，语义同 {@link #resolveModel(Map)}
     */
    private StreamingChatLanguageModel resolveStreamingModel(Map<String, Object> config) {
        String overrideKey = endpointOverrideKey(config);
        if (overrideKey == null) {
            return requireStreamingModel();
        }
        if (streamingOverrides.size() >= MAX_OVERRIDE_MODELS) {
            streamingOverrides.clear();
        }
        return streamingOverrides.computeIfAbsent(overrideKey, key -> {
            logOverrideBuild(config);
            return new RefreshableStreamingChatModel(nodeOverrideProvider(config), "NODE", buildProxy(), () -> null);
        });
    }

    /**
     * 覆盖组合缓存键：baseUrl|apiKey|modelName，三项全空返回 null（无覆盖，走全局默认）
     */
    private String endpointOverrideKey(Map<String, Object> config) {
        String baseUrl = strConfig(config, "baseUrl", "");
        String apiKey = strConfig(config, "apiKey", "");
        String modelName = strConfig(config, "modelName", "");
        if (!StringUtils.hasText(baseUrl) && !StringUtils.hasText(apiKey) && !StringUtils.hasText(modelName)) {
            return null;
        }
        return baseUrl + "|" + apiKey + "|" + modelName;
    }

    /**
     * 覆盖组合首次构建日志：modelName/baseUrl 明文、apiKey 只报是否单独配置（不落密钥），空项标注继承语义，
     * 排障时可据此确认结点实际生效的模型组合（稳态走缓存不重复打印）
     */
    private void logOverrideBuild(Map<String, Object> config) {
        String baseUrl = strConfig(config, "baseUrl", "");
        String apiKey = strConfig(config, "apiKey", "");
        String modelName = strConfig(config, "modelName", "");
        log.info("llm 结点覆盖模型构建：modelName={}，baseUrl={}，apiKey={}",
                StringUtils.hasText(modelName) ? modelName : "(继承默认)",
                StringUtils.hasText(baseUrl) ? baseUrl : "(继承默认)",
                StringUtils.hasText(apiKey) ? "(已单独配置)" : "(继承默认)");
    }

    /**
     * 结点级覆盖 Provider：覆盖项非空即替换，空项实时继承全局默认（含数据库参数热刷新）
     */
    private ModelConfigProvider nodeOverrideProvider(Map<String, Object> config) {
        return new NodeOverrideConfigProvider(configProviderProvider,
                strConfig(config, "baseUrl", ""), strConfig(config, "apiKey", ""), strConfig(config, "modelName", ""));
    }

    /**
     * 结点级覆盖模型的 HTTP 代理（与 LlmDefaultAutoConfiguration 的全局默认保持一致）
     */
    private Proxy buildProxy() {
        if (!proxyEnable || !StringUtils.hasText(proxyHost) || proxyPort <= 0) {
            return null;
        }
        return new Proxy(Proxy.Type.HTTP, new InetSocketAddress(proxyHost, proxyPort));
    }

    /**
     * Title: NodeOverrideConfigProvider
     * Description: 结点级覆盖配置——覆盖项（baseUrl/apiKey/modelName）非空即替换，
     * 空项与超时实时读全局 Provider：继承的系统默认（数据库 {@code LlmParam} 优先 → llm.* yaml）
     * 变化时经 Refreshable 包装器自动重建底层模型，结点覆盖同样热刷新。
     *
     * @author Sharp
     * @since 2026/8/16
     */
    private static class NodeOverrideConfigProvider implements ModelConfigProvider {

        private final ObjectProvider<ModelConfigProvider> baseProvider;
        private final String baseUrl;
        private final String apiKey;
        private final String modelName;

        NodeOverrideConfigProvider(ObjectProvider<ModelConfigProvider> baseProvider,
                                  String baseUrl, String apiKey, String modelName) {
            this.baseProvider = baseProvider;
            this.baseUrl = baseUrl;
            this.apiKey = apiKey;
            this.modelName = modelName;
        }

        /**
         * 继承基座：全局唯一 Provider（DbAwareModelConfigProvider 或业务自定义）
         */
        private ModelConfigProvider base() {
            ModelConfigProvider base = baseProvider.getIfUnique();
            if (base == null) {
                throw new IllegalStateException("未找到唯一的 ModelConfigProvider Bean，无法解析结点级覆盖的继承默认值");
            }
            return base;
        }

        @Override
        public String getBaseUrl(String purposeType) {
            return StringUtils.hasText(baseUrl) ? baseUrl : base().getBaseUrl(purposeType);
        }

        @Override
        public String getApiKey(String purposeType, Long userId) {
            return StringUtils.hasText(apiKey) ? apiKey : base().getApiKey(purposeType, userId);
        }

        @Override
        public String getModelName(String purposeType) {
            return StringUtils.hasText(modelName) ? modelName : base().getModelName(purposeType);
        }

        @Override
        public long getTimeoutSeconds(String purposeType) {
            return base().getTimeoutSeconds(purposeType);
        }

        @Override
        public String getConfigSignatureWithoutKey(String purposeType) {
            // 覆盖项 + 基座签名动态拼接：任一变化触发底层模型重建
            return baseUrl + "|" + modelName + "|" + base().getConfigSignatureWithoutKey(purposeType);
        }
    }

    @Override
    public String traceSummary(FlowGraph.FlowNode node, FlowContext context, boolean suspended) {
        if (suspended) {
            return "流式调用发起（挂起等待回调）";
        }
        return "模型输出 " + context.getString(FlowContext.TRACE_LLM_ANSWER).length() + " 字";
    }

    @Override
    public String traceDetail(FlowGraph.FlowNode node, FlowContext context, boolean suspended) {
        return "【渲染后提示词】\n" + context.getString(FlowContext.TRACE_LLM_PROMPT)
                + (suspended ? "\n\n（流式挂起，模型回答在续跑时补记）" : "");
    }

    @Override
    public String suspendedTraceSupplement(FlowContext context) {
        String answer = context.getString(FlowContext.TRACE_LLM_ANSWER);
        return StringUtils.hasText(answer) ? "【模型回答】\n" + answer : null;
    }

    @Override
    public void clearTraceVars(FlowContext context) {
        context.removeVariable(FlowContext.TRACE_LLM_PROMPT);
        context.removeVariable(FlowContext.TRACE_LLM_ANSWER);
    }
}
