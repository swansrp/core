package com.bidr.cube.client;

import com.bidr.cube.client.CubeHttpClient.CreateResult;
import com.bidr.cube.client.CubeHttpClient.ExecResult;
import com.bidr.cube.config.CubeSandboxProperties;
import com.bidr.kernel.exception.ServiceException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
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
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Title: CubeHttpClientTest
 * Description: CubeHttpClient 全路由契约钉桩——本地回环 {@link HttpServer} 造 Cube fake
 * （照 OpenHandsSandboxProviderTest 的 HttpServer 模式，不打外网）。钉：create body 形状
 * （templateID/timeout/metadata["host-mount"]=JSON 字符串且含 {hostRoot}/{sid}、allowOut
 * 非空才下发 network.allowOut）＋瞬态 500 ×4 重试且 4xx 不重试；exec Connect 信封前 5 字节
 * 精确断言＋响应帧流（exit 0 无 exitCode 帧→0、exit status 7 传递、stdout base64/明文双态
 * 解码、end-stream error 帧抛 ServiceException）；HTTP 超时→timedOut；files 写读 roundtrip；
 * delete 404 静默幂等；listSandboxIds 按 metadata 过滤；listSandboxEntries 全条目解析
 * （metadata 缺席⇒空 map、state 缺席⇒空串、≥400 上抛）；createAdmin body（role=cube-admin
 * ＋owner）；create/createAdmin 的 metadata.owner=缺省 ownerId；dataBaseUrl 显式覆盖/缺省
 * host 推导。
 *
 * @author Sharp
 * @since 2026/11/16
 */
class CubeHttpClientTest {

    private static final ObjectMapper OM = new ObjectMapper();
    private static final String SID = "aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee";
    private static final int ENVD_PORT = 49983;

    private HttpServer server;
    private CubeSandboxProperties properties;
    private CubeHttpClient client;
    private final ObjectMapper mapper = new ObjectMapper();

    /** fake 状态：可配置响应与调用记录 */
    private final List<String> createBodies = new ArrayList<>();
    private int createRemaining500;
    private Integer createStatusOverride;
    private Integer listStatusOverride;
    private int deleteCalls;
    private int sandboxDeletesUntilMissing;
    private final List<byte[]> execRequests = new ArrayList<>();
    private byte[] execResponse = new byte[0];
    private int execStatus = 200;
    private long execDelayMs;
    private final Map<String, byte[]> files = new LinkedHashMap<>();
    private final List<String> readFileQueries = new ArrayList<>();

    @BeforeEach
    void startFake() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", this::route);
        server.start();
        int port = server.getAddress().getPort();
        properties = new CubeSandboxProperties();
        properties.setApiUrl("http://127.0.0.1:" + port);
        properties.setApiKey("e2b_test");
        properties.setTemplate("tpl-test");
        properties.setHostRoot("/data/sandbox");
        // 缺省推导是 http://{host}（不带端口），本地 fake 在随机端口 ⇒ 显式配置覆盖（M-6 允许）
        properties.setDataBaseUrl("http://127.0.0.1:" + port);
        client = new CubeHttpClient(properties);
    }

    @AfterEach
    void stopServer() {
        if (server != null) {
            server.stop(0);
        }
    }

    // ---------------------------------------------------------------- fake 路由（按探针核对形状）

    private void route(HttpExchange exchange) throws IOException {
        String path = exchange.getRequestURI().getPath();
        String method = exchange.getRequestMethod();
        byte[] body = exchange.getRequestBody().readAllBytes();
        String query = exchange.getRequestURI().getRawQuery();
        if (path.equals("/sandboxes") && "POST".equals(method)) {
            createBodies.add(new String(body, StandardCharsets.UTF_8));
            if (createStatusOverride != null) {
                respond(exchange, createStatusOverride, "{\"detail\":\"boom\"}");
                return;
            }
            if (createRemaining500 > 0) {
                createRemaining500--;
                respond(exchange, 500, "{\"error\":\"transient\"}");
                return;
            }
            respond(exchange, 201, "{\"sandboxID\":\"" + SID + "\",\"domain\":\"" + SID
                    + ".cube.example\",\"envdVersion\":\"0.2.0\"}");
        } else if (path.equals("/sandboxes") && "GET".equals(method)) {
            if (listStatusOverride != null) {
                respond(exchange, listStatusOverride, "{\"error\":\"boom\"}");
                return;
            }
            // s1 带多键 metadata＋state；s2 单键；s3 两者全缺（缺席语义钉桩）
            respond(exchange, 200, "[{\"sandboxID\":\"s1\",\"metadata\":{\"session-id\":\"sess-A\","
                    + "\"owner\":\"epc-ai\"},\"state\":\"running\"},"
                    + "{\"sandboxID\":\"s2\",\"metadata\":{\"session-id\":\"sess-B\"},\"state\":\"paused\"},"
                    + "{\"sandboxID\":\"s3\"}]");
        } else if (path.equals("/sandboxes/" + SID) && "DELETE".equals(method)) {
            deleteCalls++;
            if (sandboxDeletesUntilMissing > 0) {
                sandboxDeletesUntilMissing--;
                exchange.sendResponseHeaders(204, -1);
                exchange.close();
            } else {
                respond(exchange, 404, "{\"detail\":\"not found\"}");
            }
        } else if (path.equals("/sandbox/" + SID + "/" + ENVD_PORT + "/process.Process/Start")
                && "POST".equals(method)) {
            execRequests.add(body);
            if (execDelayMs > 0) {
                try {
                    Thread.sleep(execDelayMs);
                } catch (InterruptedException ignored) {
                    Thread.currentThread().interrupt();
                }
            }
            if (execStatus != 200) {
                respond(exchange, execStatus, "{\"error\":\"boom\"}");
                return;
            }
            exchange.getResponseHeaders().set("Content-Type", "application/connect+json");
            exchange.sendResponseHeaders(200, execResponse.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(execResponse);
            }
        } else if (path.equals("/sandbox/" + SID + "/" + ENVD_PORT + "/files")
                && "POST".equals(method)) {
            files.put(queryParam(query, "path"), body);
            respond(exchange, 201, "");
        } else if (path.equals("/sandbox/" + SID + "/" + ENVD_PORT + "/files")
                && "GET".equals(method)) {
            readFileQueries.add(query);
            byte[] bytes = files.getOrDefault(queryParam(query, "path"), new byte[0]);
            exchange.getResponseHeaders().set("Content-Type", "application/octet-stream");
            exchange.sendResponseHeaders(200, bytes.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(bytes);
            }
        } else {
            respond(exchange, 404, "{\"detail\":\"no route: " + path + "\"}");
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

    /** 造一帧 Connect 消息：[flags 1B][len 4B 大端][payload] */
    private static byte[] frame(int flags, byte[] payload) {
        return ByteBuffer.allocate(5 + payload.length)
                .put((byte) flags)
                .putInt(payload.length)
                .put(payload)
                .array();
    }

    /** data 帧：{"event":{"data":{"stdout":...}}} */
    private static byte[] stdoutFrame(String stdout) throws IOException {
        ObjectNode data = OM.createObjectNode().put("stdout", stdout);
        return frame(0, OM.writeValueAsBytes(OM.createObjectNode().set("event",
                OM.createObjectNode().set("data", data))));
    }

    /** data 帧（stderr） */
    private static byte[] stderrFrame(String stderr) throws IOException {
        ObjectNode data = OM.createObjectNode().put("stderr", stderr);
        return frame(0, OM.writeValueAsBytes(OM.createObjectNode().set("event",
                OM.createObjectNode().set("data", data))));
    }

    /** end 帧：{"event":{"end":{...}}} */
    private static byte[] endFrame(ObjectNode end) throws IOException {
        return frame(0, OM.writeValueAsBytes(OM.createObjectNode().set("event",
                OM.createObjectNode().set("end", end))));
    }

    private static byte[] concat(byte[]... arrays) {
        int total = Arrays.stream(arrays).mapToInt(a -> a.length).sum();
        byte[] out = new byte[total];
        int pos = 0;
        for (byte[] a : arrays) {
            System.arraycopy(a, 0, out, pos, a.length);
            pos += a.length;
        }
        return out;
    }

    /** 与实现同形状的 exec 请求 JSON（用于信封长度/内容断言） */
    private static byte[] expectedExecJson(String command) throws IOException {
        ObjectNode process = OM.createObjectNode();
        process.put("cmd", "/bin/bash");
        process.set("args", OM.createArrayNode().add("-l").add("-c").add(command));
        process.set("envs", OM.createObjectNode());
        process.put("cwd", "");
        ObjectNode payload = OM.createObjectNode();
        payload.set("process", process);
        payload.put("stdin", false);
        return OM.writeValueAsBytes(payload);
    }

    // ---------------------------------------------------------------- create

    @Test
    @DisplayName("create body 形状：templateID/timeout/metadata[session-id]；host-mount=JSON 字符串含 {hostRoot}/{sid}；allowOut 空⇒无 network 键")
    void createShapesBodyAndParsesResult() throws IOException {
        CreateResult result = client.create("sess-42");
        assertEquals(SID, result.sandboxId());
        assertEquals(SID + ".cube.example", result.domain());
        assertEquals("0.2.0", result.envdVersion());
        assertEquals(1, createBodies.size());

        JsonNode body = OM.readTree(createBodies.get(0));
        assertEquals("tpl-test", body.path("templateID").asText());
        assertEquals(properties.getAdminTimeout(), body.path("timeout").asLong());
        assertEquals("sess-42", body.path("metadata").path("session-id").asText());
        assertEquals("epc-ai", body.path("metadata").path("owner").asText(),
                "metadata.owner=缺省 ownerId（对账防误杀判据）：\n" + createBodies.get(0));

        // 🔴 host-mount 是 JSON 字符串（数组 422）：先当文本取出再解析成数组
        String hostMountText = body.path("metadata").path("host-mount").asText();
        JsonNode hostMount = OM.readTree(hostMountText);
        assertTrue(hostMount.isArray(), "host-mount 反序列化后应为数组：\n" + createBodies.get(0));
        assertEquals("/data/sandbox/sess-42", hostMount.get(0).path("hostPath").asText());
        assertEquals("/workspace", hostMount.get(0).path("mountPath").asText());
        assertFalse(hostMount.get(0).path("readOnly").asBoolean());

        assertFalse(body.has("network"), "allowOut 空 ⇒ 整键不下发：\n" + createBodies.get(0));
    }

    @Test
    @DisplayName("create 瞬态 500×2→201：成功且恰好 3 次 POST /sandboxes（×4 上限内）")
    void createRetriesTransient500ThenSucceeds() {
        createRemaining500 = 2;
        CreateResult result = client.create("sess-retry");
        assertEquals(3, createBodies.size(), "500×2→201 应恰好 3 次 POST");
        assertEquals(SID, result.sandboxId());
    }

    @Test
    @DisplayName("create 4xx 不重试：400 直接抛 ServiceException 且只发 1 次 POST")
    void createDoesNotRetryOn4xx() {
        createStatusOverride = 400;
        ServiceException e = assertThrows(ServiceException.class, () -> client.create("sess-400"));
        assertEquals(1, createBodies.size(), "4xx 是确定性失败，不得重试");
        assertTrue(e.getMessage().contains("400"), e.getMessage());
    }

    @Test
    @DisplayName("create 500 耗尽 ×4 次：抛 ServiceException")
    void createExhaustsRetries() {
        createRemaining500 = 99;
        assertThrows(ServiceException.class, () -> client.create("sess-fail"));
        assertEquals(4, createBodies.size(), "×4 重试上限");
    }

    @Test
    @DisplayName("create allowOut 非空 ⇒ body.network.allowOut 原样下发（camelCase）")
    void createSendsNetworkAllowOut() throws IOException {
        properties.setAllowOut(List.of("10.0.0.0/8", "registry.internal:5000"));
        client.create("sess-net");
        JsonNode allowOut = OM.readTree(createBodies.get(0)).path("network").path("allowOut");
        assertTrue(allowOut.isArray());
        assertEquals(List.of("10.0.0.0/8", "registry.internal:5000"),
                List.of(allowOut.get(0).asText(), allowOut.get(1).asText()));
    }

    @Test
    @DisplayName("createAdmin body：metadata 带 role=cube-admin＋owner=缺省 ownerId；host-mount={hostRoot}→{hostRoot} 内外一致")
    void createAdminShapesBody() throws IOException {
        client.createAdmin();
        assertEquals(1, createBodies.size());
        JsonNode body = OM.readTree(createBodies.get(0));
        assertEquals("tpl-test", body.path("templateID").asText());
        assertEquals(properties.getAdminTimeout(), body.path("timeout").asLong());
        assertEquals("cube-admin", body.path("metadata").path("role").asText(), "role 标记键保持");
        assertEquals("epc-ai", body.path("metadata").path("owner").asText(),
                "metadata.owner=缺省 ownerId：\n" + createBodies.get(0));
        assertFalse(body.has("network"), "管理沙箱不下发 network.allowOut");
        String hostMountText = body.path("metadata").path("host-mount").asText();
        JsonNode hostMount = OM.readTree(hostMountText);
        assertTrue(hostMount.isArray(), "host-mount 仍为 JSON 字符串：\n" + createBodies.get(0));
        assertEquals("/data/sandbox", hostMount.get(0).path("hostPath").asText());
        assertEquals("/data/sandbox", hostMount.get(0).path("mountPath").asText(), "内外路径一致");
    }

    // ---------------------------------------------------------------- exec

    @Test
    @DisplayName("exec 信封+帧流：前 5 字节精确（flags=0＋len 大端）＋JSON 形状；stdout base64/明文双态解码一致；exit status 7 传递")
    void execEnvelopeAndFrames() throws IOException {
        String command = "echo hi";
        byte[] expectedJson = expectedExecJson(command);
        String base64Part = Base64.getEncoder().encodeToString("hello ".getBytes(StandardCharsets.UTF_8));
        execResponse = concat(
                stdoutFrame(base64Part),
                stdoutFrame("world!\n"),
                endFrame(OM.createObjectNode().put("exited", true).put("status", "exit status 7")));

        ExecResult result = client.exec(SID, command, 10_000);

        // 请求侧：信封前 5 字节精确断言（flags=0＋len 4B 大端）＋JSON 形状
        assertEquals(1, execRequests.size());
        byte[] sent = execRequests.get(0);
        assertEquals(5 + expectedJson.length, sent.length, "信封＝5B 头＋JSON");
        assertEquals(0, sent[0] & 0xFF, "flags 必须=0");
        int len = ((sent[1] & 0xFF) << 24) | ((sent[2] & 0xFF) << 16)
                | ((sent[3] & 0xFF) << 8) | (sent[4] & 0xFF);
        assertEquals(expectedJson.length, len, "len 4B 大端=JSON 字节数");
        JsonNode sentJson = OM.readTree(Arrays.copyOfRange(sent, 5, sent.length));
        assertEquals("/bin/bash", sentJson.path("process").path("cmd").asText());
        JsonNode args = sentJson.path("process").path("args");
        assertEquals(List.of("-l", "-c", command), List.of(args.get(0).asText(), args.get(1).asText(), args.get(2).asText()));
        assertFalse(sentJson.path("stdin").asBoolean());

        // 响应侧：base64 帧解码＋明文帧直取，两态拼接一致；exitCode 由 status 兜底解析
        assertEquals(7, result.exitCode());
        assertEquals("hello world!\n", result.stdout());
        assertEquals("", result.stderr());
        assertFalse(result.timedOut());
    }

    @Test
    @DisplayName("exec exit 0 无 exitCode 字段 ⇒ exitCode=0（照探针口径）；stderr 明文帧直取")
    void execExitZeroWithoutExitCodeField() throws IOException {
        execResponse = concat(
                stderrFrame("warn!"),
                endFrame(OM.createObjectNode().put("exited", true).put("status", "exited")));
        ExecResult result = client.exec(SID, "true", 10_000);
        assertEquals(0, result.exitCode());
        assertEquals("", result.stdout());
        assertEquals("warn!", result.stderr());
    }

    @Test
    @DisplayName("exec 显式 exitCode 帧优先传递（exit=0 显式帧也照读）")
    void execExplicitExitCodeWins() throws IOException {
        execResponse = concat(endFrame(OM.createObjectNode()
                .put("exited", true).put("exitCode", 3).put("status", "exit status 3")));
        assertEquals(3, client.exec(SID, "exit 3", 10_000).exitCode());
    }

    @Test
    @DisplayName("exec 明文帧纯字母数字（F-1：如 hello，长度非 4 倍数）⇒ 原文直回不崩（长度预筛先于 decode）")
    void execPlaintextAlnumFramesDoNotCrash() throws IOException {
        // "hello"/"warn123" 均纯字母数字（旧实现正则命中即 decode ⇒ IllegalArgumentException 崩）
        execResponse = concat(
                stdoutFrame("hello"),
                stderrFrame("warn123"),
                endFrame(OM.createObjectNode().put("exited", true).put("status", "exit status 0")));
        ExecResult result = client.exec(SID, "echo hello", 10_000);
        assertEquals("hello", result.stdout(), "长度 %4!=0 ⇒ 明文直回");
        assertEquals("warn123", result.stderr(), "stderr 同口径");
    }

    @Test
    @DisplayName("exec end-stream（flags&0x02）error 帧 ⇒ ServiceException 且信息含 error 内容")
    void execEndStreamErrorThrows() throws IOException {
        execResponse = frame(0x02, OM.writeValueAsBytes(OM.createObjectNode().put("error", "boom-inside")));
        ServiceException e = assertThrows(ServiceException.class, () -> client.exec(SID, "x", 10_000));
        assertTrue(e.getMessage().contains("boom-inside"), e.getMessage());
    }

    @Test
    @DisplayName("exec HTTP >=400 ⇒ ServiceException 带状态码")
    void execHttpErrorThrows() {
        execStatus = 500;
        ServiceException e = assertThrows(ServiceException.class, () -> client.exec(SID, "x", 10_000));
        assertTrue(e.getMessage().contains("500"), e.getMessage());
    }

    @Test
    @DisplayName("exec HTTP 超时 ⇒ ExecResult(-1,\"\",\"\",true)（短超时 200ms，fake 延迟 400ms）")
    void execTimeoutMarksTimedOut() {
        execDelayMs = 400;
        ExecResult result = client.exec(SID, "sleep 999", 200);
        assertEquals(-1, result.exitCode());
        assertEquals("", result.stdout());
        assertEquals("", result.stderr());
        assertTrue(result.timedOut());
    }

    // ---------------------------------------------------------------- files

    @Test
    @DisplayName("files 写读 roundtrip：POST octet-stream 入、GET 回字节一致；username=root")
    void filesRoundtrip() {
        byte[] content = "文件内容-bytes-123".getBytes(StandardCharsets.UTF_8);
        client.writeFile(SID, "/workspace/out/产物.txt", content);
        byte[] back = client.readFile(SID, "/workspace/out/产物.txt");
        assertArrayEquals(content, back);
        assertEquals(1, readFileQueries.size());
        String query = readFileQueries.get(0);
        assertEquals("root", queryParam(query, "username"));
        assertEquals("/workspace/out/产物.txt", queryParam(query, "path"));
    }

    // ---------------------------------------------------------------- delete / list

    @Test
    @DisplayName("delete 幂等：首次 204 成功；再删 404 静默不抛")
    void deleteIsIdempotentOn404() {
        sandboxDeletesUntilMissing = 1;
        client.delete(SID);
        client.delete(SID);
        assertEquals(2, deleteCalls, "404 也照发 DELETE（由上游判定不存在）");
    }

    @Test
    @DisplayName("listSandboxIds 按 metadata 过滤：匹配返回 id，无 metadata 项/无匹配⇒空")
    void listSandboxIdsFiltersByMetadata() {
        assertEquals(List.of("s1"), client.listSandboxIds("session-id", "sess-A"));
        assertTrue(client.listSandboxIds("session-id", "nope").isEmpty());
        assertTrue(client.listSandboxIds("other-key", "sess-A").isEmpty());
    }

    @Test
    @DisplayName("listSandboxEntries 全条目不过滤：metadata 多键原样转 Map、缺席⇒空 map；state 缺席⇒空串")
    void listSandboxEntriesReturnsAllEntries() {
        List<CubeHttpClient.SandboxEntry> entries = client.listSandboxEntries();
        assertEquals(3, entries.size(), "全条目返回（不过滤）");
        CubeHttpClient.SandboxEntry first = entries.get(0);
        assertEquals("s1", first.sandboxId());
        assertEquals(Map.of("session-id", "sess-A", "owner", "epc-ai"), first.metadata(),
                "metadata 多键原样解析");
        assertEquals("running", first.state());
        assertEquals("s2", entries.get(1).sandboxId());
        assertEquals("paused", entries.get(1).state());
        CubeHttpClient.SandboxEntry bare = entries.get(2);
        assertEquals("s3", bare.sandboxId());
        assertTrue(bare.metadata().isEmpty(), "无 metadata ⇒ 空 map（平台剥离口径）");
        assertEquals("", bare.state(), "无 state ⇒ 空串");
    }

    @Test
    @DisplayName("listSandboxEntries ≥400 ⇒ ServiceException（5xx 合法 JSON 不查状态码会被当空集——既有纪律）")
    void listSandboxEntriesThrowsOnHttpError() {
        listStatusOverride = 500;
        ServiceException e = assertThrows(ServiceException.class, () -> client.listSandboxEntries());
        assertTrue(e.getMessage().contains("500"), e.getMessage());
    }

    // ---------------------------------------------------------------- dataBaseUrl 推导（M-6）

    @Test
    @DisplayName("dataBaseUrl：显式配置覆盖；缺省由 apiUrl host 推导（http://{host}，不带端口）")
    void dataBaseUrlResolution() {
        assertEquals("http://127.0.0.1:" + server.getAddress().getPort(), client.dataBaseUrl(),
                "显式配置覆盖");
        properties.setDataBaseUrl("");
        assertEquals("http://127.0.0.1", client.dataBaseUrl(),
                "缺省=apiUrl 的 host（80 端口 path 路由零 DNS）");
        properties.setApiUrl("http://10.3.6.31:3000");
        assertEquals("http://10.3.6.31", client.dataBaseUrl(), "照探针口径：api=host:3000 ⇒ 数据面=host");
    }
}
