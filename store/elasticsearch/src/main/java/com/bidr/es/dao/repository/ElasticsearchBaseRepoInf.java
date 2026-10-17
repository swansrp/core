package com.bidr.es.dao.repository;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch.core.BulkResponse;
import co.elastic.clients.elasticsearch.core.bulk.BulkResponseItem;
import co.elastic.clients.json.JsonData;
import co.elastic.clients.json.JsonpMapper;
import co.elastic.clients.json.JsonpSerializable;
import co.elastic.clients.json.jackson.JacksonJsonpMapper;
import com.bidr.es.anno.EsField;
import com.bidr.es.anno.EsId;
import com.bidr.es.config.ElasticsearchMappingConfig;
import com.bidr.es.utils.HanLPUtil;
import com.bidr.kernel.utils.ReflectionUtil;
import com.bidr.kernel.validate.Validator;
import com.fasterxml.jackson.core.JsonProcessingException;
import jakarta.json.spi.JsonProvider;
import jakarta.json.stream.JsonGenerator;
import jakarta.json.stream.JsonParser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.StringReader;
import java.io.StringWriter;
import java.lang.reflect.Field;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Title: ElasticsearchSelectRepoInf
 * Description: Copyright: Copyright (c) 2024 Company: Bidr Ltd.
 *
 * @author Sharp
 * @since 2025/8/3 13:47
 */

public interface ElasticsearchBaseRepoInf<T> {


    /**
     * 获取非空的doc map
     *
     * @param doc document
     * @return map
     */
    default Map<String, JsonData> extractNonNullFields(T doc) {
        Map<String, JsonData> map = new HashMap<>();
        for (Field field : ReflectionUtil.getFields(doc)) {
            try {
                field.setAccessible(true);
                Object value = field.get(doc);
                if (value != null && !field.isAnnotationPresent(EsId.class)) {
                    map.put(field.getName(), JsonData.of(value));
                }
            } catch (IllegalAccessException ignored) {
            }
        }
        return map;
    }

    ElasticsearchClient getClient();

    /**
     * 获取索引名称
     *
     * @return 索引名称
     */
    default String getIndexName() {
        return ElasticsearchMappingConfig.getIndex(getEntityClass());
    }

    @SuppressWarnings("unchecked")
    default Class<T> getEntityClass() {
        return (Class<T>) ReflectionUtil.getSuperClassGenericType(getClass(), 0);
    }

    /**
     * 获取数据列表 id列表
     *
     * @param docs 数据列表
     * @return id列表
     */
    default List<String> getIds(Collection<T> docs) {
        return docs.stream().map(this::extractId).collect(Collectors.toList());
    }

    /**
     * 获取主键
     *
     * @return 主键
     */
    default String extractId(T doc) {
        try {
            Field idField = ReflectionUtil.getFields(doc).stream().filter(f -> f.isAnnotationPresent(EsId.class))
                    .findFirst().orElse(null);
            if (idField == null) {
                return null;
            }
            idField.setAccessible(true);
            Object id = idField.get(doc);
            return id == null ? null : id.toString();
        } catch (Exception e) {
            Validator.assertException(e);
            return null;
        }
    }

    /**
     * 处理bulk 返回
     *
     * @param response 返回
     * @return
     */
    default boolean handleBulkResponse(BulkResponse response) {
        if (response.errors()) {
            getLogger().warn("批量操作中存在错误！");
            for (BulkResponseItem item : response.items()) {
                if (item.error() != null) {
                    getLogger().warn("  - 文档ID: {}, 错误原因: {}", item.id(), item.error().reason());
                }
            }
            return true;
        } else {
            getLogger().trace("所有批量更新操作都成功执行。");
            return false;
        }
    }

    /**
     * 获取logger
     *
     * @return logger
     */
    default Logger getLogger() {
        return LoggerFactory.getLogger(this.getClass());
    }

    /**
     * 转换成 jsonParser
     *
     * @param obj 实体内容
     * @return jsonParser
     */
    @SuppressWarnings("unchecked")
    default JsonParser getJsonParser(Object obj) {
        StringReader stringReader = null;

        try {
            Object value = obj.getClass().isAssignableFrom(getEntityClass()) ? buildMap((T) obj) : obj;
            stringReader = new StringReader(getJsonpMapper().objectMapper().writeValueAsString(value));
        } catch (JsonProcessingException e) {
            getLogger().error("JSON序列化失败", e);
        }
        return getJsonpMapper().jsonProvider().createParser(stringReader);
    }

    /**
     * 获取非空的doc map
     *
     * @param doc document
     * @return map
     */
    default Map<String, Object> buildMap(T doc) {
        Map<String, Object> map = new HashMap<>();
        for (Field field : ReflectionUtil.getFields(doc)) {
            EsField anno = field.getAnnotation(EsField.class);
            try {
                field.setAccessible(true);
                Object value = field.get(doc);
                if (value != null) {
                    map.put(field.getName(), value);
                    if (anno != null) {
                        if (anno.useHanLP() && value instanceof String) {
                            map.put(field.getName() + "_" + anno.hanlpFieldSuffix(),
                                    HanLPUtil.tokenize(String.valueOf(value)));
                        }
                    }
                }
            } catch (IllegalAccessException ignored) {

            }
        }
        return map;
    }

    default JacksonJsonpMapper getJsonpMapper() {
        return new JacksonJsonpMapper();
    }

    /**
     * 打印 request
     *
     * @param request 请求
     * @return request解析结果
     */
    default void logRequest(JsonpSerializable request) {
        getLogger().trace("es => {}", parseRequest(request));
    }

    /**
     * 解析request
     *
     * @param request 请求
     * @return request解析结果
     */
    default String parseRequest(JsonpSerializable request) {
        StringWriter sw = new StringWriter();
        JsonpMapper mapper = new JacksonJsonpMapper();
        JsonGenerator generator = JsonProvider.provider().createGenerator(sw);
        request.serialize(generator, mapper);
        generator.close();
        return sw.toString();
    }
}