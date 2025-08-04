package com.bidr.es.dao.repository;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch.core.SearchResponse;
import com.bidr.es.anno.EsId;
import com.bidr.es.anno.EsIndex;
import com.bidr.kernel.utils.ReflectionUtil;
import com.bidr.kernel.validate.Validator;

import java.io.IOException;
import java.lang.reflect.Field;
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

    @SuppressWarnings("unchecked")
    default Class<T> getEntityClass() {
        return (Class<T>) ReflectionUtil.getSuperClassGenericType(getClass(), 0);
    }

    /**
     * 获取主键
     *
     * @return 主键
     */
    default String extractId(T doc) {
        try {
            Field idField = ReflectionUtil.getFields(doc).stream().filter(f -> f.isAnnotationPresent(EsId.class)).findFirst().orElse(null);
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

    ElasticsearchClient getClient();

    /**
     * 获取索引名称
     *
     * @return 索引名称
     */
    default String getIndexName() {
        EsIndex esIndex = getEntityClass().getAnnotation(EsIndex.class);
        return esIndex.name();
    }

    /**
     * 获取非空的doc map
     *
     * @param doc document
     * @return map
     */
    default Map<String, Object> extractNonNullFields(T doc) {
        Map<String, Object> map = new HashMap<>();
        for (Field field : ReflectionUtil.getFields(doc)) {
            try {
                field.setAccessible(true);
                Object value = field.get(doc);
                if (value != null && !field.isAnnotationPresent(EsId.class)) {
                    map.put(field.getName(), value);
                }
            } catch (IllegalAccessException ignored) {
            }
        }
        return map;
    }

    default List<String> getMatchingIds(EsQueryWrapper<T> wrapper) throws IOException {
        SearchResponse<T> response =
                getClient().search(s -> s.index(indexName).query(wrapper.toQuery()).source(sf -> sf.filter(f -> f.includes("_id"))).size(1000),
                        (Class<T>) Object.class);
        return response.hits().hits().stream().map(Hit::id).collect(Collectors.toList());
    }
}