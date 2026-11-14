package com.bidr.mcp.registry;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Title: McpSchemaGenerator
 * Description: 工具参数 → MCP inputSchema（JSON Schema 子集）。
 * 类型映射沿用旧 Solon 实测口径：String→string、Map→object（runtime_meta 平台判定
 * 正好吃 type=object）、整型→integer、浮点→number、布尔→boolean、集合/数组→array、
 * 其余→object。schema 只声明本框架自己绑定时认识的形态，与绑定层同源不分裂。
 *
 * @author sharp
 * @since 2026/11/14
 */
public final class McpSchemaGenerator {

    private McpSchemaGenerator() {
    }

    /**
     * 生成 inputSchema：{type:object, properties:{...}, required:[...]}
     */
    public static Map<String, Object> generate(Collection<McpParamDefinition> params) {
        Map<String, Object> properties = new LinkedHashMap<>();
        for (McpParamDefinition param : params) {
            Map<String, Object> property = new LinkedHashMap<>();
            property.put("type", schemaType(param.getJavaType()));
            if (!param.getDescription().isEmpty()) {
                property.put("description", param.getDescription());
            }
            properties.put(param.getName(), property);
        }

        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put("type", "object");
        schema.put("properties", properties);
        if (params.stream().anyMatch(McpParamDefinition::isRequired)) {
            schema.put("required", params.stream().filter(McpParamDefinition::isRequired)
                    .map(McpParamDefinition::getName).toList());
        }
        return Collections.unmodifiableMap(schema);
    }

    /**
     * Java 形参类型 → JSON Schema type
     */
    public static String schemaType(Class<?> javaType) {
        if (javaType == String.class || javaType == CharSequence.class || javaType == Character.class
                || javaType == char.class) {
            return "string";
        }
        if (javaType == Integer.class || javaType == int.class || javaType == Long.class || javaType == long.class
                || javaType == Short.class || javaType == short.class || javaType == Byte.class
                || javaType == byte.class) {
            return "integer";
        }
        if (javaType == Float.class || javaType == float.class || javaType == Double.class || javaType == double.class
                || javaType == Number.class || javaType == java.math.BigDecimal.class) {
            return "number";
        }
        if (javaType == Boolean.class || javaType == boolean.class) {
            return "boolean";
        }
        if (javaType.isArray() || Collection.class.isAssignableFrom(javaType)) {
            return "array";
        }
        // Map 及其余复杂类型（runtime_meta 等按需注入参数走这里）
        return "object";
    }
}
