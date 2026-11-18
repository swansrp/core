package com.bidr.mcp.json;

import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.modelcontextprotocol.json.McpJsonMapper;
import io.modelcontextprotocol.json.TypeRef;

import java.io.IOException;

/**
 * Title: JacksonMcpJsonMapper
 * Description: MCP SDK 的 McpJsonMapper 适配器，落在宿主 Jackson 2.13 上。
 * 官方 mcp-json-jackson2 模块编译目标 jackson 2.21（SB2.7 宿主为 2.13，直引有
 * NoSuchMethodError 风险且附带 networknt 校验器），故按其 88 行实现原样移植——
 * 所用 API（readValue/convertValue/writeValueAsString/TypeFactory.constructType）
 * 均为 Jackson 2.x 远古稳定面。独立 ObjectMapper，不受宿主序列化配置影响。
 *
 * @author sharp
 * @since 2026/11/14
 */
public final class JacksonMcpJsonMapper implements McpJsonMapper {

    private final ObjectMapper objectMapper;

    public JacksonMcpJsonMapper(ObjectMapper objectMapper) {
        if (objectMapper == null) {
            throw new IllegalArgumentException("ObjectMapper must not be null");
        }
        this.objectMapper = objectMapper;
    }

    public ObjectMapper getObjectMapper() {
        return objectMapper;
    }

    @Override
    public <T> T readValue(String content, Class<T> type) throws IOException {
        return objectMapper.readValue(content, type);
    }

    @Override
    public <T> T readValue(byte[] content, Class<T> type) throws IOException {
        return objectMapper.readValue(content, type);
    }

    @Override
    public <T> T readValue(String content, TypeRef<T> type) throws IOException {
        JavaType javaType = objectMapper.getTypeFactory().constructType(type.getType());
        return objectMapper.readValue(content, javaType);
    }

    @Override
    public <T> T readValue(byte[] content, TypeRef<T> type) throws IOException {
        JavaType javaType = objectMapper.getTypeFactory().constructType(type.getType());
        return objectMapper.readValue(content, javaType);
    }

    @Override
    public <T> T convertValue(Object fromValue, Class<T> type) {
        return objectMapper.convertValue(fromValue, type);
    }

    @Override
    public <T> T convertValue(Object fromValue, TypeRef<T> type) {
        JavaType javaType = objectMapper.getTypeFactory().constructType(type.getType());
        return objectMapper.convertValue(fromValue, javaType);
    }

    @Override
    public String writeValueAsString(Object value) throws IOException {
        return objectMapper.writeValueAsString(value);
    }

    @Override
    public byte[] writeValueAsBytes(Object value) throws IOException {
        return objectMapper.writeValueAsBytes(value);
    }
}
