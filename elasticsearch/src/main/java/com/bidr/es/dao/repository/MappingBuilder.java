package com.bidr.es.dao.repository;

import co.elastic.clients.elasticsearch._types.mapping.Property;
import co.elastic.clients.elasticsearch._types.mapping.TextProperty;
import com.bidr.es.anno.EsField;
import com.bidr.es.anno.EsId;
import com.bidr.es.config.EsFieldType;
import com.bidr.kernel.constant.err.ErrCodeSys;
import com.bidr.kernel.validate.Validator;

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
    public static Map<String, Property> buildMapping(Class<?> clazz) {
        Map<String, Property> properties = new LinkedHashMap<>();
        boolean findIdField = false;
        for (Field field : clazz.getDeclaredFields()) {
            Property property = null;
            if (field.isAnnotationPresent(EsId.class)) {
                property = Property.of(p -> p.keyword(k -> k));
                findIdField = true;
            } else if (field.isAnnotationPresent(EsField.class)) {
                EsField esField = field.getAnnotation(EsField.class);
                EsFieldType type = esField.type();
                switch (type) {
                    case TEXT:
                        property = Property.of(p -> {
                            TextProperty.Builder textBuilder = new TextProperty.Builder();
                            if (esField.keyword()) {
                                textBuilder.fields("keyword", Property.of(k -> k.keyword(kb -> kb)));
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
