package com.bidr.mcp.registry;

import com.bidr.mcp.anno.McpServerEndpoint;
import com.bidr.mcp.anno.Param;
import com.bidr.mcp.anno.ToolMapping;
import com.bidr.mcp.config.IMcpServerEndpoint;
import org.springframework.aop.support.AopUtils;
import org.springframework.core.annotation.AnnotationUtils;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.Parameter;
import java.util.ArrayList;
import java.util.List;

/**
 * Title: McpEndpointScanner
 * Description: 端点扫描器：把 {@code @McpServerEndpoint} bean 的 {@code @ToolMapping}
 * 方法收编成类型化元数据。纯静态、无 Spring/DB 依赖，回环自测直接复用。
 *
 * <p>与旧实现的能力面差异：旧 Solon 路径还支持 resource/prompt 两类挂载，但两仓
 * 从无业务使用，本注册表只收 tool；将来有需要在此扩 {@code @ResourceMapping} 系
 * 扫描即可。</p>
 *
 * @author sharp
 * @since 2026/11/14
 */
public final class McpEndpointScanner {

    private McpEndpointScanner() {
    }

    /**
     * 扫描端点 bean；无 {@code @McpServerEndpoint} 注解返回 null（收集方跳过）
     */
    public static McpEndpointDefinition scan(Object endpointBean) {
        Class<?> targetClass = AopUtils.getTargetClass(endpointBean);
        McpServerEndpoint anno = AnnotationUtils.findAnnotation(targetClass, McpServerEndpoint.class);
        if (anno == null) {
            return null;
        }

        McpEndpointDefinition definition = new McpEndpointDefinition(targetClass.getName(), anno.name(),
                anno.sseEndpoint(), endpointBean);

        for (Method method : targetClass.getDeclaredMethods()) {
            if (method.isSynthetic() || Modifier.isStatic(method.getModifiers())) {
                continue;
            }
            ToolMapping toolMapping = AnnotationUtils.findAnnotation(method, ToolMapping.class);
            if (toolMapping == null) {
                continue;
            }
            definition.getTools().add(buildTool(method, toolMapping));
        }

        if (definition.getTools().isEmpty()) {
            throw new IllegalStateException("MCP 端点未声明任何工具：" + targetClass.getName());
        }
        return definition;
    }

    private static McpToolDefinition buildTool(Method method, ToolMapping toolMapping) {
        if (!Modifier.isPublic(method.getModifiers())) {
            throw new IllegalStateException("MCP 工具方法必须 public：" + method);
        }
        method.setAccessible(true);

        String toolName = toolMapping.name().isEmpty() ? method.getName() : toolMapping.name();

        List<McpParamDefinition> params = new ArrayList<>();
        Parameter[] parameters = method.getParameters();
        for (int i = 0; i < parameters.length; i++) {
            Parameter parameter = parameters[i];
            Param paramAnno = parameter.getAnnotation(Param.class);
            String paramName = parameter.getName();
            String description = "";
            boolean required = true;
            if (paramAnno != null) {
                // 名字归一单一来源：name > value > 反射参数名（别名分裂坑结构性根除）
                paramName = !paramAnno.name().isEmpty() ? paramAnno.name()
                        : !paramAnno.value().isEmpty() ? paramAnno.value() : parameter.getName();
                description = paramAnno.description();
                required = paramAnno.required();
            }
            params.add(new McpParamDefinition(paramName, description, required, parameter.getType(), i));
        }

        return new McpToolDefinition(toolName, toolMapping.description(), method, params,
                toolMapping.resultConverter(), McpSchemaGenerator.generate(params));
    }
}
