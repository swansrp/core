package com.bidr.mcp.registry;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * Title: McpEndpointDefinition
 * Description: 端点类型化元数据：一个 {@code @McpServerEndpoint} bean = 一个 SSE 端点
 * = 一套 transport + McpServer。替代旧实现里散在 Solon Provider 私有字段与反射里
 * 的全部信息，台账同步（sys_mcp）与描述热更新都在这层操作。
 *
 * @author sharp
 * @since 2026/11/14
 */
@Data
public class McpEndpointDefinition {

    /**
     * 端点类全名（sys_mcp.end_point，与旧实现同值）
     */
    private final String endpointClassName;

    /**
     * 端点显示名（注解 name，sys_mcp.end_point_name；亦作 serverInfo 名）
     */
    private final String name;

    /**
     * SSE 建连路径（注解 sseEndpoint）
     */
    private final String sseEndpoint;

    /**
     * 端点 bean 实例（工具方法反射调用的宿主）
     */
    private final Object bean;

    private final List<McpToolDefinition> tools = new ArrayList<>();
}
