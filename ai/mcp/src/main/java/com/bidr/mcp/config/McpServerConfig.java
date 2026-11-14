package com.bidr.mcp.config;

import com.bidr.kernel.utils.FuncUtil;
import com.bidr.mcp.constant.dict.McpTypeDict;
import com.bidr.mcp.dao.entity.SysMcp;
import com.bidr.mcp.dao.repository.SysMcpService;
import com.bidr.mcp.json.JacksonMcpJsonMapper;
import com.bidr.mcp.json.PermissiveJsonSchemaValidator;
import com.bidr.mcp.registry.McpEndpointDefinition;
import com.bidr.mcp.registry.McpEndpointScanner;
import com.bidr.mcp.registry.McpToolDefinition;
import com.bidr.mcp.registry.McpToolInvoker;
import com.bidr.mcp.transport.McpDispatcherServlet;
import com.bidr.mcp.transport.McpSseTransportProvider;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.modelcontextprotocol.server.McpServer;
import io.modelcontextprotocol.server.McpSyncServer;
import io.modelcontextprotocol.spec.McpSchema;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.servlet.ServletRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import javax.annotation.PreDestroy;
import javax.annotation.Resource;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Title: McpServerConfig
 * Description: MCP Server 装配：收集 {@code @McpServerEndpoint} bean → 类型化注册表 →
 * 每端点一套「javax SSE transport ＋ 官方 SDK McpServer」→ 统一挂
 * {@link McpDispatcherServlet}（${my.mcp.path:/mcp}/*）→ sys_mcp 台账同步。
 *
 * <p>替代旧 Solon「第二上下文」方案：不再 {@code Solon.start()} 起副容器、不再
 * {@code System.setProperty("server.contextPath")} 全局副作用、不再反射读框架私有
 * 字段；HTTP 层回到宿主自己的 Servlet 容器（SB 2.7.3 / javax）。wire 形态与调用端
 * 契约不变，正源见 docs/mcp-endpoint-替换方案.md。</p>
 *
 * <p>与旧实现的能力面差异：Solon 路径还支持 resource/prompt 挂载，两仓从无业务
 * 使用，本版只装配 tool。</p>
 *
 * @author Sharp
 * @since 2025/6/5 10:55
 */
@Slf4j
@Configuration
public class McpServerConfig {

    private final Map<String, McpEndpointDefinition> definitions = new ConcurrentHashMap<>();

    private final Map<String, McpSyncServer> servers = new ConcurrentHashMap<>();

    private final List<McpSseTransportProvider> providers = new ArrayList<>();

    /**
     * 共享 JSON 适配器：transport 与协议层同源（独立 ObjectMapper，不受宿主序列化配置影响）
     */
    private final JacksonMcpJsonMapper jsonMapper = new JacksonMcpJsonMapper(new ObjectMapper());

    private final PermissiveJsonSchemaValidator schemaValidator = new PermissiveJsonSchemaValidator();

    @Value("${my.mcp.path:/mcp}")
    private String mcpPath;

    /**
     * SSE 心跳间隔秒数（注释行 ping，防网关闲置断流）；<=0 关闭
     */
    @Value("${my.mcp.keepalive-seconds:15}")
    private long keepAliveSeconds;

    @Resource
    private SysMcpService sysMcpService;

    /**
     * 装配全部端点并注册总入口 Servlet（路径 ${my.mcp.path:/mcp}/*，与旧 SolonFilter 同前缀）
     */
    @Bean
    public ServletRegistrationBean<McpDispatcherServlet> mcpDispatcherServlet(List<IMcpServerEndpoint> serverEndpoints) {
        McpDispatcherServlet servlet = new McpDispatcherServlet();
        for (IMcpServerEndpoint serverEndpoint : serverEndpoints) {
            registerEndpoint(servlet, serverEndpoint);
        }
        ServletRegistrationBean<McpDispatcherServlet> registration =
                new ServletRegistrationBean<>(servlet, mcpPath + "/*");
        registration.setName("mcpDispatcher");
        log.info("MCP server assembled: {} endpoint(s) on {}/*", providers.size(), mcpPath);
        return registration;
    }

    private void registerEndpoint(McpDispatcherServlet servlet, IMcpServerEndpoint serverEndpoint) {
        McpEndpointDefinition definition = McpEndpointScanner.scan(serverEndpoint);
        if (definition == null) {
            return;
        }
        if (!definition.getSseEndpoint().startsWith(mcpPath + "/")) {
            throw new IllegalStateException("MCP 端点路径须落在 " + mcpPath + "/ 前缀下："
                    + definition.getSseEndpoint() + "（" + definition.getEndpointClassName() + "）");
        }

        // 台账同步先行：已有描述以库为准（与旧实现同口径），再按生效描述建协议层
        syncToDb(definition);

        McpSseTransportProvider provider = new McpSseTransportProvider(jsonMapper, definition.getSseEndpoint(),
                definition.getSseEndpoint() + "/message", keepAliveSeconds);

        List<io.modelcontextprotocol.server.McpServerFeatures.SyncToolSpecification> specs = new ArrayList<>();
        for (McpToolDefinition tool : definition.getTools()) {
            specs.add(McpToolInvoker.toSpecification(definition, tool));
        }

        McpSyncServer server = McpServer.sync(provider)
                .serverInfo(new McpSchema.Implementation(definition.getName(), null, "1.0.0", null, null, null))
                .capabilities(new McpSchema.ServerCapabilities(null, null, null, null, null,
                        new McpSchema.ServerCapabilities.ToolCapabilities(false), null))
                .jsonMapper(jsonMapper)
                .jsonSchemaValidator(schemaValidator)
                .validateToolInputs(false)
                .tools(specs)
                .build();

        servlet.register(provider);
        definitions.put(definition.getEndpointClassName(), definition);
        servers.put(definition.getEndpointClassName(), server);
        providers.add(provider);
    }

    /**
     * 台账同步（类型化元数据直读，不再反射框架私有字段）：行存在→库描述回写内存；
     * 不存在→按注解描述插行。口径与旧实现一致。
     */
    private void syncToDb(McpEndpointDefinition definition) {
        for (McpToolDefinition tool : definition.getTools()) {
            SysMcp sysMcp = sysMcpService.get(definition.getEndpointClassName(), tool.getName(),
                    McpTypeDict.TOOL.getValue());
            if (FuncUtil.isNotEmpty(sysMcp)) {
                tool.setDescription(sysMcp.getDescription());
            } else {
                sysMcp = buildSysMcp(definition, tool);
                sysMcpService.insert(sysMcp);
            }
        }
    }

    private SysMcp buildSysMcp(McpEndpointDefinition definition, McpToolDefinition tool) {
        SysMcp sysMcp = new SysMcp();
        sysMcp.setEndPoint(definition.getEndpointClassName());
        sysMcp.setEndPointName(definition.getName());
        sysMcp.setName(tool.getName());
        sysMcp.setType(McpTypeDict.TOOL.getValue());
        sysMcp.setDescription(tool.getDescription());
        return sysMcp;
    }

    @PreDestroy
    public void stop() {
        for (McpSseTransportProvider provider : providers) {
            provider.closeGracefully().block();
        }
        providers.clear();
        definitions.clear();
        servers.clear();
    }

    /**
     * 描述热更新：内存元数据改写 + 协议层重挂工具 + 台账落库（对齐旧实现的
     * remove/add 语义；不再需要反射改框架字段）
     */
    public void updateDescription(SysMcp sysMcp) {
        McpEndpointDefinition definition = definitions.get(sysMcp.getEndPoint());
        if (definition == null) {
            throw new IllegalStateException("MCP 端点未注册：" + sysMcp.getEndPoint());
        }
        McpToolDefinition tool = definition.getTools().stream()
                .filter(item -> item.getName().equals(sysMcp.getName()))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("MCP 工具未注册："
                        + sysMcp.getEndPoint() + "#" + sysMcp.getName()));

        tool.setDescription(sysMcp.getDescription());
        McpSyncServer server = servers.get(sysMcp.getEndPoint());
        server.removeTool(tool.getName());
        server.addTool(McpToolInvoker.toSpecification(definition, tool));

        sysMcp.setType(McpTypeDict.TOOL.getValue());
        sysMcpService.updateById(sysMcp);
    }
}
