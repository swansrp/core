package com.bidr.mcp.transport;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Title: McpDispatcherServlet
 * Description: MCP 总入口 Servlet，注册在 {@code ${my.mcp.path:/mcp}/*}，替代旧
 * Solon {@code SolonServletFilter}。按「去掉 contextPath 后的精确路径」把请求派给
 * 对应端点的 {@link McpSseTransportProvider}：
 * <ul>
 * <li>GET {@code <sseEndpoint>} → SSE 建连</li>
 * <li>POST {@code <sseEndpoint>/message} → JSON-RPC 收发</li>
 * </ul>
 * 其余路径一律 404（与旧实现的拒绝面一致）。
 *
 * @author sharp
 * @since 2026/11/14
 */
public class McpDispatcherServlet extends HttpServlet {

    private final Map<String, McpSseTransportProvider> bySsePath = new ConcurrentHashMap<>();

    private final Map<String, McpSseTransportProvider> byMessagePath = new ConcurrentHashMap<>();

    /**
     * 注册端点 transport（幂等，重复注册以最后一次为准）
     */
    public void register(McpSseTransportProvider provider) {
        bySsePath.put(provider.getSseEndpoint(), provider);
        byMessagePath.put(provider.getMessageEndpoint(), provider);
    }

    public void unregister(McpSseTransportProvider provider) {
        bySsePath.remove(provider.getSseEndpoint(), provider);
        byMessagePath.remove(provider.getMessageEndpoint(), provider);
    }

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        McpSseTransportProvider provider = bySsePath.get(pathWithinContext(request));
        if (provider == null) {
            response.sendError(HttpServletResponse.SC_NOT_FOUND);
            return;
        }
        provider.handleSseConnect(request, response);
    }

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        McpSseTransportProvider provider = byMessagePath.get(pathWithinContext(request));
        if (provider == null) {
            response.sendError(HttpServletResponse.SC_NOT_FOUND);
            return;
        }
        provider.handleMessage(request, response);
    }

    private static String pathWithinContext(HttpServletRequest request) {
        String uri = request.getRequestURI();
        String contextPath = request.getContextPath();
        if (contextPath != null && !contextPath.isEmpty() && uri.startsWith(contextPath)) {
            return uri.substring(contextPath.length());
        }
        return uri;
    }
}
