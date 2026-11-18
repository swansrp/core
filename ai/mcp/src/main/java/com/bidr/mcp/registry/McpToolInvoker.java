package com.bidr.mcp.registry;

import com.bidr.kernel.utils.JsonUtil;
import com.bidr.mcp.config.McpResultConverter;
import com.bidr.mcp.context.McpCallContext;
import io.modelcontextprotocol.spec.McpSchema;
import io.modelcontextprotocol.server.McpServerFeatures;
import io.modelcontextprotocol.server.McpSyncServerExchange;
import lombok.extern.slf4j.Slf4j;

import java.lang.reflect.Method;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Title: McpToolInvoker
 * Description: 工具调用管线：绑定参数 → 记请求日志 → 反射调起业务方法 → 记响应日志 →
 * 结果转 JSON 文本 content。日志格式与旧 {@code McpRequestLogAspect} 逐字一致
 * （=======>[MCP] 前缀、参数名取 @Param description 的旧口径、&lt;======= 响应行），
 * 只是落点从 AspectJ 切面挪进调用器——不再依赖 Spring AOP 代理链。
 *
 * <p>业务方法抛出的异常原样上抛，由 SDK 协议层转成 JSON-RPC 错误信封（与旧实现
 * 「绑定失败抛 IllegalArgumentException」的语义对齐）。</p>
 *
 * @author sharp
 * @since 2026/11/14
 */
@Slf4j
public final class McpToolInvoker {

    private McpToolInvoker() {
    }

    /**
     * 构建挂到 McpServer 的工具规格（handler 闭包持有端点 bean 与工具元数据）
     */
    public static McpServerFeatures.SyncToolSpecification toSpecification(McpEndpointDefinition endpoint,
                                                                           McpToolDefinition tool) {
        McpSchema.Tool toolRecord = toToolRecord(tool);
        return new McpServerFeatures.SyncToolSpecification(toolRecord, (exchange, request) ->
                invoke(endpoint, tool, exchange, request.arguments()));
    }

    /**
     * 用当前元数据重建工具记录（描述热更新后 removeTool + addTool 用）
     */
    public static McpSchema.Tool toToolRecord(McpToolDefinition tool) {
        return new McpSchema.Tool(tool.getName(), null, tool.getDescription(), tool.getInputSchema(),
                null, null, null, null);
    }

    /**
     * 完整调用管线（上下文绑定 / 日志 / 转换 / 异常上抛）
     */
    static McpSchema.CallToolResult invoke(McpEndpointDefinition endpoint, McpToolDefinition tool,
                                           McpSyncServerExchange exchange, Map<String, Object> arguments) {
        Map<String, Object> headers = extractHeaders(exchange);
        McpCallContext.associate(headers, arguments);
        try {
            long startTime = System.currentTimeMillis();
            Object[] bound = bindArguments(tool, arguments);
            logRequest(endpoint, tool, bound);

            Object result;
            try {
                Method method = tool.getMethod();
                result = method.invoke(endpoint.getBean(), bound);
            } catch (Exception e) {
                long costTime = System.currentTimeMillis() - startTime;
                Throwable cause = e instanceof java.lang.reflect.InvocationTargetException && e.getCause() != null
                        ? e.getCause() : e;
                log.error("<=======[{}] 方法: {}, \n异常: {}", costTime, javaMethodName(tool), cause.getMessage());
                if (cause instanceof RuntimeException runtimeException) {
                    throw runtimeException;
                }
                throw new IllegalStateException("MCP 工具调用失败：" + tool.getName(), cause);
            }

            long costTime = System.currentTimeMillis() - startTime;
            log.info("<=======[{}] 方法: {}, \n数据: {}", costTime, javaMethodName(tool),
                    JsonUtil.toJson(result, false, false, true));

            return toCallToolResult(tool, result);
        } finally {
            McpCallContext.clear();
        }
    }

    /**
     * 从 exchange 的 transport context 取完整请求头映射（transport 层经
     * {@code McpSseTransportProvider.HEADERS_CONTEXT_KEY} 存入）
     */
    @SuppressWarnings("unchecked")
    private static Map<String, Object> extractHeaders(McpSyncServerExchange exchange) {
        try {
            Object headers = exchange.transportContext()
                    .get(com.bidr.mcp.transport.McpSseTransportProvider.HEADERS_CONTEXT_KEY);
            if (headers instanceof Map) {
                return (Map<String, Object>) headers;
            }
        } catch (Exception e) {
            log.warn("读取 MCP 调用上下文头失败，按空头继续：{}", e.getMessage());
        }
        return java.util.Collections.emptyMap();
    }

    /**
     * 参数绑定：按归一参数名从 arguments 取值并做类型转换；必填缺失抛
     * IllegalArgumentException（消息口径与旧实现一致：Required parameter @name）
     */
    static Object[] bindArguments(McpToolDefinition tool, Map<String, Object> arguments) {
        Map<String, Object> args = arguments == null ? Map.of() : arguments;
        Object[] bound = new Object[tool.getParams().size()];
        for (McpParamDefinition param : tool.getParams()) {
            Object value = args.get(param.getName());
            if (value == null || (value instanceof String stringValue && stringValue.isEmpty())) {
                if (param.isRequired()) {
                    throw new IllegalArgumentException("Required parameter @" + param.getName());
                }
                bound[param.getParameterIndex()] = null;
                continue;
            }
            bound[param.getParameterIndex()] = convertValue(value, param.getJavaType());
        }
        return bound;
    }

    /**
     * wire 值 → 形参类型的宽容转换（wire 上数字可能是 Integer/Double、布尔可能是 Boolean）
     */
    private static Object convertValue(Object value, Class<?> targetType) {
        if (targetType.isInstance(value)) {
            return value;
        }
        if (targetType == String.class) {
            return String.valueOf(value);
        }
        if (targetType == Integer.class || targetType == int.class) {
            return Integer.valueOf(String.valueOf(value));
        }
        if (targetType == Long.class || targetType == long.class) {
            return Long.valueOf(String.valueOf(value));
        }
        if (targetType == Double.class || targetType == double.class) {
            return Double.valueOf(String.valueOf(value));
        }
        if (targetType == Float.class || targetType == float.class) {
            return Float.valueOf(String.valueOf(value));
        }
        if (targetType == Boolean.class || targetType == boolean.class) {
            return Boolean.valueOf(String.valueOf(value));
        }
        if (Collection.class.isAssignableFrom(targetType) && value instanceof Collection) {
            return value;
        }
        if (Map.class.isAssignableFrom(targetType) && value instanceof Map) {
            return value;
        }
        throw new IllegalArgumentException("Parameter type mismatch: @" + targetType.getSimpleName());
    }

    private static McpSchema.CallToolResult toCallToolResult(McpToolDefinition tool, Object result) {
        McpResultConverter converter = tool.resultConverter();
        String text = converter.convert(result);
        return new McpSchema.CallToolResult(
                List.of(new McpSchema.TextContent(null, text, null)), null, null, null);
    }

    /**
     * 请求日志（格式与旧切面逐字一致）
     */
    private static void logRequest(McpEndpointDefinition endpoint, McpToolDefinition tool, Object[] bound) {
        try {
            Map<String, Object> paramsMap = new LinkedHashMap<>();
            List<McpParamDefinition> params = tool.getParams();
            for (int i = 0; i < params.size(); i++) {
                McpParamDefinition param = params.get(i);
                // 旧切面口径：参数名优先取 @Param description，无描述取参数名
                paramsMap.put(!param.getDescription().isEmpty() ? param.getDescription() : param.getName(),
                        bound[param.getParameterIndex()]);
            }
            StringBuffer requestMsg = new StringBuffer();
            requestMsg.append(endpoint.getSseEndpoint()).append(" ").append(javaMethodName(tool)).append("\n");
            requestMsg.append(JsonUtil.toJson(paramsMap, false, false, true));
            log.info("=======>[MCP]{}", requestMsg);
        } catch (Exception e) {
            log.error("记录MCP请求信息时发生错误", e);
        }
    }

    private static String javaMethodName(McpToolDefinition tool) {
        return tool.getMethod().getName();
    }
}
