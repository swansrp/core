package com.bidr.es.dao.repository;

import co.elastic.clients.elasticsearch._types.mapping.Property;
import co.elastic.clients.elasticsearch._types.mapping.TextProperty;
import co.elastic.clients.elasticsearch._types.mapping.TypeMapping;
import co.elastic.clients.json.JsonpMapper;
import co.elastic.clients.transport.ElasticsearchTransport;
import com.bidr.es.anno.EsField;
import com.bidr.es.anno.EsId;
import com.bidr.es.config.EsFieldType;
import com.bidr.kernel.constant.err.ErrCodeSys;
import com.bidr.kernel.validate.Validator;
import jakarta.json.stream.JsonGenerator;

import java.io.StringWriter;
import java.lang.reflect.Field;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Title: MappingBuilder
 * Description: Copyright: Copyright (c) 2025 Company: Bidr Ltd.
 *
 * @author Sharp
 * @since 2025/8/1 16:49
 */

public class MappingBuilder {
    /**
     * 将 Map<String, Property> 转换成 JSON 字符串，用于构建 Elasticsearch 索引 DSL。
     *
     * @param clazz     entity 配置
     * @param transport 客户端 transport（用来获取 JsonpMapper）
     * @return JSON 字符串，结构是 {"mappings": {"properties": {...}}}
     */
    public static String toJson(Class<?> clazz, ElasticsearchTransport transport) {
        Map<String, Property> mapping = buildMapping(clazz);
        TypeMapping typeMapping = new TypeMapping.Builder().properties(mapping).build();

        JsonpMapper jsonpMapper = transport.jsonpMapper();
        StringWriter stringWriter = new StringWriter();

        JsonGenerator generator = jsonpMapper.jsonProvider().createGenerator(stringWriter);
        generator.writeStartObject(); // {
        generator.writeKey("mappings");
        typeMapping.serialize(generator, jsonpMapper);
        generator.writeEnd(); // }

        generator.close();
        return stringWriter.toString();
    }

    public static Map<String, Property> buildMapping(Class<?> clazz) {
        Map<String, Property> properties = new LinkedHashMap<>();
        boolean findIdField = false;
        for (Field field : clazz.getDeclaredFields()) {
            Property property = null;
            if (field.isAnnotationPresent(EsId.class)) {
                property = Property.of(p -> p.keyword(k -> k));
                findIdField = true;
            } else {if (field.isAnnotationPresent(EsField.class)) {
                EsField esField = field.getAnnotation(EsField.class);
                EsFieldType type = esField.type();
                switch (type) {
                    case TEXT:
                        property = Property.of(p -> {
                            TextProperty.Builder textBuilder = new TextProperty.Builder();
                            if (esField.keyword()) {
                                textBuilder.fields("keyword", Property.of(k -> k.keyword(kb -> kb)));
                            }
                            // 1. 主字段 analyzer
                            if (esField.useIk()) {
                                textBuilder.analyzer("ik_smart");
                            } else {
                                // 默认分词器，或者不设置
                                textBuilder.analyzer("standard");
                            }
                            // 2. 配置 multi-fields
                            Map<String, Property> fields = new LinkedHashMap<>();

                            // 2.1 keyword 子字段
                            if (esField.keyword()) {
                                fields.put("keyword", Property.of(k -> k.keyword(kb -> kb)));
                            }

                            // 2.2 pinyin 子字段
                            if (esField.usePinyin()) {
                                fields.put("pinyin", Property.of(pinyin -> pinyin.text(tp -> tp.analyzer("pinyin_analyzer"))));
                            }

                            // 2.3 stconvert 子字段（简繁转换）
                            if (esField.useStConvert()) {
                                fields.put("stconvert", Property.of(st -> st.text(tp -> tp.analyzer("ik_smart_stconvert"))));
                            }

                            if (!fields.isEmpty()) {
                                textBuilder.fields(fields);
                            }

                            return p.text(textBuilder.build());
                        });
                        break;
                    case KEYWORD:
                        property = Property.of(p -> p.keyword(k -> k));
                        break;
                    case DATE:
                        property = Property.of(p -> p.date(d -> d));
                        break;
                    case INTEGER:
                        property = Property.of(p -> p.integer(i -> i));
                        break;
                    case FLOAT:
                        property = Property.of(p -> p.float_(f -> f));
                        break;
                    case BOOLEAN:
                        property = Property.of(p -> p.boolean_(b -> b));
                        break;
                    case COMPLETION:
                        property = Property.of(p -> p.completion(c -> c));
                        break;
                    default:
                        throw new UnsupportedOperationException("不支持的字段类型: " + type);
                }
            }
            if (property != null) {
                properties.put(field.getName(), property);
            }
        }
        Validator.assertTrue(findIdField, ErrCodeSys.PA_DATA_NOT_EXIST, "ID字段");
        return properties;
    }
}
