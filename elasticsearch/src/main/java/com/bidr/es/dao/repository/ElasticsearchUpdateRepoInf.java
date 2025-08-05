package com.bidr.es.dao.repository;

import co.elastic.clients.elasticsearch._types.Conflicts;
import co.elastic.clients.elasticsearch._types.query_dsl.Query;
import co.elastic.clients.elasticsearch.core.*;
import co.elastic.clients.json.JsonData;
import co.elastic.clients.json.JsonpMapper;
import co.elastic.clients.json.jackson.JacksonJsonpMapper;
import com.bidr.kernel.utils.JsonUtil;
import jakarta.json.stream.JsonParser;

import java.io.IOException;
import java.io.StringReader;
import java.util.Collection;
import java.util.Collections;
import java.util.Map;

/**
 * Title: ElasticsearchSelectRepoInf
 * Description: Copyright: Copyright (c) 2024 Company: Bidr Ltd.
 *
 * @author Sharp
 * @since 2025/8/3 13:47
 */

public interface ElasticsearchUpdateRepoInf<T> extends ElasticsearchBaseRepoInf<T> {
    /**
     * 更新实体依照id
     *
     * @param doc 实体
     * @return 结果
     */
    default boolean updateById(T doc) {
        return updateById(doc, true);
    }

    /**
     * 更新实体依照id
     *
     * @param doc        实体
     * @param ignoreNull 是否无视ignore
     * @return 结果
     */
    default boolean updateById(T doc, boolean ignoreNull) {
        try {
            GetRequest getRequest = GetRequest.of(g -> g.index(getIndexName()).id(extractId(doc)));
            // 执行 Get 请求，并指定将结果映射到 实体 类
            GetResponse<T> getResponse = getClient().get(getRequest, getEntityClass());
            // 检查文档是否存在
            if (getResponse.found()) {
                if (ignoreNull) {
                    Map<String, JsonData> nonNullFields = extractNonNullFields(doc);
                    if (nonNullFields.isEmpty()) {
                        return false;
                    }
                    getClient().update(u -> u.index(getIndexName()).id(extractId(doc)).doc(nonNullFields), Void.class);
                } else {
                    getClient().update(u -> u.index(getIndexName()).id(extractId(doc)).doc(doc), Void.class);
                }
            } else {
                getLogger().debug("更新失败, id不存在");
                return false;
            }
            return true;
        } catch (IOException e) {
            getLogger().error("更新失败", e);
            return false;
        }

    }

    /**
     * 更新实体列表依照id
     *
     * @param docs 实体列表
     * @return 结果
     */
    default boolean updateById(Collection<T> docs) {
        return updateById(docs, true);
    }

    /**
     * 更新实体列表依照id
     *
     * @param docs       实体列表
     * @param ignoreNull 是否无视ignore
     * @return 结果
     */
    default boolean updateById(Collection<T> docs, boolean ignoreNull) {
        BulkRequest.Builder br = new BulkRequest.Builder();
        try {
            for (T doc : docs) {
                String docId = extractId(doc);
                if (docId == null || docId.isEmpty()) {
                    System.err.println("Skipping document with null or empty ID.");
                    continue;
                }
                // 1. 将 Map 转换为 JSON 字符串
                JsonpMapper mapper = new JacksonJsonpMapper();
                String jsonString;
                if (ignoreNull) {
                    jsonString = JsonUtil.toJson(extractNonNullFields(doc));
                } else {
                    jsonString = JsonUtil.toJson(doc);
                }
                StringReader reader = new StringReader(jsonString);

                // 2. 从 JSON 字符串创建 JsonParser
                JsonParser parser = mapper.jsonProvider().createParser(reader);
                br.operations(op -> op.update(i -> i.index(getIndexName()).id(docId).withJson(parser, mapper)));
            }

            BulkResponse result = getClient().bulk(br.build());
            return handleBulkResponse(result);
        } catch (IOException e) {
            getLogger().error("批量更新失败, 不回滚", e);
            return false;
        }

    }

    /**
     * 根据条件 批量修改
     *
     * @param doc   修改内容
     * @param query 条件
     * @return 返回
     * @throws IOException 异常
     */
    default UpdateByQueryResponse update(T doc, Query query) throws IOException {
        Map<String, JsonData> updateData = extractNonNullFields(doc);
        if (updateData.isEmpty()) {
            return null;
        }
        UpdateByQueryRequest request = UpdateByQueryRequest.of(
                u -> u.index(Collections.singletonList(getIndexName())).query(query).conflicts(Conflicts.Proceed)
                        .script(s -> s.params(updateData)));
        return getClient().updateByQuery(request);
    }

}