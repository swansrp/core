package com.bidr.mcp.registry;

import com.bidr.mcp.config.McpResultConverter;

import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;

/**
 * Title: McpToolDefinition
 * Description: 工具的类型化元数据——替代旧实现里反射读 Solon FunctionTool 私有字段的
 * 全部信息（name/description），sys_mcp 台账同步与描述热更新都在这层元数据上操作。
 *
 * @author sharp
 * @since 2026/11/14
 */
public class McpToolDefinition {

    private final String name;

    /**
     * 工具描述；描述热更新（sys_mcp 管理面）时在内存里原地改写并重挂工具
     */
    private volatile String description;

    private final Method method;

    private final List<McpParamDefinition> params;

    private final Class<? extends McpResultConverter> resultConverterClass;

    /**
     * inputSchema（type/properties/required），由 {@link McpSchemaGenerator} 一次性生成
     */
    private final Map<String, Object> inputSchema;

    /**
     * 结果转换器实例（懒加载，工具单发串行调用足够）
     */
    private volatile McpResultConverter resultConverter;

    public McpToolDefinition(String name, String description, Method method, List<McpParamDefinition> params,
                             Class<? extends McpResultConverter> resultConverterClass,
                             Map<String, Object> inputSchema) {
        this.name = name;
        this.description = description;
        this.method = method;
        this.params = params;
        this.resultConverterClass = resultConverterClass;
        this.inputSchema = inputSchema;
    }

    public String getName() {
        return name;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public Method getMethod() {
        return method;
    }

    public List<McpParamDefinition> getParams() {
        return params;
    }

    public Class<? extends McpResultConverter> getResultConverterClass() {
        return resultConverterClass;
    }

    public Map<String, Object> getInputSchema() {
        return inputSchema;
    }

    /**
     * 取结果转换器实例（懒加载；转换器须有无参构造）
     */
    public McpResultConverter resultConverter() {
        McpResultConverter converter = this.resultConverter;
        if (converter == null) {
            synchronized (this) {
                converter = this.resultConverter;
                if (converter == null) {
                    try {
                        converter = resultConverterClass.getDeclaredConstructor().newInstance();
                    } catch (ReflectiveOperationException e) {
                        throw new IllegalStateException(
                                "MCP 结果转换器实例化失败：" + resultConverterClass.getName(), e);
                    }
                    this.resultConverter = converter;
                }
            }
        }
        return converter;
    }
}
