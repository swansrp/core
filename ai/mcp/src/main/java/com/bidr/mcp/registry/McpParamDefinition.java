package com.bidr.mcp.registry;

import lombok.AllArgsConstructor;
import lombok.Data;

/**
 * Title: McpParamDefinition
 * Description: 工具参数的类型化元数据（名字解析已归一：Param.name > Param.value > 反射参数名）。
 *
 * @author sharp
 * @since 2026/11/14
 */
@Data
@AllArgsConstructor
public class McpParamDefinition {

    /**
     * wire 上的参数名（snake_case），绑定与 inputSchema 共用同一来源
     */
    private final String name;

    /**
     * 参数描述（进 inputSchema.properties[name].description）
     */
    private final String description;

    /**
     * 是否必填
     */
    private final boolean required;

    /**
     * Java 形参类型（决定 schema type 与绑定转换）
     */
    private final Class<?> javaType;

    /**
     * 形参下标
     */
    private final int parameterIndex;
}
