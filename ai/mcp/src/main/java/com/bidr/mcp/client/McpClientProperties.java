package com.bidr.mcp.client;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Title: McpClientProperties
 * Description: 通用 MCP 客户端连接配置（{@code my.mcp.clients.*}）。一个 serverKey 对应一套
 * 外部 MCP Server 的连接参数；业务层只认 serverKey，不感知 SSE/凭证细节。
 *
 * <p>键位与既有 {@code my.mcp.path} / {@code my.mcp.keepalive-seconds}（本模块服务端装配用）
 * 同前缀共存：本类只绑定 {@code clients} 子树，标量键由各自的 {@code @Value} 读取，互不影响。</p>
 *
 * <p>默认值口径：{@code enabled=true}、{@code sseEndpoint=/mcp}、连接 5s、初始化 10s、
 * 单次请求 30s；{@code baseUrl/appKey/appSecret} 未填即视为未配置（{@code hasServer=false}）。</p>
 *
 * @author sharp
 * @since 2026/11/20
 */
@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "my.mcp")
public class McpClientProperties {

    /**
     * serverKey → Server 连接配置；serverKey 为业务侧逻辑名（如 {@code inkhub-kb}）
     */
    private Map<String, Server> clients = new LinkedHashMap<>();

    /**
     * 单个 MCP Server 的连接参数
     */
    @Getter
    @Setter
    public static class Server {

        /**
         * 是否启用；false 时 hasServer=false，业务层按「未配置」降级
         */
        private boolean enabled = true;

        /**
         * 服务根地址（如 {@code http://10.3.6.60/inkhub}），不含 SSE 端点
         */
        private String baseUrl;

        /**
         * SSE 端点；可写相对路径（{@code /mcp/inkhub}，由客户端拼成绝对 URL）或绝对 URL
         */
        private String sseEndpoint = "/mcp";

        /**
         * 凭证 appKey（与 appSecret 拼 Base64 后放 {@code x-api-key} 头）
         */
        private String appKey;

        /**
         * 凭证 appSecret；任何日志不得输出
         */
        private String appSecret;

        /**
         * 建连超时（毫秒）
         */
        private long connectTimeoutMs = 5000L;

        /**
         * 初始化（initialize 握手）超时（毫秒）
         */
        private long initializationTimeoutMs = 10000L;

        /**
         * 单次工具请求超时（毫秒）
         */
        private long requestTimeoutMs = 30000L;
    }
}