package com.bidr.mcp.context;

import java.util.Collections;
import java.util.Map;

/**
 * Title: McpCallContext
 * Description: 工具调用上下文（ThreadLocal）。调用器在反射调起业务方法前绑定、
 * 调用后清理，业务侧在工具方法内经 {@link #current()} 读取：
 * <ul>
 * <li>{@link #header(String)} —— 本次调用的 HTTP 请求头（SSE 建连头与 message POST 头合并，POST 优先），
 *     替代旧 Solon {@code Context.current().header(...)}；</li>
 * <li>{@link #args()} —— wire 上的完整 arguments（含平台注入键），替代旧
 *     {@code Context.attr(MethodExecuteHandler.MCP_BODY_ATTR)}。</li>
 * </ul>
 * header 查找大小写不敏感（HTTP 头本就大小写不敏感）。
 *
 * @author sharp
 * @since 2026/11/14
 */
public final class McpCallContext {

    private static final ThreadLocal<McpCallContext> CURRENT = new ThreadLocal<>();

    private final Map<String, Object> headers;
    private final Map<String, Object> arguments;

    private McpCallContext(Map<String, Object> headers, Map<String, Object> arguments) {
        this.headers = headers;
        this.arguments = arguments;
    }

    /**
     * 绑定当前线程的调用上下文（仅调用器使用）
     */
    public static McpCallContext associate(Map<String, Object> headers, Map<String, Object> arguments) {
        McpCallContext context = new McpCallContext(headers == null ? Collections.emptyMap() : headers,
                arguments == null ? Collections.emptyMap() : Collections.unmodifiableMap(arguments));
        CURRENT.set(context);
        return context;
    }

    /**
     * 清理当前线程的调用上下文（仅调用器使用，务必 try/finally）
     */
    public static void clear() {
        CURRENT.remove();
    }

    /**
     * 当前调用上下文；不在工具调用线程上返回 null
     */
    public static McpCallContext current() {
        return CURRENT.get();
    }

    /**
     * 读请求头（大小写不敏感），不存在返回 null
     */
    public String header(String name) {
        Object value = headers.get(name);
        return value == null ? null : String.valueOf(value);
    }

    /**
     * 本次调用的完整 arguments（不可变视图；含平台注入的 runtime_meta 等键）
     */
    public Map<String, Object> args() {
        return arguments;
    }
}
