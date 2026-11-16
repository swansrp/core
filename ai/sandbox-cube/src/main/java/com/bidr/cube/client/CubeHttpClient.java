package com.bidr.cube.client;

import com.bidr.cube.config.CubeSandboxProperties;
import com.bidr.kernel.exception.ServiceException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Title: CubeHttpClient
 * Description: Cube 平台 HTTP 客户端（命令面/文件面的传输底座）——纯 JDK17 内置 HttpClient
 * 零 SDK 可完成全部调用（探针已证）。双面路由：
 * <ul>
 *   <li>命令面（create/delete/list）＝{@code {apiUrl}}＋{@code X-API-Key} 头；</li>
 *   <li>数据面（exec/files）＝{@code {dataBaseUrl}/sandbox/{sid}/{port}{path}}＋envd 端口
 *       49983＋{@code Authorization: Basic root:} 头；{@code dataBaseUrl} 缺省由 apiUrl 的
 *       host 推导（{@code http://{host}}，走 80 端口 path 路由零 DNS），显式配置覆盖。</li>
 * </ul>
 * 🔴 实测坑（探针 L30）：JDK HttpClient 必须显式 {@code proxy(NO_PROXY)}——系统代理会把
 * 数据面直连流量劫持到代理机，连接全挂。
 * <p>
 * exec 走 Connect 信封：请求 {@code [flags 1B=0][len 4B 大端][JSON]}＋四头
 * （Content-Type: application/connect+json / Connect-Protocol-Version: 1 /
 * Connect-Content-Encoding: identity / Authorization: Basic root:）；响应是
 * {@code [flags][len][payload]} 帧流（data→end，尾帧 flags&amp;0x02 为 end-stream，
 * 带 error 字段即抛）。exit 0 时 end 帧无 {@code exitCode} 字段，兜底解析
 * {@code status} 的 {@code exit status N}；stdout/stderr 双形态兼容（base64 或明文）。
 *
 * @author Sharp
 * @since 2026/11/16
 */
public class CubeHttpClient {

    /** envd 数据面端口（exec Connect-RPC 与 /files 都挂在这里，探针实测） */
    private static final int ENVD_PORT = 49983;

    /** 管理沙箱 metadata 标记键值（role=cube-admin）：对账/清理不误伤业务会话 */
    public static final String ADMIN_METADATA_ROLE = "cube-admin";

    /** create 重试总次数（探针 ×3＋500ms 固定；生产 ×4＋指数退避起步 500ms——500/1000/2000） */
    private static final int CREATE_ATTEMPTS = 4;

    /** create 重试退避起步毫秒（指数：500 * 2^(attempt-1)） */
    private static final long CREATE_BACKOFF_BASE_MS = 500;

    /** 管理面/文件面 HTTP 请求超时（秒）；create 语义的沙箱保活 TTL 另由 adminTimeout 下发 */
    private static final Duration CALL_TIMEOUT = Duration.ofSeconds(60);

    /** delete 单独放宽到 30s（探针口径） */
    private static final Duration DELETE_TIMEOUT = Duration.ofSeconds(30);

    /** connect 尾帧标志位：flags & END_STREAM = end-stream（trailer，可能带 error） */
    private static final int FLAG_END_STREAM = 0x02;

    /** base64 保守判定（plan 口径）：全匹配才解码，否则按明文字节——比探针的「解码回读看控制字符」更不误伤明文 */
    private static final Pattern BASE64_PATTERN = Pattern.compile("^[A-Za-z0-9+/]+={0,2}$");

    private static final ObjectMapper OM = new ObjectMapper();

    /** JDK HttpClient（🔴NO_PROXY：系统代理劫持实测坑，见类注释） */
    private final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .proxy(HttpClient.Builder.NO_PROXY)
            .build();

    private final CubeSandboxProperties properties;

    public CubeHttpClient(CubeSandboxProperties properties) {
        this.properties = properties;
    }

    // ---------------------------------------------------------------- 契约记录（棒3 消费，不得改形状）

    /**
     * create 成功产物：{@code sandboxID}/{@code domain}/{@code envdVersion}（字段名照平台响应）。
     */
    public record CreateResult(String sandboxId, String domain, String envdVersion) {
    }

    /**
     * exec 产物：exitCode（超时为 -1）、stdout/stderr 文本、timedOut。
     */
    public record ExecResult(int exitCode, String stdout, String stderr, boolean timedOut) {
    }

    // ---------------------------------------------------------------- 命令面（走 apiUrl + X-API-Key）

    /**
     * 建沙箱：body {@code {templateID, timeout, metadata, network?}}。metadata 内
     * {@code session-id}＋{@code host-mount}（🔴host-mount 是 JSON <b>字符串</b>，数组 422——
     * 探针实测）；allowOut 非空时下发顶层 {@code network.allowOut}（camelCase 线格式，
     * 照 SDK _policy.py），空则整键不下发。
     * <p>
     * 瞬态 500/网络异常按 ×4＋指数退避（500ms 起步）重试；4xx 直接抛不重试。
     *
     * @param sessionId 会话 id（写进 metadata.session-id，宿主挂载目录为 {hostRoot}/{sessionId}）
     */
    public CreateResult create(String sessionId) {
        return createWithBody(buildCreateBody(sessionId));
    }

    /**
     * 建常驻管理沙箱（{@code AdminSandboxManager} 专用）：挂载 {@code {hostRoot} → {hostRoot}}
     * （内外路径一致 ⇒ mkdir 的路径即业务沙箱 hostPath，无需换算）；metadata 只带
     * {@code role=cube-admin} 标记键（对账/清理不误伤——业务对账键是 session-id）；
     * 不下发 network.allowOut（管理面只做 mkdir）。重试口径与 {@link #create(String)} 相同
     * （瞬态 ×4＋指数退避；4xx 直接抛）。
     */
    public CreateResult createAdmin() {
        return createWithBody(buildAdminCreateBody());
    }

    /** create 共干：请求循环与重试口径（create/createAdmin 唯一差异是 body 形状） */
    private CreateResult createWithBody(byte[] body) {
        RuntimeException last = new ServiceException("create 沙箱未执行任何请求");
        for (int attempt = 0; attempt < CREATE_ATTEMPTS; attempt++) {
            if (attempt > 0) {
                long backoff = CREATE_BACKOFF_BASE_MS << (attempt - 1);
                try {
                    Thread.sleep(backoff);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new ServiceException("create 沙箱重试等待被中断: " + e.getMessage());
                }
            }
            HttpRequest request = adminBuilder(adminBase() + "/sandboxes")
                    .header("Content-Type", "application/json")
                    .timeout(CALL_TIMEOUT)
                    .POST(HttpRequest.BodyPublishers.ofByteArray(body))
                    .build();
            try {
                HttpResponse<String> resp = http.send(request, HttpResponse.BodyHandlers.ofString());
                int code = resp.statusCode();
                if (code >= 200 && code < 300) {
                    JsonNode node = OM.readTree(resp.body());
                    return new CreateResult(
                            node.path("sandboxID").asText(""),
                            node.path("domain").asText(""),
                            node.path("envdVersion").asText(""));
                }
                if (code >= 500) {
                    // 瞬态：记录后重试（探针实测偶发 500，重试即成）
                    last = new ServiceException("create 沙箱失败 HTTP " + code + ": " + resp.body());
                    continue;
                }
                // 4xx 是确定性失败，不重试
                throw new ServiceException("create 沙箱失败 HTTP " + code + "（不重试）: " + resp.body());
            } catch (IOException e) {
                // 网络异常视为瞬态，重试
                last = new ServiceException("create 沙箱网络异常: " + e.getMessage());
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new ServiceException("create 沙箱请求被中断: " + e.getMessage());
            }
        }
        throw last;
    }

    /**
     * 删沙箱：DELETE {@code /sandboxes/{id}}。<b>幂等</b>：404（已销毁/不存在）静默返回。
     */
    public void delete(String sandboxId) {
        HttpRequest request = adminBuilder(adminBase() + "/sandboxes/" + sandboxId)
                .timeout(DELETE_TIMEOUT)
                .DELETE()
                .build();
        HttpResponse<String> resp = send(request, "delete 沙箱");
        int code = resp.statusCode();
        if (code == 404) {
            return;
        }
        if (code >= 400) {
            throw new ServiceException("delete 沙箱失败 HTTP " + code + ": " + resp.body());
        }
    }

    /**
     * 列出沙箱并按 metadata 过滤（对账/兜底重建用）：metadata[{@code metadataKey}] 等于
     * {@code metadataValue} 的 {@code sandboxID} 集合。
     */
    public List<String> listSandboxIds(String metadataKey, String metadataValue) {
        HttpRequest request = adminBuilder(adminBase() + "/sandboxes")
                .timeout(CALL_TIMEOUT)
                .GET()
                .build();
        HttpResponse<String> resp = send(request, "list 沙箱");
        // HTTP 错误必须上抛（调用方 Provider 的 D3 对账降级依赖"异常"信号）：5xx 响应体
        // 可能是合法 JSON（{"error":...}），不查状态码会被当数组迭代静默返回空集
        if (resp.statusCode() >= 400) {
            throw new ServiceException("list 沙箱失败 HTTP " + resp.statusCode() + ": " + resp.body());
        }
        try {
            List<String> ids = new ArrayList<>();
            for (JsonNode node : OM.readTree(resp.body())) {
                String value = node.path("metadata").path(metadataKey).asText(null);
                if (metadataValue.equals(value)) {
                    ids.add(node.path("sandboxID").asText(""));
                }
            }
            return ids;
        } catch (IOException e) {
            throw new ServiceException("list 沙箱响应解析失败: " + e.getMessage());
        }
    }

    // ---------------------------------------------------------------- 数据面（走 dataBaseUrl + Basic root:）

    /**
     * 沙箱内执行命令（Connect 信封）：{@code [flags=0][len 4B 大端][JSON]}，JSON 形状
     * {@code {"process":{"cmd":"/bin/bash","args":["-l","-c",CMD],"envs":{},"cwd":""},"stdin":false}}。
     * <p>
     * HTTP 请求超时 ⇒ {@code ExecResult(-1, "", "", true)}。<b>注意</b>：超时只断开本次 HTTP，
     * 远程进程不显式 kill——兜底＝沙箱 idle 回收（到期整沙箱销毁，进程随容器消亡），
     * 调用方拿到 timedOut 后不应再依赖该进程产物。
     *
     * @param sandboxId 沙箱实例 id
     * @param command   shell 命令（经 {@code /bin/bash -l -c} 执行）
     * @param timeoutMs 本次调用的 HTTP 超时（毫秒）
     */
    public ExecResult exec(String sandboxId, String command, long timeoutMs) {
        byte[] json = buildExecPayload(command);
        byte[] envelope = ByteBuffer.allocate(5 + json.length)
                .put((byte) 0)
                .putInt(json.length)
                .put(json)
                .array();
        HttpRequest request = HttpRequest.newBuilder(URI.create(dataBaseUrl() + "/sandbox/" + sandboxId
                        + "/" + ENVD_PORT + "/process.Process/Start"))
                .header("Content-Type", "application/connect+json")
                .header("Connect-Protocol-Version", "1")
                .header("Connect-Content-Encoding", "identity")
                .header("Authorization", basicRoot())
                .timeout(Duration.ofMillis(timeoutMs))
                .POST(HttpRequest.BodyPublishers.ofByteArray(envelope))
                .build();
        HttpResponse<byte[]> resp;
        try {
            resp = http.send(request, HttpResponse.BodyHandlers.ofByteArray());
        } catch (HttpTimeoutException e) {
            return new ExecResult(-1, "", "", true);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new ServiceException("exec 被中断: " + e.getMessage());
        } catch (IOException e) {
            throw new ServiceException("exec 网络异常: " + e.getMessage());
        }
        if (resp.statusCode() >= 400) {
            throw new ServiceException("exec 失败 HTTP " + resp.statusCode() + ": "
                    + new String(resp.body(), StandardCharsets.UTF_8));
        }
        return parseConnectStream(resp.body());
    }

    /**
     * 读沙箱内文件字节：GET {@code /files?path=&username=root}。
     */
    public byte[] readFile(String sandboxId, String path) {
        HttpRequest request = filesBuilder(sandboxId, path)
                .timeout(CALL_TIMEOUT)
                .GET()
                .build();
        HttpResponse<byte[]> resp = sendBytes(request, "readFile");
        if (resp.statusCode() >= 400) {
            // 🔴 固定前缀「readFile 失败 HTTP <code>:」——Provider 的 isNotFound 按前缀精确判定
            // 404（M-1），path 只允许出现在 detail 段（path 含 "HTTP 404" 字样也不得干扰判定）
            throw new ServiceException("readFile 失败 HTTP " + resp.statusCode() + ": path=" + path
                    + ", body=" + new String(resp.body(), StandardCharsets.UTF_8));
        }
        return resp.body();
    }

    /**
     * 写沙箱内文件：POST {@code /files?path=&username=root}，octet-stream body
     * （200/201 均算成功，探针口径）。
     */
    public void writeFile(String sandboxId, String path, byte[] content) {
        HttpRequest request = filesBuilder(sandboxId, path)
                .timeout(CALL_TIMEOUT)
                .header("Content-Type", "application/octet-stream")
                .POST(HttpRequest.BodyPublishers.ofByteArray(content))
                .build();
        HttpResponse<byte[]> resp = sendBytes(request, "writeFile");
        int code = resp.statusCode();
        if (code != 200 && code != 201) {
            throw new ServiceException("writeFile 失败 HTTP " + code + " path=" + path
                    + ": " + new String(resp.body(), StandardCharsets.UTF_8));
        }
    }

    // ---------------------------------------------------------------- 内部

    /** 命令面请求基础 builder（X-API-Key；apiKey 未配则不带） */
    private HttpRequest.Builder adminBuilder(String url) {
        HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(url));
        if (properties.getApiKey() != null && !properties.getApiKey().isBlank()) {
            builder.header("X-API-Key", properties.getApiKey());
        }
        return builder;
    }

    /** 数据面 /files 请求基础 builder（Basic root: 鉴权＋path 查询参数编码） */
    private HttpRequest.Builder filesBuilder(String sandboxId, String path) {
        String encoded = URLEncoder.encode(path, StandardCharsets.UTF_8)
                .replace("+", "%20")
                .replace("%2F", "/");
        return HttpRequest.newBuilder(URI.create(dataBaseUrl() + "/sandbox/" + sandboxId
                        + "/" + ENVD_PORT + "/files?path=" + encoded + "&username=root"))
                .header("Authorization", basicRoot());
    }

    /** create body：{templateID, timeout, metadata{session-id, host-mount JSON 字符串}, network?} */
    private byte[] buildCreateBody(String sessionId) {
        ArrayNode mounts = OM.createArrayNode()
                .add(mount(hostRoot() + "/" + sessionId, "/workspace"));
        ObjectNode metadata = OM.createObjectNode();
        metadata.put("session-id", sessionId);
        ObjectNode payload = OM.createObjectNode();
        payload.put("templateID", properties.getTemplate());
        payload.put("timeout", properties.getAdminTimeout());
        payload.set("metadata", metadata);
        List<String> allowOut = properties.getAllowOut();
        if (allowOut != null && !allowOut.isEmpty()) {
            // M-9：网络放行＝create body 顶层 network.allowOut（camelCase；空则整键不下发）
            ArrayNode allow = OM.createArrayNode();
            allowOut.forEach(allow::add);
            payload.set("network", OM.createObjectNode().set("allowOut", allow));
        }
        return serializeCreateBody(payload, metadata, mounts);
    }

    /** 管理沙箱 create body：挂载 {hostRoot}→{hostRoot}（内外一致），metadata 只带 role=cube-admin 标记 */
    private byte[] buildAdminCreateBody() {
        ArrayNode mounts = OM.createArrayNode().add(mount(hostRoot(), hostRoot()));
        ObjectNode metadata = OM.createObjectNode();
        metadata.put("role", ADMIN_METADATA_ROLE);
        ObjectNode payload = OM.createObjectNode();
        payload.put("templateID", properties.getTemplate());
        payload.put("timeout", properties.getAdminTimeout());
        payload.set("metadata", metadata);
        return serializeCreateBody(payload, metadata, mounts);
    }

    /** host-mount 挂载项（业务/管理两型 create 共用形状） */
    private static ObjectNode mount(String hostPath, String mountPath) {
        return OM.createObjectNode()
                .put("hostPath", hostPath)
                .put("mountPath", mountPath)
                .put("readOnly", false);
    }

    /** 尾段公共：🔴 host-mount 必须是 JSON 字符串（数组 422——探针实测），随 metadata 序列化整 body */
    private static byte[] serializeCreateBody(ObjectNode payload, ObjectNode metadata, ArrayNode mounts) {
        try {
            metadata.put("host-mount", OM.writeValueAsString(mounts));
            return OM.writeValueAsBytes(payload);
        } catch (IOException e) {
            throw new ServiceException("create body 序列化失败: " + e.getMessage());
        }
    }

    /** exec 请求 JSON：{"process":{cmd,args,envs,cwd},"stdin":false} */
    private byte[] buildExecPayload(String command) {
        ObjectNode process = OM.createObjectNode();
        process.put("cmd", "/bin/bash");
        process.set("args", OM.createArrayNode().add("-l").add("-c").add(command));
        process.set("envs", OM.createObjectNode());
        process.put("cwd", "");
        ObjectNode payload = OM.createObjectNode();
        payload.set("process", process);
        payload.put("stdin", false);
        try {
            return OM.writeValueAsBytes(payload);
        } catch (IOException e) {
            throw new ServiceException("exec payload 序列化失败: " + e.getMessage());
        }
    }

    /**
     * Connect 响应帧流解析：循环 {@code [flags][len 4B 大端][payload]}。
     * data 帧取 {@code event.data.stdout/stderr}（base64/明文双形态）；end 帧取
     * {@code event.end.exitCode}，缺席时兜底解析 {@code status} 的 {@code exit status N}
     * （🔴exit 0 无 exitCode 字段——探针实测）；end-stream（flags&amp;0x02）带 error 即抛。
     */
    private ExecResult parseConnectStream(byte[] body) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ByteArrayOutputStream err = new ByteArrayOutputStream();
        int exitCode = 0;
        int i = 0;
        while (i + 5 <= body.length) {
            int flags = body[i] & 0xFF;
            int len = ByteBuffer.wrap(body, i + 1, 4).getInt();
            if (i + 5 + len > body.length) {
                break;
            }
            byte[] raw = new byte[len];
            System.arraycopy(body, i + 5, raw, 0, len);
            i += 5 + len;
            if ((flags & FLAG_END_STREAM) != 0) {
                // end-stream（trailer）：带 error 字段即抛
                if (len > 0) {
                    try {
                        JsonNode node = OM.readTree(raw);
                        if (node.has("error")) {
                            throw new ServiceException("exec end-stream error: " + node.get("error"));
                        }
                    } catch (IOException e) {
                        throw new ServiceException("exec end-stream 解析失败: " + e.getMessage());
                    }
                }
                continue;
            }
            JsonNode event;
            try {
                event = OM.readTree(raw).path("event");
            } catch (IOException e) {
                throw new ServiceException("exec 事件帧解析失败: " + e.getMessage());
            }
            JsonNode data = event.path("data");
            try {
                if (data.has("stdout")) {
                    out.write(decodeMaybeB64(data.get("stdout").asText()));
                }
                if (data.has("stderr")) {
                    err.write(decodeMaybeB64(data.get("stderr").asText()));
                }
            } catch (IOException e) {
                throw new ServiceException("exec 输出累积失败: " + e.getMessage());
            }
            JsonNode end = event.path("end");
            if (end.has("exitCode")) {
                exitCode = end.get("exitCode").asInt();
            } else if (end.path("exited").asBoolean(false)) {
                String status = end.path("status").asText("");
                exitCode = status.matches("exit status \\d+")
                        ? Integer.parseInt(status.replaceAll("\\D+", ""))
                        : 0;
            }
        }
        return new ExecResult(exitCode,
                out.toString(StandardCharsets.UTF_8),
                err.toString(StandardCharsets.UTF_8),
                false);
    }

    /**
     * base64/明文双形态解码（plan 保守口径）：整串匹配 {@code ^[A-Za-z0-9+/]+={0,2}$} 且
     * 长度是 4 的倍数才按 base64 解码；解码失败（IllegalArgumentException）回退按明文字节，
     * 不崩。不满足两道预筛（含空白/标点、长度非 4 倍数）直接按明文字节。
     * <p>
     * 残余歧义：长度恰为 4 的倍数的纯字母数字明文（如 {@code "abcd1234"}）仍会被当 base64
     * 解成乱码——双形态固有局限，留 M2 校准（如解码回读看控制字符的启发式）。
     */
    private static byte[] decodeMaybeB64(String value) {
        if (value.length() % 4 == 0 && BASE64_PATTERN.matcher(value).matches()) {
            try {
                return Base64.getDecoder().decode(value);
            } catch (IllegalArgumentException e) {
                // 正则+长度预筛双过仍解码失败（边缘填充形态）：按明文字节兜底，不向上抛
                return value.getBytes(StandardCharsets.UTF_8);
            }
        }
        return value.getBytes(StandardCharsets.UTF_8);
    }

    /** 命令面基址：apiUrl 去尾斜杠（缺席即未接线，抛 ServiceException） */
    private String adminBase() {
        String apiUrl = properties.getApiUrl();
        if (apiUrl == null || apiUrl.isBlank()) {
            throw new ServiceException("Cube 沙箱 apiUrl 未配置");
        }
        return trimTrailingSlash(apiUrl);
    }

    /**
     * 数据面基址：{@code dataBaseUrl} 显式配置覆盖；缺省由 apiUrl 的 host 推导
     * （{@code http://{host}}——数据面走 80 端口 path 路由，零 DNS，探针口径）。
     * 包内可见供单测断言推导口径。
     */
    String dataBaseUrl() {
        String explicit = properties.getDataBaseUrl();
        if (explicit != null && !explicit.isBlank()) {
            return trimTrailingSlash(explicit);
        }
        URI api = URI.create(adminBase());
        if (api.getHost() == null) {
            throw new ServiceException("dataBaseUrl 缺省推导失败：apiUrl 无 host: " + properties.getApiUrl());
        }
        return "http://" + api.getHost();
    }

    private static String trimTrailingSlash(String url) {
        return url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
    }

    /** 宿主挂载根（去尾斜杠）：会话目录＝{hostRoot}/{sessionId} */
    private String hostRoot() {
        String hostRoot = properties.getHostRoot();
        if (hostRoot == null || hostRoot.isBlank()) {
            return "/data/sandbox";
        }
        return hostRoot.endsWith("/") ? hostRoot.substring(0, hostRoot.length() - 1) : hostRoot;
    }

    private static String basicRoot() {
        return "Basic " + Base64.getEncoder().encodeToString("root:".getBytes(StandardCharsets.UTF_8));
    }

    private HttpResponse<String> send(HttpRequest request, String action) {
        try {
            return http.send(request, HttpResponse.BodyHandlers.ofString());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new ServiceException(action + " 被中断: " + e.getMessage());
        } catch (IOException e) {
            throw new ServiceException(action + " 网络异常: " + e.getMessage());
        }
    }

    private HttpResponse<byte[]> sendBytes(HttpRequest request, String action) {
        try {
            return http.send(request, HttpResponse.BodyHandlers.ofByteArray());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new ServiceException(action + " 被中断: " + e.getMessage());
        } catch (IOException e) {
            throw new ServiceException(action + " 网络异常: " + e.getMessage());
        }
    }
}
