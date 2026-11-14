package com.bidr.mcp.json;

import io.modelcontextprotocol.json.schema.JsonSchemaValidator;

import java.util.Map;

/**
 * Title: PermissiveJsonSchemaValidator
 * Description: 宽松版 JSON Schema 校验器：build() 时的 inputSchema 元校验与
 * 运行期结构化输出校验一律放行。官方校验器在 mcp-json-jackson2 模块里（连带
 * jackson 2.21 依赖，不引）；本框架的 inputSchema 由 {@code McpSchemaGenerator}
 * 自产自销、参数绑定层自校验，元校验无增量价值。绑定语义与旧 Solon 实现一致。
 *
 * @author sharp
 * @since 2026/11/14
 */
public class PermissiveJsonSchemaValidator implements JsonSchemaValidator {

    @Override
    public ValidationResponse validateSchema(Map<String, Object> schema) {
        return ValidationResponse.asValid(null);
    }

    @Override
    public ValidationResponse validate(Map<String, Object> schema, Object structuredContent) {
        return ValidationResponse.asValid(null);
    }
}
