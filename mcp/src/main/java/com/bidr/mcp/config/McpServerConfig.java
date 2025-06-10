package com.bidr.mcp.config;

import org.noear.solon.Solon;
import org.noear.solon.ai.chat.tool.MethodToolProvider;
import org.noear.solon.ai.mcp.server.McpServerEndpointProvider;
import org.noear.solon.ai.mcp.server.annotation.McpServerEndpoint;
import org.noear.solon.ai.mcp.server.prompt.MethodPromptProvider;
import org.noear.solon.ai.mcp.server.resource.MethodResourceProvider;
import org.noear.solon.web.servlet.SolonServletFilter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.AnnotationUtils;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import java.util.List;

/**
 * Title: McpServerConfig
 * Description: Copyright: Copyright (c) 2025 Company: Bidr Ltd.
 *
 * @author Sharp
 * @since 2025/6/5 10:55
 */

@Configuration
public class McpServerConfig {
    @Value("${my.mcp.path:/mcp}")
    private String mcpPath;
    @Value("${server.servlet.context-path:}")
    private String contextPath;

    @PostConstruct
    public void start() {
        System.setProperty("server.contextPath", contextPath);
        Solon.start(McpServerConfig.class, new String[]{"--cfg=mcp-server.properties"});
    }

    @PreDestroy
    public void stop() {
        if (Solon.app() != null) {
            Solon.stopBlock(false, Solon.cfg().stopDelay());
        }
    }

    @Bean
    public McpServerConfig init(List<IMcpServerEndpoint> serverEndpoints) {
        //提取实现容器里 IMcpServerEndpoint 接口的 bean ，并注册为服务端点
        for (IMcpServerEndpoint serverEndpoint : serverEndpoints) {
            McpServerEndpoint anno = AnnotationUtils.findAnnotation(serverEndpoint.getClass(), McpServerEndpoint.class);

            if (anno == null) {
                continue;
            }

            McpServerEndpointProvider serverEndpointProvider = McpServerEndpointProvider.builder()
                    .from(serverEndpoint.getClass(), anno).build();

            serverEndpointProvider.addTool(new MethodToolProvider(serverEndpoint));
            serverEndpointProvider.addResource(new MethodResourceProvider(serverEndpoint));
            serverEndpointProvider.addPrompt(new MethodPromptProvider(serverEndpoint));

            serverEndpointProvider.postStart();

            //可以再把 serverEndpointProvider 手动转入 SpringBoot 容器
        }

        //为了能让这个 init 能正常运行
        return this;
    }

    @Bean
    public FilterRegistrationBean<SolonServletFilter> mcpServerFilter() {
        FilterRegistrationBean<SolonServletFilter> filter = new FilterRegistrationBean<>();
        filter.setName("SolonFilter");
        String path = mcpPath + "/*";
        filter.addUrlPatterns(path);
        filter.setFilter(new SolonServletFilter());
        return filter;
    }
}
