package com.bidr.cube.support;

import com.bidr.cube.config.CubeSandboxProperties;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Title: CubeFakeServer
 * Description: 本地回环 Cube 平台 fake（{@link HttpServer}，不打外网）——Provider/Admin
 * 单测共用（骨架复用 {@code CubeHttpClientTest.route()} 形态）。记录<b>全局有序</b>调用日志
 * {@link #ops}（create/exec/write/read/delete 各一行一事），专供调用序断言
 * （ensure＝mkdir→POST /sandboxes→files write×N）。路由口径：
 * <ul>
 *   <li>POST /sandboxes：按 body metadata 区分——{@code role=cube-admin} 归管理沙箱
 *       （回 {@link #ADMIN_ID}），否则归业务沙箱（回 {@link #SID}）；</li>
 *   <li>GET /sandboxes：按 {@link #listedSessions} 造含 metadata 的运行中列表（M-10 口径），
 *       {@link #listStatus} 可造对账失败；</li>
 *   <li>DELETE /sandboxes/{id}：live 集（create 时登记）命中回 204 并移除，否则 404；</li>
 *   <li>exec（process.Process/Start）：解 Connect 信封取命令记 ops；响应帧由
 *       {@link #setExecFrames} 配置（缺省 exit 0）；{@link #execStatus} 可造执行故障（自愈用例）；</li>
 *   <li>/files：POST 落盘内存 map＋记 ops；GET 回字节（{@link #readFileStatus} 可造 404/500）。</li>
 * </ul>
 *
 * @author Sharp
 * @since 2026/11/16
 */
public class CubeFakeServer {

    /** 常量 id：管理沙箱（fake 按 metadata.role 区分后固定分配） */
    public static final String ADMIN_ID = "admin-0001";
    /** 常量 id：业务沙箱 */
    public static final String SID = "sandbox-0002";
    private static final int ENVD_PORT = 49983;

    private static final ObjectMapper OM = new ObjectMapper();

    public final HttpServer server;
    public final CubeSandboxProperties properties;

    // ---- 全局有序调用日志（线程安全：HttpServer worker 线程写） ----
    /** 有序调用日志："create:admin"/"create:<sid>"/"exec:<cmd>"/"write:<path>"/"read:<path>"/"delete:<id>" */
    public final List<String> ops = Collections.synchronizedList(new ArrayList<>());

    // ---- 计数与记录 ----
    public final List<String> adminCreateBodies = Collections.synchronizedList(new ArrayList<>());
    public final List<String> businessCreateBodies = Collections.synchronizedList(new ArrayList<>());
    public final Set<String> liveSandboxIds = Collections.synchronizedSet(new LinkedHashSet<>());
    public final Map<String, byte[]> files = Collections.synchronizedMap(new LinkedHashMap<>());
    public final List<String> readFileQueries = Collections.synchronizedList(new ArrayList<>());
    public final List<String> execCommands = Collections.synchronizedList(new ArrayList<>());
    public int adminDeletes;
    public int businessDeletes;
    public int listCalls;

    // ---- 可配置响应 ----
    /** GET /sandboxes 会话清单（造对账命中：出现 SID ⇒ 返回 sandboxID=SID） */
    public final Set<String> listedSessions = Collections.synchronizedSet(new LinkedHashSet<>());
    public int listStatus = 200;
    public int readFileStatus = 200;
    public int execStatus = 200;
    /** 剩余一次性 exec 故障次数（消耗完恢复 execStatus；自愈用例造"失败一次后恢复"） */
    public int execFailuresRemaining;
    public long execDelayMs;
    private byte[] execResponse = endFrameJson(true, "exit status 0");

    public CubeFakeServer() throws IOException {
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
    }

    public void stop() {
        server.stop(0);
    }

    /** 配置 exec 响应帧流（data/end 帧由本类 frame 助手构造） */
    public void setExecFrames(byte[]... frames) {
        this.execResponse = concat(frames);
    }

    // ---------------------------------------------------------------- 路由

    private void route(HttpExchange exchange) throws IOException {
        String path = exchange.getRequestURI().getPath();
        String method = exchange.getRequestMethod();
        byte[] body = exchange.getRequestBody().readAllBytes();
        String query = exchange.getRequestURI().getRawQuery();
        if (path.equals("/sandboxes") && "POST".equals(method)) {
            handleCreate(exchange, body);
        } else if (path.equals("/sandboxes") && "GET".equals(method)) {
            handleList(exchange);
        } else if (path.startsWith("/sandboxes/") && "DELETE".equals(method)) {
            handleDelete(exchange, path.substring("/sandboxes/".length()));
        } else if (path.startsWith("/sandbox/") && path.endsWith("/process.Process/Start")
                && "POST".equals(method)) {
            handleExec(exchange, body);
        } else if (path.startsWith("/sandbox/") && path.endsWith("/files") && "POST".equals(method)) {
            handleWrite(exchange, query, body);
        } else if (path.startsWith("/sandbox/") && path.endsWith("/files") && "GET".equals(method)) {
            handleRead(exchange, query);
        } else {
            respond(exchange, 404, "{\"detail\":\"no route: " + path + "\"}");
        }
    }

    /** POST /sandboxes：metadata.role=cube-admin ⇒ 管理沙箱，否则业务沙箱 */
    private void handleCreate(HttpExchange exchange, byte[] body) throws IOException {
        String raw = new String(body, StandardCharsets.UTF_8);
        JsonNode metadata;
        try {
            metadata = OM.readTree(raw).path("metadata");
        } catch (IOException e) {
            respond(exchange, 400, "{\"detail\":\"bad json\"}");
            return;
        }
        boolean admin = "cube-admin".equals(metadata.path("role").asText(null));
        String id = admin ? ADMIN_ID : SID;
        (admin ? adminCreateBodies : businessCreateBodies).add(raw);
        ops.add("create:" + (admin ? "admin" : id));
        liveSandboxIds.add(id);
        respond(exchange, 201, "{\"sandboxID\":\"" + id + "\",\"domain\":\"" + id
                + ".cube.example\",\"envdVersion\":\"0.2.0\"}");
    }

    /** GET /sandboxes：M-10 口径——运行中列表带 metadata；listedSessions 命中 SID ⇒ sandboxID=SID */
    private void handleList(HttpExchange exchange) throws IOException {
        listCalls++;
        ops.add("list");
        if (listStatus != 200) {
            respond(exchange, listStatus, "{\"error\":\"list boom\"}");
            return;
        }
        StringBuilder json = new StringBuilder("[");
        boolean first = true;
        // 在册管理沙箱（role=cube-admin 标记）也入列表：M-2 重启对账复用靠它命中
        if (liveSandboxIds.contains(ADMIN_ID)) {
            json.append("{\"sandboxID\":\"").append(ADMIN_ID)
                    .append("\",\"metadata\":{\"role\":\"cube-admin\"}}");
            first = false;
        }
        synchronized (listedSessions) {
            for (String session : listedSessions) {
                if (!first) {
                    json.append(',');
                }
                first = false;
                String id = SID.equals(session) ? SID : "listed-" + session;
                json.append("{\"sandboxID\":\"").append(id)
                        .append("\",\"metadata\":{\"session-id\":\"").append(session).append("\"}}");
            }
        }
        respond(exchange, 200, json.append("]").toString());
    }

    /** DELETE /sandboxes/{id}：live 命中 204（并移除），否则 404 */
    private void handleDelete(HttpExchange exchange, String id) throws IOException {
        boolean admin = ADMIN_ID.equals(id);
        if (admin) {
            adminDeletes++;
        } else {
            businessDeletes++;
        }
        ops.add("delete:" + id);
        if (liveSandboxIds.remove(id)) {
            exchange.sendResponseHeaders(204, -1);
            exchange.close();
        } else {
            respond(exchange, 404, "{\"detail\":\"not found\"}");
        }
    }

    /** exec：解 Connect 信封记命令；execStatus 可造故障（HTTP 层失败=自愈用例的触发器） */
    private void handleExec(HttpExchange exchange, byte[] body) throws IOException {
        String command = "";
        if (body.length >= 5) {
            int len = ByteBuffer.wrap(body, 1, 4).getInt();
            if (body.length >= 5 + len) {
                try {
                    command = OM.readTree(Arrays.copyOfRange(body, 5, 5 + len))
                            .path("process").path("args").path(2).asText("");
                } catch (IOException ignored) {
                    // 解析失败按空命令记录，不影响响应
                }
            }
        }
        execCommands.add(command);
        ops.add("exec:" + command);
        if (execDelayMs > 0) {
            try {
                Thread.sleep(execDelayMs);
            } catch (InterruptedException ignored) {
                Thread.currentThread().interrupt();
            }
        }
        int status = execStatus;
        if (execFailuresRemaining > 0) {
            execFailuresRemaining--;
            status = 500;
        }
        if (status != 200) {
            respond(exchange, status, "{\"error\":\"exec boom\"}");
            return;
        }
        exchange.getResponseHeaders().set("Content-Type", "application/connect+json");
        byte[] out = execResponse;
        exchange.sendResponseHeaders(200, out.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(out);
        }
    }

    private void handleWrite(HttpExchange exchange, String query, byte[] body) throws IOException {
        String path = queryParam(query, "path");
        files.put(path, body);
        ops.add("write:" + path);
        respond(exchange, 201, "");
    }

    private void handleRead(HttpExchange exchange, String query) throws IOException {
        readFileQueries.add(query);
        String path = queryParam(query, "path");
        ops.add("read:" + path);
        if (readFileStatus != 200) {
            respond(exchange, readFileStatus, "{\"detail\":\"no such file\"}");
            return;
        }
        byte[] bytes = files.getOrDefault(path, new byte[0]);
        exchange.getResponseHeaders().set("Content-Type", "application/octet-stream");
        exchange.sendResponseHeaders(200, bytes.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(bytes);
        }
    }

    // ---------------------------------------------------------------- 助手

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

    /** 造一帧 Connect 消息：[flags 1B][len 4B 大端][payload]（与 CubeHttpClientTest 同款骨架） */
    public static byte[] frame(int flags, byte[] payload) {
        return ByteBuffer.allocate(5 + payload.length)
                .put((byte) flags)
                .putInt(payload.length)
                .put(payload)
                .array();
    }

    /** data 帧：{"event":{"data":{"stdout":...}}}（序列化失败按 IllegalStateException 包装——帧构造不会 I/O） */
    public static byte[] stdoutFrame(String stdout) {
        ObjectNode data = OM.createObjectNode().put("stdout", stdout);
        return frame(0, writeJson(OM.createObjectNode().set("event",
                OM.createObjectNode().set("data", data))));
    }

    /** data 帧（stderr） */
    public static byte[] stderrFrame(String stderr) {
        ObjectNode data = OM.createObjectNode().put("stderr", stderr);
        return frame(0, writeJson(OM.createObjectNode().set("event",
                OM.createObjectNode().set("data", data))));
    }

    /** end 帧：{"event":{"end":{...}}} */
    public static byte[] endFrame(ObjectNode end) {
        return frame(0, writeJson(OM.createObjectNode().set("event",
                OM.createObjectNode().set("end", end))));
    }

    private static byte[] writeJson(ObjectNode node) {
        try {
            return OM.writeValueAsBytes(node);
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    /** exit N 的 end 帧便捷构造 */
    public static byte[] endFrameJson(boolean exited, String status) {
        return endFrame(OM.createObjectNode().put("exited", exited).put("status", status));
    }

    /** 显式 exitCode 的 end 帧便捷构造 */
    public static byte[] endFrameExit(int exitCode) {
        return endFrame(OM.createObjectNode().put("exited", true)
                .put("exitCode", exitCode).put("status", "exit status " + exitCode));
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
}
