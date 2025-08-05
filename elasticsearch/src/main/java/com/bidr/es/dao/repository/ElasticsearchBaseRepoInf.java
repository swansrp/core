package com.bidr.es.dao.repository;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._types.query_dsl.Query;
import co.elastic.clients.elasticsearch.core.BulkResponse;
import co.elastic.clients.elasticsearch.core.SearchResponse;
import co.elastic.clients.elasticsearch.core.bulk.BulkResponseItem;
import co.elastic.clients.elasticsearch.core.search.Hit;
import co.elastic.clients.json.JsonData;
import com.bidr.es.anno.EsId;
import com.bidr.es.anno.EsIndex;
import com.bidr.kernel.utils.ReflectionUtil;
import com.bidr.kernel.validate.Validator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
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
     * 获取logger
     *
     * @return logger
     */
    default Logger getLogger() {
        return LoggerFactory.getLogger(this.getClass());
    }

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

    default List<String> getMatchingIds(Query query) throws IOException {
        SearchResponse<T> response = getClient().search(
                s -> s.index(getIndexName()).query(query).source(sf -> sf.filter(f -> f.includes("_id"))).size(1000),
                (Class<T>) Object.class);
        return response.hits().hits().stream().map(Hit::id).collect(Collectors.toList());
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
            getLogger().info("所有批量更新操作都成功执行。");
            return false;
        }
    }
}