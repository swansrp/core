package com.bidr.mcp.anno;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Title: McpServerEndpoint
 * Description: MCP 服务端点声明（类级）。实现 {@code IMcpServerEndpoint} 的 Spring bean
 * 打上本注解即被 {@code McpServerConfig} 收集为独立端点：一个注解类 = 一个 SSE 端点 +
 * 一套 JSON-RPC 会话。注解名与语义沿用旧 Solon 注解，业务侧迁移只换 import。
 *
 * @author sharp
 * @since 2026/11/14
 */
@Documented
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
public @interface McpServerEndpoint {

    /**
     * 端点显示名（写入 sys_mcp.end_point_name，亦作为 serverInfo 名）
     */
    String name();

    /**
     * SSE 建连路径，须落在 ${my.mcp.path:/mcp} 前缀下，如 /mcp/supervision-log；
     * message 端点固定派生为其下的 /message
     */
    String sseEndpoint();
}
