package com.bidr.mcp.config;

import com.bidr.kernel.utils.DictEnumUtil;
import com.bidr.kernel.utils.FuncUtil;
import com.bidr.kernel.utils.ReflectionUtil;
import com.bidr.mcp.constant.dict.McpTypeDict;
import com.bidr.mcp.dao.entity.SysMcp;
import com.bidr.mcp.dao.repository.SysMcpService;
import lombok.extern.slf4j.Slf4j;
import org.noear.solon.Solon;
import org.noear.solon.ai.chat.tool.FunctionTool;
import org.noear.solon.ai.chat.tool.MethodToolProvider;
import org.noear.solon.ai.mcp.server.McpServerEndpointProvider;
import org.noear.solon.ai.mcp.server.annotation.McpServerEndpoint;
import org.noear.solon.ai.mcp.server.prompt.FunctionPrompt;
import org.noear.solon.ai.mcp.server.prompt.MethodPromptProvider;
import org.noear.solon.ai.mcp.server.resource.FunctionResource;
import org.noear.solon.ai.mcp.server.resource.MethodResourceProvider;
import org.noear.solon.web.servlet.SolonServletFilter;
import org.springframework.aop.support.AopUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.AnnotationUtils;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import javax.annotation.Resource;
import java.lang.reflect.Field;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import static com.bidr.mcp.constant.dict.McpTypeDict.TOOL;

/**
 * Title: McpServerConfig
 * Description: Copyright: Copyright (c) 2025 Company: Bidr Ltd.
 *
 * @author Sharp
 * @since 2025/6/5 10:55
 */
@Slf4j
@Configuration
public class McpServerConfig {

    private final Map<String, McpServerEndpointProvider> map = new ConcurrentHashMap<>();
    @Value("${my.mcp.path:/mcp}")
    private String mcpPath;
    @Value("${server.servlet.context-path:}")
    private String contextPath;
    @Resource
    private SysMcpService sysMcpService;

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
    public McpServerConfig init(List<IMcpServerEndpoint> serverEndpoints) throws IllegalAccessException {
        //提取实现容器里 IMcpServerEndpoint 接口的 bean ，并注册为服务端点
        for (IMcpServerEndpoint serverEndpoint : serverEndpoints) {
            Class<?> targetClass = AopUtils.getTargetClass(serverEndpoint);
            McpServerEndpoint anno = AnnotationUtils.findAnnotation(targetClass, McpServerEndpoint.class);

            if (anno == null) {
                continue;
            }

            McpServerEndpointProvider serverEndpointProvider = McpServerEndpointProvider.builder().from(targetClass, anno).build();

            addTool(serverEndpoint, targetClass, serverEndpointProvider);
            addResource(serverEndpoint, targetClass, serverEndpointProvider);
            addPrompt(serverEndpoint, targetClass, serverEndpointProvider);

            serverEndpointProvider.postStart();

            map.put(targetClass.getName(), serverEndpointProvider);
        }

        //为了能让这个 init 能正常运行
        return this;
    }

    public void addTool(IMcpServerEndpoint serverEndpoint, Class<?> targetClass, McpServerEndpointProvider serverEndpointProvider) throws IllegalAccessException {
        MethodToolProvider methodToolProvider = new MethodToolProvider(targetClass, serverEndpoint);
        for (FunctionTool tool : methodToolProvider.getTools()) {
            syncToDb(targetClass, tool, TOOL);
        }
        serverEndpointProvider.addTool(methodToolProvider);
    }

    private void addResource(IMcpServerEndpoint serverEndpoint, Class<?> targetClass, McpServerEndpointProvider serverEndpointProvider) throws IllegalAccessException {
        MethodResourceProvider methodResourceProvider = new MethodResourceProvider(targetClass, serverEndpoint);
        for (FunctionResource resource : methodResourceProvider.getResources()) {
            syncToDb(targetClass, resource, McpTypeDict.RESOURCE);
        }
        serverEndpointProvider.addResource(methodResourceProvider);
    }

    private void addPrompt(IMcpServerEndpoint serverEndpoint, Class<?> targetClass, McpServerEndpointProvider serverEndpointProvider) throws IllegalAccessException {
        MethodPromptProvider methodPromptProvider = new MethodPromptProvider(targetClass, serverEndpoint);
        for (FunctionPrompt prompt : methodPromptProvider.getPrompts()) {
            syncToDb(targetClass, prompt, McpTypeDict.PROMPT);
        }
        serverEndpointProvider.addPrompt(methodPromptProvider);
    }

    private void syncToDb(Class<?> targetClass, Object obj, McpTypeDict type) throws IllegalAccessException {
        Field nameField = ReflectionUtil.getField(obj, "name");
        nameField.setAccessible(true);
        String name = (String) nameField.get(obj);
        SysMcp sysMcp = sysMcpService.get(targetClass.getName(), name, type.getValue());
        if (FuncUtil.isNotEmpty(sysMcp)) {
            Field field = ReflectionUtil.getField(obj, "description");
            field.setAccessible(true);
            field.set(obj, sysMcp.getDescription());
        } else {
            sysMcp = new SysMcp();
            sysMcp.setEndPoint(targetClass.getName());
            sysMcp.setName(name);
            sysMcp.setName(type.getValue());
            sysMcpService.insert(sysMcp);
        }
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

    public McpServerEndpointProvider getMcpServerEndpointProvider(Class<?> clazz) {
        return map.get(clazz.getName());
    }

    public void updateDescription(Class<?> clazz, String name, String type, String description) throws IllegalAccessException {
        updateDescription(clazz.getName(), name, DictEnumUtil.getEnumByValue(type, McpTypeDict.class), description);
    }

    public void updateDescription(String endpointClassName, String name, McpTypeDict type, String description) throws IllegalAccessException {
        McpServerEndpointProvider mcpServerEndpointProvider = map.get(endpointClassName);
        switch (type) {
            case TOOL:
                Collection<FunctionTool> tools = mcpServerEndpointProvider.getTools();
                FunctionTool functionTool = tools.stream().filter(tool -> tool.name().equals(name)).findFirst().orElse(null);
                mcpServerEndpointProvider.removeTool(name);
                setField(functionTool, "description", description);
                mcpServerEndpointProvider.addTool(functionTool);
                break;
            case RESOURCE:
                Collection<FunctionResource> resources = mcpServerEndpointProvider.getResources();
                FunctionResource functionResource = resources.stream().filter(resource -> resource.name().equals(name)).findFirst().orElse(null);
                mcpServerEndpointProvider.removeResource(name);
                setField(functionResource, "description", description);
                mcpServerEndpointProvider.addResource(functionResource);
                break;
            case PROMPT:
                Collection<FunctionPrompt> prompts = mcpServerEndpointProvider.getPrompts();
                FunctionPrompt functionPrompt = prompts.stream().filter(prompt -> prompt.name().equals(name)).findFirst().orElse(null);
                mcpServerEndpointProvider.removePrompt(name);
                setField(functionPrompt, "description", description);
                mcpServerEndpointProvider.addPrompt(functionPrompt);
                break;
            default:
                break;
        }
        SysMcp sysMcp = new SysMcp();
        sysMcp.setEndPoint(endpointClassName);
        sysMcp.setName(name);
        sysMcp.setDescription(description);
        sysMcp.setType(type.getValue());
        sysMcpService.updateById(sysMcp);
    }

    private void setField(Object obj, String name, Object value) throws IllegalAccessException {
        Field field = ReflectionUtil.getField(obj, name);
        field.setAccessible(true);
        field.set(obj, value);
    }

    public void updateDescription(SysMcp sysMcp, String description) throws IllegalAccessException {
        updateDescription(sysMcp.getEndPoint(), sysMcp.getName(), DictEnumUtil.getEnumByValue(sysMcp.getType(), McpTypeDict.class), description);
    }
}
