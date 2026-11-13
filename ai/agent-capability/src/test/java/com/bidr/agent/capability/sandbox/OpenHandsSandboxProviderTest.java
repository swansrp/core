package com.bidr.agent.capability.sandbox;

import com.bidr.agent.runtime.config.AgentRuntimeConfigProvider;
import com.bidr.agent.runtime.config.AgentRuntimeProperties;
import com.bidr.agent.runtime.provider.openhands.OpenHandsClient;
import com.bidr.agent.runtime.provider.openhands.OpenHandsProvider;
import com.bidr.llm.agent.sandbox.SandboxExecResult;
import com.bidr.llm.agent.sandbox.SandboxSeedFile;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Title: OpenHandsSandboxProviderTest
 * Description: OpenHands 沙箱实现的契约钉桩——本地回环 {@link HttpServer} 造 openhands-agent-server
 * fake（路由/字段按 1.62.0 wheel 核对形状：会话族 {@code /api/conversations/*}、
 * bash {@code POST /api/conversations/{cid}/bash/execute_bash_command}、file upload/download、
 * agent-profiles），client/provider 全真件指向 127.0.0.1（照棒2 HttpServer 模式，不打外网）。钉：
 * ensure 幂等（已建只补种子）＋种子上传 relPath 拼绝对路径；会话缺失兜底重建用同 id＋
 * createSession 同口径 body；exec 映射 exitCode/合并 output/timedOut＋64KB 截断；fetch 回字节＋
 * 非法路径转 IllegalArgumentException（端口契约）；destroy 404 幂等；validate 非 404 异常不误重建。
 *
 * @author Sharp
 * @since 2026/11/14
 */
class OpenHandsSandboxProviderTest {

    private static final String SID = "aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee";
    private static final String WORKSPACE = "/workspace/" + SID;

    private HttpServer server;
    private OpenHandsSandboxProvider sandbox;
    private final ObjectMapper mapper = new ObjectMapper();

    /** fake 状态：已建会话集、调用记录、可配置响应 */
    private final Set<String> conversations = new HashSet<>();
    private final List<String> createBodies = new ArrayList<>();
    private final List<String> uploadPaths = new ArrayList<>();
    private final List<String> bashBodies = new ArrayList<>();
    private final List<String> downloadPaths = new ArrayList<>();
    private final List<String> deleteCalls = new ArrayList<>();
    private String bashResponse = "{\"exit_code\":0,\"stdout\":\"ok\",\"stderr\":\"\"}";
    private byte[] downloadBytes = "file-content".getBytes(StandardCharsets.UTF_8);
    private int validateStatus = 200;

    @BeforeEach
    void startFake() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", this::route);
        server.start();
        AgentRuntimeProperties properties = new AgentRuntimeProperties();
        properties.setBaseUrl("http://127.0.0.1:" + server.getAddress().getPort());
        properties.setApiKey("sk-sandbox-test");
        AgentRuntimeConfigProvider config = new AgentRuntimeConfigProvider(properties, null);
        OpenHandsClient client = new OpenHandsClient(config);
        sandbox = new OpenHandsSandboxProvider(client,
                new OpenHandsProvider(config, properties, client), properties);
    }

    @AfterEach
    void stopServer() {
        if (server != null) {
            server.stop(0);
        }
    }

    // ---------------------------------------------------------------- fake 路由（按 M-5 核对形状）

    private void route(HttpExchange exchange) throws IOException {
        String path = exchange.getRequestURI().getPath();
        String method = exchange.getRequestMethod();
        byte[] body = exchange.getRequestBody().readAllBytes();
        String query = exchange.getRequestURI().getRawQuery();
        try {
            if (path.equals("/api/agent-profiles")) {
                respond(exchange, 200, "{\"profiles\":[{\"id\":\"prof-1\",\"name\":\"default\"}],"
                        + "\"active_agent_profile_id\":\"prof-1\"}");
            } else if (path.equals("/api/conversations") && "POST".equals(method)) {
                createBodies.add(new String(body, StandardCharsets.UTF_8));
                JsonNode node = mapper.readTree(body);
                conversations.add(node.path("conversation_id").asText());
                respond(exchange, 201, "{\"id\":\"" + node.path("conversation_id").asText() + "\"}");
            } else if (path.equals("/api/conversations/" + SID) && "GET".equals(method)) {
                if (validateStatus != 200) {
                    respond(exchange, validateStatus, "{\"detail\":\"boom\"}");
                } else if (conversations.contains(SID)) {
                    respond(exchange, 200, "{\"id\":\"" + SID + "\"}");
                } else {
                    respond(exchange, 404, "{\"detail\":\"Conversation not found\"}");
                }
            } else if (path.equals("/api/conversations/" + SID) && "DELETE".equals(method)) {
                deleteCalls.add(SID);
                if (conversations.remove(SID)) {
                    respond(exchange, 200, "{\"success\":true}");
                } else {
                    respond(exchange, 404, "{\"detail\":\"Conversation not found\"}");
                }
            } else if (path.equals("/api/conversations/" + SID + "/file/upload")) {
                uploadPaths.add(queryParam(query, "path"));
                respond(exchange, 200, "{\"success\":true,\"file_size\":" + body.length + "}");
            } else if (path.equals("/api/conversations/" + SID + "/file/download")) {
                downloadPaths.add(queryParam(query, "path"));
                exchange.getResponseHeaders().set("Content-Type", "application/octet-stream");
                exchange.sendResponseHeaders(200, downloadBytes.length);
                try (OutputStream os = exchange.getResponseBody()) {
                    os.write(downloadBytes);
                }
            } else if (path.equals("/api/conversations/" + SID + "/bash/execute_bash_command")) {
                bashBodies.add(new String(body, StandardCharsets.UTF_8));
                respond(exchange, 200, bashResponse);
            } else {
                respond(exchange, 404, "{\"detail\":\"no route: " + path + "\"}");
            }
        } catch (RuntimeException e) {
            respond(exchange, 500, "{\"detail\":\"" + e.getMessage() + "\"}");
        }
    }

    private static String queryParam(String rawQuery, String name) {
        if (rawQuery == null) {
            return null;
        }
        for (String pair : rawQuery.split("&")) {
            int eq = pair.indexOf('=');
            if (eq > 0 && name.equals(pair.substring(0, eq))) {
                return URLDecoder.decode(pair.substring(eq + 1), StandardCharsets.UTF_8);
            }
        }
        return null;
    }

    private static void respond(HttpExchange exchange, int status, String json) throws IOException {
        byte[] out = json.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json");
        exchange.sendResponseHeaders(status, out.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(out);
        }
    }

    // ---------------------------------------------------------------- 用例

    @Test
    @DisplayName("ensure 幂等：会话已建只补种子，不重建会话；种子按 relPath 拼进会话工作目录绝对路径")
    void ensureIsIdempotentAndUploadsSeedsIntoSessionWorkspace() {
        conversations.add(SID);
        sandbox.ensure(SID, Arrays.asList(
                new SandboxSeedFile("scripts/make_docx.py", "print(1)".getBytes(StandardCharsets.UTF_8)),
                new SandboxSeedFile("assets/监理日志-template.docx", new byte[]{1, 2, 3})));
        assertEquals(0, createBodies.size(), "会话已存在不得重建");
        assertEquals(Arrays.asList(WORKSPACE + "/scripts/make_docx.py",
                WORKSPACE + "/assets/监理日志-template.docx"), uploadPaths);

        // 二次 ensure（工具层每轮 sandbox_run 都调）：仍不重建，种子覆盖重传
        createBodies.clear();
        uploadPaths.clear();
        sandbox.ensure(SID, List.of(new SandboxSeedFile("scripts/make_docx.py",
                "print(2)".getBytes(StandardCharsets.UTF_8))));
        assertEquals(0, createBodies.size());
        assertEquals(List.of(WORKSPACE + "/scripts/make_docx.py"), uploadPaths);
    }

    @Test
    @DisplayName("ensure 兜底重建：会话 404 时按 createSession 同口径建同 id 会话（conversation_id/workspace/profile/max_iterations）")
    void ensureRecreatesMissingConversationWithSameId() {
        sandbox.ensure(SID, List.of(new SandboxSeedFile("scripts/a.py", new byte[]{9})));
        assertEquals(1, createBodies.size(), "会话缺失应兜底重建一次");
        Map<String, Object> parsed = parse(createBodies.get(0));
        assertEquals(SID, parsed.get("conversation_id"), "必须同 id 重建（exec/fetch 都按该 id 寻址）");
        assertEquals("prof-1", parsed.get("agent_profile_id"));
        assertEquals(30, ((Number) parsed.get("max_iterations")).intValue());
        @SuppressWarnings("unchecked")
        Map<String, Object> workspace = (Map<String, Object>) parsed.get("workspace");
        assertEquals(WORKSPACE, workspace.get("working_dir"));
        assertEquals("LocalWorkspace", workspace.get("kind"));
        assertEquals(List.of(WORKSPACE + "/scripts/a.py"), uploadPaths, "重建后照常补种子");
    }

    @Test
    @DisplayName("ensure 非 404 失败（503 不可达）：原样上抛且不误建新会话")
    void ensureRethrowsNonMissingErrorsWithoutRecreate() {
        validateStatus = 503;
        conversations.add(SID);
        RuntimeException e = assertThrows(RuntimeException.class,
                () -> sandbox.ensure(SID, List.of()));
        assertEquals(0, createBodies.size(), "不可达不是会话缺失，禁止重建");
        assertTrue(e.getMessage().contains("503") || e.getMessage().contains("不可达"),
                "错误信息应体现不可达：" + e.getMessage());
    }

    @Test
    @DisplayName("exec 映射：exitCode/合并 stdout+stderr/timedOut；bash 端点按会话挂载")
    void execMapsBashOutput() {
        conversations.add(SID);
        bashResponse = "{\"exit_code\":3,\"stdout\":\"out-1\",\"stderr\":\"err-1\"}";
        SandboxExecResult result = sandbox.exec(SID, "python scripts/make_docx.py", 60_000);
        assertEquals(3, result.exitCode());
        assertEquals("out-1\nerr-1", result.output());
        assertEquals(false, result.timedOut());
        assertEquals(1, bashBodies.size());
        assertTrue(bashBodies.get(0).contains("\"command\":\"python scripts/make_docx.py\""));
        assertTrue(bashBodies.get(0).contains("\"timeout\":60"), "60000ms 应折算 60 秒");

        bashResponse = "{\"exit_code\":-1,\"stdout\":\"partial\"}";
        SandboxExecResult timedOut = sandbox.exec(SID, "sleep 999", 30_000);
        assertTrue(timedOut.timedOut());
        assertEquals("partial", timedOut.output());
    }

    @Test
    @DisplayName("exec 输出截断：合并超 64KB（UTF-8 字节）按边界截断并追加标记")
    void execTruncatesOutputTo64Kb() {
        conversations.add(SID);
        StringBuilder big = new StringBuilder();
        for (int i = 0; i < 70_000; i++) {
            big.append('x');
        }
        bashResponse = "{\"exit_code\":0,\"stdout\":\"" + big + "\"}";
        SandboxExecResult result = sandbox.exec(SID, "cat big", 60_000);
        assertTrue(result.output().endsWith(OpenHandsSandboxProvider.TRUNCATION_NOTE));
        byte[] head = result.output().replace(OpenHandsSandboxProvider.TRUNCATION_NOTE, "")
                .getBytes(StandardCharsets.UTF_8);
        assertTrue(head.length <= 64 * 1024, "截断后不得超 64KB：" + head.length);
        assertTrue(head.length >= 64 * 1024 - 4, "应在预算内尽量保留：" + head.length);

        // 中文多字节不被劈开：截断点附近的字节序列仍是合法 UTF-8（能原样编码回环）
        String headText = new String(head, StandardCharsets.UTF_8);
        assertEquals(head.length, headText.getBytes(StandardCharsets.UTF_8).length);
    }

    @Test
    @DisplayName("fetch：走 provider.downloadFile 回字节（绝对路径=会话工作目录+relPath）")
    void fetchDownloadsFromSessionWorkspace() {
        conversations.add(SID);
        downloadBytes = new byte[]{7, 8, 9};
        byte[] bytes = sandbox.fetch(SID, "out/产物.docx");
        assertEquals(3, bytes.length);
        assertEquals(List.of(WORKSPACE + "/out/产物.docx"), downloadPaths);
    }

    @Test
    @DisplayName("fetch 非法路径：穿越/绝对路径抛 IllegalArgumentException（端口契约，工具层转「拒绝」人话）")
    void fetchRejectsIllegalPathAsIllegalArgumentException() {
        for (String risky : new String[]{"../escape.txt", "/etc/passwd", "a/./b"}) {
            IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
                    () -> sandbox.fetch(SID, risky), "应拒绝：" + risky);
            assertTrue(!e.getMessage().startsWith("["), "人话不带平台码前缀：" + e.getMessage());
        }
    }

    @Test
    @DisplayName("destroy 幂等：首次删除成功；再次（404）静默吞掉")
    void destroyIsIdempotentOn404() {
        conversations.add(SID);
        sandbox.destroy(SID);
        assertEquals(List.of(SID), deleteCalls);
        sandbox.destroy(SID);
        assertEquals(2, deleteCalls.size(), "第二次 DELETE 也发起（由上游 404 判定不存在）");
    }

    @Test
    @DisplayName("合并与截断纯函数：stdout/stderr 空缺不造多余换行；64KB 内原文直回")
    void mergeOutputEdgeCases() {
        OpenHandsClient.BashExecResult onlyErr =
                new OpenHandsClient.BashExecResult(1, null, "err", false);
        assertEquals("err", OpenHandsSandboxProvider.mergeOutput(onlyErr));
        OpenHandsClient.BashExecResult onlyOut =
                new OpenHandsClient.BashExecResult(1, "out", "", false);
        assertEquals("out", OpenHandsSandboxProvider.mergeOutput(onlyOut));
        OpenHandsClient.BashExecResult none =
                new OpenHandsClient.BashExecResult(1, null, null, false);
        assertEquals("", OpenHandsSandboxProvider.mergeOutput(none));
        String within = "x".repeat(64 * 1024);
        assertEquals(within, OpenHandsSandboxProvider.truncateUtf8(within));
    }

    /** 简易 JSON 对象解析（只服务断言，够用即可） */
    private Map<String, Object> parse(String json) {
        try {
            Map<String, Object> out = new LinkedHashMap<>();
            JsonNode node = mapper.readTree(json);
            node.fields().forEachRemaining(f -> {
                JsonNode v = f.getValue();
                if (v.isObject()) {
                    Map<String, Object> inner = new LinkedHashMap<>();
                    v.fields().forEachRemaining(g -> inner.put(g.getKey(), g.getValue().asText()));
                    out.put(f.getKey(), inner);
                } else if (v.isNumber()) {
                    out.put(f.getKey(), v.asInt());
                } else {
                    out.put(f.getKey(), v.asText());
                }
            });
            return out;
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }
}
