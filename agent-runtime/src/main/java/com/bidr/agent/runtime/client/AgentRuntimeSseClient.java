package com.bidr.agent.runtime.client;

import com.bidr.agent.runtime.config.AgentRuntimeConfigProvider;
import lombok.extern.slf4j.Slf4j;
import org.springframework.util.StringUtils;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * Title: AgentRuntimeSseClient
 * Description: 上游**单轮流 SSE** 客户端（§5.7 票 13）：只负责「开流 → 逐帧取原始 JSON → 收流」，
 * 不解析帧语义（relay 原样透传，未知帧自动兼容）。
 * <p>
 * 实现要点：
 * <ul>
 * <li>用 JDK {@link HttpURLConnection}（零新依赖，便于整模块下沉；框架 core/llm 的 okhttp 口径不引入）；</li>
 * <li>{@code open()} **同步**读响应头：据此判定「开流前错误」（非 200 → 读 JSON 信封），
 * 这是 relay 能把开流前错误回成 JSON 而非 SSE 的前提；</li>
 * <li>空闲读超时 = {@code my.agent.runtime.idle-timeout-ms}（平台 15s 心跳，超时即视为断流）；
 * 读超时/连接异常一律作 {@code IOException} 上抛，由 relay 收流，交由客户端挂流恢复；</li>
 * <li>{@code : ping} 注释行忽略；空行分事件；一行或多行 {@code data:} 按 \n 拼回原文。</li>
 * </ul>
 *
 * @author sharp
 * @since 2026/9/22
 */
@Slf4j
public class AgentRuntimeSseClient {

    private final AgentRuntimeConfigProvider config;

    public AgentRuntimeSseClient(AgentRuntimeConfigProvider config) {
        this.config = config;
    }

    /**
     * 开流（同步返回响应头信息；此时流尚未消费）
     *
     * @param method         GET（挂流恢复）或 POST（建轮即流）
     * @param path           相对接口前缀的路径，如 /sessions/{sid}/messages:stream
     * @param jsonBody       请求体（POST 时必需）
     * @param idempotencyKey 幂等键（可空；平台据此「同键同体复用同一轮次」）
     */
    public UpstreamStream open(String method, String path, String jsonBody, String idempotencyKey) throws IOException {
        URL url = new URL(config.requireBaseUrl() + config.getApiPrefix() + path);
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        connection.setRequestMethod(method);
        connection.setConnectTimeout(config.getConnectTimeoutMs());
        // 空闲读超时：平台 15s 心跳，超时即判断流（不设全量超时，长轮次可达数分钟）
        connection.setReadTimeout(config.getIdleTimeoutMs());
        connection.setRequestProperty("Authorization", "Bearer " + config.requireApiKey());
        connection.setRequestProperty("Accept", "text/event-stream");
        if (StringUtils.hasText(idempotencyKey)) {
            connection.setRequestProperty(AgentRuntimeRestClient.HEADER_IDEMPOTENCY_KEY, idempotencyKey);
        }
        if (jsonBody != null) {
            connection.setDoOutput(true);
            connection.setRequestProperty("Content-Type", "application/json; charset=utf-8");
            connection.getOutputStream().write(jsonBody.getBytes(StandardCharsets.UTF_8));
        }

        int status;
        try {
            status = connection.getResponseCode();
        } catch (IOException e) {
            // POST 带体时 JDK 遇 401 抛 HttpRetryException（拿不到响应体）：按状态码 401 交回 relay
            if (AgentRuntimeErrors.isUnauthorized(e)) {
                return UpstreamStream.preStreamError(connection, 401, "");
            }
            throw e;
        }
        String contentType = connection.getContentType();
        if (status != HttpURLConnection.HTTP_OK) {
            return UpstreamStream.preStreamError(connection, status, readFully(connection.getErrorStream()));
        }
        if (contentType == null || !contentType.toLowerCase().contains("text/event-stream")) {
            return UpstreamStream.preStreamError(connection, status, readFully(connection.getInputStream()));
        }
        return UpstreamStream.streaming(connection, status, contentType);
    }

    private static String readFully(InputStream input) {
        if (input == null) {
            return "";
        }
        try (InputStream in = input) {
            byte[] buffer = new byte[4096];
            StringBuilder text = new StringBuilder();
            int read;
            while ((read = in.read(buffer)) >= 0) {
                text.append(new String(buffer, 0, read, StandardCharsets.UTF_8));
            }
            return text.toString();
        } catch (IOException e) {
            return "";
        }
    }

    /**
     * 已开的上游流：{@link #readFrameJson()} 逐帧取原始 JSON（null = 已收流）
     */
    public static final class UpstreamStream implements Closeable {

        private final HttpURLConnection connection;
        private final int status;
        private final String contentType;
        private final String errorBody;
        private final BufferedReader reader;
        private final List<String> dataLines = new ArrayList<>();
        private boolean ended;

        private UpstreamStream(HttpURLConnection connection, int status, String contentType,
                               String errorBody, BufferedReader reader) {
            this.connection = connection;
            this.status = status;
            this.contentType = contentType;
            this.errorBody = errorBody;
            this.reader = reader;
        }

        static UpstreamStream preStreamError(HttpURLConnection connection, int status, String errorBody) {
            return new UpstreamStream(connection, status, connection.getContentType(), errorBody, null);
        }

        static UpstreamStream streaming(HttpURLConnection connection, int status, String contentType) throws IOException {
            return new UpstreamStream(connection, status, contentType, null,
                    new BufferedReader(new InputStreamReader(connection.getInputStream(), StandardCharsets.UTF_8)));
        }

        public boolean isEventStream() {
            return reader != null;
        }

        public int getStatus() {
            return status;
        }

        public String getContentType() {
            return contentType;
        }

        /**
         * 开流前错误的响应体（JSON 信封原文）
         */
        public String getErrorBody() {
            return errorBody;
        }

        /**
         * 取下一帧原始 JSON；收流（正常/异常）返回 null。
         * 读取异常（含空闲超时）以 {@link IOException} 上抛 —— relay 据此收流走挂流恢复。
         */
        public String readFrameJson() throws IOException {
            if (reader == null || ended) {
                return null;
            }
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.isEmpty()) {
                    String payload = flush();
                    if (payload != null) {
                        return payload;
                    }
                    continue;
                }
                if (line.charAt(0) == ':') {
                    continue; // 注释（`: ping` 心跳）
                }
                if (line.startsWith("data:")) {
                    String value = line.substring(5);
                    dataLines.add(value.startsWith(" ") ? value.substring(1) : value);
                }
                // 其他字段（event:/id:/retry:）协议无，忽略
            }
            ended = true;
            return flush();
        }

        private String flush() {
            if (dataLines.isEmpty()) {
                return null;
            }
            String payload = String.join("\n", dataLines);
            dataLines.clear();
            return payload;
        }

        @Override
        public void close() {
            ended = true;
            if (reader != null) {
                try {
                    reader.close();
                } catch (IOException ignored) {
                    // 收流即忽略
                }
            }
            try {
                connection.disconnect();
            } catch (Exception ignored) {
                // 忽略
            }
        }
    }
}