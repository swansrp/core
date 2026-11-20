package com.bidr.mcp.client;

import java.util.Map;

/**
 * Title: McpToolClient
 * Description: 通用 MCP 工具调用端口。业务层按 serverKey 调外部 MCP Server 的工具，
 * 连接管理、凭证、重试与错误归一化由 {@link McpClientManager} 负责。
 *
 * @author sharp
 * @since 2026/11/20
 */
public interface McpToolClient {

    /**
     * 指定 serverKey 是否可用（配置存在、enabled、baseUrl/appKey/appSecret 齐全）
     *
     * @param serverKey 业务侧逻辑名
     * @return true＝可以调用
     */
    boolean hasServer(String serverKey);

    /**
     * 调用工具并返回结果文本（信封 JSON 原样）
     *
     * @param serverKey 业务侧逻辑名
     * @param toolName  MCP 工具名
     * @param arguments 工具参数（可为 null，按空 map 处理）
     * @return TextContent 文本拼接结果
     * @throws McpToolException 配置缺失、业务错误或传输失败（重试后）
     */
    String callTool(String serverKey, String toolName, Map<String, Object> arguments);
}