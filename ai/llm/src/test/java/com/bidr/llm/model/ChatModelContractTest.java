package com.bidr.llm.model;

import com.bidr.llm.provider.ModelConfigProvider;
import com.sun.net.httpserver.HttpServer;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.output.FinishReason;
import dev.langchain4j.model.output.TokenUsage;
import org.junit.After;
import org.junit.Assert;
import org.junit.Test;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * Title: ChatModelContractTest
 * Description: T1 双实现行为契约钉桩（lc4j 0.33→1.23 升级防回归锚，报告 §6 T1）。
 * 同一假网关场景分别打 {@link RawSyncChatModel}（同步）与 {@link RawSseStreamingChatModel}
 * （SSE 流式）两实现，断言六组网关应答形态下的**行为面**：产出内容、工具调用保全、
 * usage 容错、截断不异常、非 2xx 错误透传——这些是升级前后都不许变的契约。
 * <p>
 * <b>可机械移植设计（棒3 契约，升级时只动类型层）</b>：全部 lc4j 0.33 类型触点收敛在
 * {@link #syncCall} / {@link #sseCall} 两个适配方法与 {@link Outcome} 内——棒3 移植时
 * 仅替换 {@code Response<AiMessage>}→{@code ChatResponse}、{@code generate}→{@code chat}、
 * SSE {@code onComplete(Response)}→{@code onCompleteResponse(ChatResponse)}，六个用例的
 * 断言主体（text/toolCalls/tokenUsage/finishReason/error 五要素）一字不动。
 * {@link ToolExecutionRequest}/{@link TokenUsage}/{@link FinishReason} 在 1.23 同名同包，
 * import 不动。
 * </p>
 * <p>
 * 假网关用 JDK 内置 {@link HttpServer}（同 {@link RawSyncChatModelTest}/{@link RawSseIdleTimeoutTest}
 * 范式，零新依赖）；SSE 形态按 {@code data:} 事件流回放同一语义的分片。JUnit4（M-7）。
 * </p>
 *
 * @author Sharp
 * @since 2026/11/7
 */
public class ChatModelContractTest {

    /** 待钉桩的两条实现链路 */
    private enum Mode {
        SYNC("同步"), SSE("SSE 流式");

        final String label;

        Mode(String label) {
            this.label = label;
        }
    }

    private final List<HttpServer> servers = new ArrayList<>();

    @After
    public void stopServers() {
        for (HttpServer server : servers) {
            server.stop(0);
        }
        servers.clear();
    }

    // ---------------- 六组契约用例（断言主体棒3 不动） ----------------

    /** 1. 纯文本应答：text 原样返回、无工具调用、usage 透出 */
    @Test
    public void plainTextAnswerReturned() throws Exception {
        String syncBody = "{\"choices\":[{\"message\":{\"role\":\"assistant\",\"content\":\"你好，世界\"},"
                + "\"finish_reason\":\"stop\"}],"
                + "\"usage\":{\"prompt_tokens\":11,\"completion_tokens\":22,\"total_tokens\":33}}";
        String sseStream = "data: {\"choices\":[{\"delta\":{\"role\":\"assistant\",\"content\":\"你好，世界\"}}]}\n"
                + "data: {\"choices\":[{\"delta\":{},\"finish_reason\":\"stop\"}]}\n"
                + "data: {\"usage\":{\"prompt_tokens\":11,\"completion_tokens\":22,\"total_tokens\":33}}\n"
                + "data: [DONE]\n";
        for (Mode mode : Mode.values()) {
            Outcome out = runScenario(mode, 200, syncBody, sseStream);
            Assert.assertNull(mode.label + "链路不应报错: " + out.errorDesc(), out.error);
            Assert.assertTrue(mode.label + "链路须正常收口", out.completed);
            Assert.assertEquals(mode.label + "链路应答文本原样返回", "你好，世界", out.text);
            Assert.assertTrue(mode.label + "链路不应产出工具调用", out.toolCalls.isEmpty());
            Assert.assertNotNull(mode.label + "链路 token 用量须透出", out.tokenUsage);
            Assert.assertEquals(mode.label + "链路输入 token 数", Integer.valueOf(11),
                    out.tokenUsage.inputTokenCount());
            Assert.assertEquals(mode.label + "链路输出 token 数", Integer.valueOf(22),
                    out.tokenUsage.outputTokenCount());
        }
    }

    /** 2. tool_calls[].id 缺失：按序合成 id（call_0/call_1），名称与参数不丢 */
    @Test
    public void toolCallIdsSynthesizedWhenGatewayOmitsThem() throws Exception {
        String syncBody = "{\"choices\":[{\"message\":{\"role\":\"assistant\",\"content\":null,"
                + "\"tool_calls\":["
                + "{\"function\":{\"name\":\"searchDocs\",\"arguments\":\"{\\\"q\\\":\\\"档案\\\"}\"}},"
                + "{\"function\":{\"name\":\"listScopes\",\"arguments\":\"{}\"}}"
                + "]},\"finish_reason\":\"tool_calls\"}],"
                + "\"usage\":{\"prompt_tokens\":12,\"completion_tokens\":30,\"total_tokens\":42}}";
        String sseStream = "data: {\"choices\":[{\"delta\":{\"tool_calls\":[{\"index\":0,"
                + "\"function\":{\"name\":\"searchDocs\",\"arguments\":\"{\\\"q\\\":\\\"档\"}}]}}]}\n"
                + "data: {\"choices\":[{\"delta\":{\"tool_calls\":[{\"index\":0,"
                + "\"function\":{\"arguments\":\"案\\\"}\"}}]}}]}\n"
                + "data: {\"choices\":[{\"delta\":{\"tool_calls\":[{\"index\":1,"
                + "\"function\":{\"name\":\"listScopes\",\"arguments\":\"{}\"}}]}}]}\n"
                + "data: {\"choices\":[{\"delta\":{},\"finish_reason\":\"tool_calls\"}]}\n"
                + "data: [DONE]\n";
        for (Mode mode : Mode.values()) {
            Outcome out = runScenario(mode, 200, syncBody, sseStream);
            Assert.assertNull(mode.label + "链路不应报错: " + out.errorDesc(), out.error);
            Assert.assertEquals(mode.label + "链路工具调用数不丢", 2, out.toolCalls.size());
            ToolExecutionRequest first = out.toolCalls.get(0);
            ToolExecutionRequest second = out.toolCalls.get(1);
            Assert.assertEquals("id 缺失须按序合成(call_0)，回填结果需 id 对齐",
                    "call_0", first.id());
            Assert.assertEquals("id 缺失须按序合成(call_1)", "call_1", second.id());
            Assert.assertEquals("工具名不丢", "searchDocs", first.name());
            Assert.assertEquals("分片参数须拼装完整: " + first.arguments(),
                    "{\"q\":\"档案\"}", first.arguments());
            Assert.assertEquals("第二个工具名不丢", "listScopes", second.name());
        }
    }

    /** 3. finish_reason=length：截断按正常应答返回（不异常），同步链路映射 LENGTH */
    @Test
    public void finishReasonLengthReturnsNormally() throws Exception {
        String syncBody = "{\"choices\":[{\"message\":{\"role\":\"assistant\",\"content\":\"partial\"},"
                + "\"finish_reason\":\"length\"}],"
                + "\"usage\":{\"prompt_tokens\":10,\"completion_tokens\":4096,\"total_tokens\":4106}}";
        String sseStream = "data: {\"choices\":[{\"delta\":{\"content\":\"partial\"}}]}\n"
                + "data: {\"choices\":[{\"delta\":{},\"finish_reason\":\"length\"}]}\n"
                + "data: [DONE]\n";
        for (Mode mode : Mode.values()) {
            Outcome out = runScenario(mode, 200, syncBody, sseStream);
            Assert.assertNull(mode.label + "链路截断不应报错(重试无意义): " + out.errorDesc(), out.error);
            Assert.assertTrue(mode.label + "链路截断须正常收口", out.completed);
            Assert.assertEquals(mode.label + "链路截断文本照常返回", "partial", out.text);
            if (mode == Mode.SYNC) {
                Assert.assertEquals("同步链路截断须映射 LENGTH 透出", FinishReason.LENGTH,
                        out.finishReason);
            } else {
                // 0.33 SSE 实现现状钉桩：收口 Response 不携带 finishReason（恒 null）；
                // 棒3 若随移植带上 finishReason，本断言随类型层一并翻转
                Assert.assertNull("SSE 链路 0.33 收口不带 finishReason(现状钉桩)", out.finishReason);
            }
        }
    }

    /** 4. usage 缺失：不 NPE，tokenUsage 容忍 null（没拿到≠0） */
    @Test
    public void nullUsageTolerated() throws Exception {
        String syncBody = "{\"choices\":[{\"message\":{\"role\":\"assistant\","
                + "\"content\":\"no usage here\"},\"finish_reason\":\"stop\"}]}";
        String sseStream = "data: {\"choices\":[{\"delta\":{\"content\":\"no usage here\"}}]}\n"
                + "data: {\"choices\":[{\"delta\":{},\"finish_reason\":\"stop\"}]}\n"
                + "data: [DONE]\n";
        for (Mode mode : Mode.values()) {
            Outcome out = runScenario(mode, 200, syncBody, sseStream);
            Assert.assertNull(mode.label + "链路 usage 缺失不应报错: " + out.errorDesc(), out.error);
            Assert.assertTrue(mode.label + "链路须正常收口", out.completed);
            Assert.assertEquals(mode.label + "链路应答文本不受 usage 缺失影响", "no usage here", out.text);
            Assert.assertNull(mode.label + "链路未拿到 usage 须如实为 null(不臆造 0)", out.tokenUsage);
        }
    }

    /** 5. 空 content + 非空 tool_calls：不丢 toolCalls、不炸（工具轮正文本可缺） */
    @Test
    public void emptyContentWithToolCallsKeepsToolCalls() throws Exception {
        String syncBody = "{\"choices\":[{\"message\":{\"role\":\"assistant\",\"content\":\"\","
                + "\"tool_calls\":[{\"function\":{\"name\":\"searchDocs\","
                + "\"arguments\":\"{\\\"space\\\":\\\"IT空间\\\"}\"}}"
                + "]},\"finish_reason\":\"tool_calls\"}],"
                + "\"usage\":{\"prompt_tokens\":8,\"completion_tokens\":20,\"total_tokens\":28}}";
        String sseStream = "data: {\"choices\":[{\"delta\":{\"tool_calls\":[{\"index\":0,"
                + "\"function\":{\"name\":\"searchDocs\",\"arguments\":\"{\\\"space\\\":\\\"IT空间\\\"}\"}}]}}]}\n"
                + "data: {\"choices\":[{\"delta\":{},\"finish_reason\":\"tool_calls\"}]}\n"
                + "data: [DONE]\n";
        for (Mode mode : Mode.values()) {
            Outcome out = runScenario(mode, 200, syncBody, sseStream);
            Assert.assertNull(mode.label + "链路空 content+工具调用不应报错: " + out.errorDesc(), out.error);
            Assert.assertTrue(mode.label + "链路须正常收口", out.completed);
            Assert.assertEquals(mode.label + "链路 toolCalls 不得丢失", 1, out.toolCalls.size());
            Assert.assertEquals("工具名不丢", "searchDocs", out.toolCalls.get(0).name());
            Assert.assertTrue("工具参数中文须完好: " + out.toolCalls.get(0).arguments(),
                    out.toolCalls.get(0).arguments().contains("IT空间"));
            Assert.assertEquals("空 content 须按序合成 id", "call_0", out.toolCalls.get(0).id());
        }
    }

    /** 6. 网关非 2xx：异常透传 HTTP 码与网关原始错误体（不吞没、不被包装成泛化错误） */
    @Test
    public void non2xxSurfacesErrorWithGatewayBody() throws Exception {
        String errorBody = "{\"error\":\"invalid tools\"}";
        for (Mode mode : Mode.values()) {
            Outcome out = runScenario(mode, 400, errorBody, errorBody);
            Assert.assertFalse(mode.label + "链路失败不得正常收口", out.completed);
            Assert.assertNotNull(mode.label + "链路非 2xx 须报错", out.error);
            String msg = String.valueOf(out.error.getMessage());
            Assert.assertTrue(mode.label + "链路错误须含 HTTP 状态码，实际: " + msg, msg.contains("400"));
            Assert.assertTrue(mode.label + "链路错误须带网关原始体，实际: " + msg, msg.contains("invalid tools"));
        }
    }

    // ---------------- 适配层（棒3 唯一改动点：类型层替换） ----------------

    /**
     * 双实现调用收口的归一化五要素：断言全部打在这层之上，棒3 六用例断言主体不动。
     * SYNC：generate 返回 {@code Response<AiMessage>} → 本类拆五要素；
     * SSE：listener onComplete/onError 回调 → 本类拆五要素。
     */
    private static final class Outcome {
        boolean completed;
        String text;
        List<ToolExecutionRequest> toolCalls = Collections.emptyList();
        TokenUsage tokenUsage;
        FinishReason finishReason;
        Throwable error;

        String errorDesc() {
            return error == null ? "无异常" : error.getClass().getSimpleName() + ": " + error.getMessage();
        }

        /** 棒3 移植点：Response&lt;AiMessage&gt; → ChatResponse（取值调用同步替换） */
        static Outcome of(ChatResponse resp) {
            Outcome out = new Outcome();
            out.completed = true;
            AiMessage ai = resp.aiMessage();
            out.text = ai.text();
            out.toolCalls = ai.hasToolExecutionRequests()
                    ? new ArrayList<>(ai.toolExecutionRequests()) : Collections.emptyList();
            out.tokenUsage = resp.tokenUsage();
            out.finishReason = resp.finishReason();
            return out;
        }
    }

    /** 同步链路适配（棒3 移植点：generate→chat、Response→ChatResponse） */
    private Outcome syncCall(String baseUrl) {
        RawSyncChatModel model = new RawSyncChatModel(stubProvider(baseUrl), "AGENT", null);
        try {
            ChatResponse resp = model.chat(Collections.singletonList(UserMessage.from("契约钉桩")));
            return Outcome.of(resp);
        } catch (RuntimeException e) {
            Outcome out = new Outcome();
            out.error = e;
            return out;
        }
    }

    /** SSE 流式链路适配（棒3 移植点：onComplete(Response)→onCompleteResponse(ChatResponse)） */
    private Outcome sseCall(String baseUrl) throws InterruptedException {
        RawSseStreamingChatModel model = new RawSseStreamingChatModel(stubProvider(baseUrl), "AGENT", null);
        CountDownLatch done = new CountDownLatch(1);
        Outcome[] holder = new Outcome[1];
        Throwable[] failure = new Throwable[1];
        model.generate(Collections.singletonList(UserMessage.from("契约钉桩")), null,
                new RawSseStreamingChatModel.Listener() {
                    @Override
                    public void onReasoning(String delta) {
                    }

                    @Override
                    public void onToken(String delta) {
                    }

                    @Override
                    public void onComplete(ChatResponse response) {
                        holder[0] = Outcome.of(response);
                        done.countDown();
                    }

                    @Override
                    public void onError(Throwable error) {
                        failure[0] = error;
                        done.countDown();
                    }
                });
        Assert.assertTrue("SSE 链路须在超时前收口(onComplete/onError 必达)", done.await(15, TimeUnit.SECONDS));
        if (failure[0] != null) {
            Outcome out = new Outcome();
            out.error = failure[0];
            return out;
        }
        return holder[0];
    }

    /**
     * 按模式起假网关跑一轮并归一化收口：SYNC 回固定 JSON 体（application/json），
     * SSE 回事件流（text/event-stream）；非 2xx 时两模式同体同码（错误腿不挑内容类型）。
     */
    private Outcome runScenario(Mode mode, int status, String syncBody, String sseStream) throws IOException {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/v1/chat/completions", exchange -> {
            if (status != 200) {
                byte[] out = syncBody.getBytes(StandardCharsets.UTF_8);
                exchange.getResponseHeaders().add("Content-Type", "application/json");
                exchange.sendResponseHeaders(status, out.length);
                exchange.getResponseBody().write(out);
                exchange.close();
                return;
            }
            if (mode == Mode.SYNC) {
                byte[] out = syncBody.getBytes(StandardCharsets.UTF_8);
                exchange.getResponseHeaders().add("Content-Type", "application/json");
                exchange.sendResponseHeaders(200, out.length);
                exchange.getResponseBody().write(out);
                exchange.close();
            } else {
                byte[] out = sseStream.getBytes(StandardCharsets.UTF_8);
                exchange.getResponseHeaders().set("Content-Type", "text/event-stream");
                exchange.sendResponseHeaders(200, out.length);
                try (OutputStream os = exchange.getResponseBody()) {
                    os.write(out);
                }
            }
        });
        server.start();
        servers.add(server);
        String baseUrl = "http://127.0.0.1:" + server.getAddress().getPort() + "/v1";
        try {
            return mode == Mode.SYNC ? syncCall(baseUrl) : sseCall(baseUrl);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("SSE 收口等待被中断", e);
        }
    }

    /** 桩配置：与 RawSyncChatModelTest 同款，baseUrl 指向本轮假网关 */
    private static ModelConfigProvider stubProvider(String baseUrl) {
        return new ModelConfigProvider() {
            @Override
            public String getBaseUrl(String purposeType) {
                return baseUrl;
            }

            @Override
            public String getApiKey(String purposeType, Long userId) {
                return "test-key";
            }

            @Override
            public String getModelName(String purposeType) {
                return "test-model";
            }

            @Override
            public long getTimeoutSeconds(String purposeType) {
                return 30;
            }

            @Override
            public String getConfigSignatureWithoutKey(String purposeType) {
                return baseUrl + "|contract-stub";
            }
        };
    }
}
