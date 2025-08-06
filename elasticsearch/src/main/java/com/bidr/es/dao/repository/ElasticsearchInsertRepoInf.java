package com.bidr.es.dao.repository;

import co.elastic.clients.elasticsearch.core.*;
import co.elastic.clients.json.JsonData;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * Title: ElasticsearchInsertRepoInf
 * Description: Copyright: Copyright (c) 2024 Company: Bidr Ltd.
 *
 * @author Sharp
 * @since 2025/8/3 13:47
 */

public interface ElasticsearchInsertRepoInf<T> extends ElasticsearchBaseRepoInf<T> {
    /**
     * 插入或更新实体
     *
     * @param doc 实体
     * @return 结果
     */
    default boolean insertOrUpdate(T doc) {
        try {
            String id = extractId(doc);
            IndexRequest.Builder<T> builder = new IndexRequest.Builder<T>().index(getIndexName())
                    .withJson(getJsonParser(doc), getJsonpMapper());
            if (id != null) {
                builder.id(id);
            }
            getClient().index(builder.build());
            return true;
        } catch (IOException e) {
            getLogger().error("添加或更新失败, 不回滚", e);
            return false;
        }
    }

    /**
     * 插入或更新实体列表
     *
     * @param docs 实体列表
     * @return 结果
     */
    default boolean insertOrUpdate(Collection<T> docs) {
        return insertOrUpdate(docs, docs.size());
    }

    /**
     * 插入或更新实体列表
     *
     * @param docs      实体列表
     * @param batchSize 每次数量
     * @return 结果
     */
    default boolean insertOrUpdate(Collection<T> docs, int batchSize) {
        List<T> tempList = new ArrayList<>();
        try {
            BulkRequest.Builder br = new BulkRequest.Builder();
            for (T doc : docs) {
                String id = extractId(doc);
                br.operations(op -> op.index(idx -> {
                    idx.index(getIndexName()).document(JsonData.of(buildMap(doc)));
                    if (id != null) {
                        idx.id(id);
                    }
                    return idx;
                }));
                tempList.add(doc);
                if (tempList.size() == batchSize) {
                    BulkResponse bulk = getClient().bulk(br.build());
                    handleBulkResponse(bulk);
                    tempList.clear();
                    br = new BulkRequest.Builder();
                }
            }
            if (!tempList.isEmpty()) {
                getClient().bulk(br.build());
            }
            return true;
        } catch (IOException e) {
            getLogger().error("批量添加或更新失败, 不回滚", e);
            return false;
        }
    }

    /**
     * 插入实体
     *
     * @param doc 实体
     * @return 结果
     */
    default boolean insert(T doc) {
        try {
            String id = extractId(doc);
            CreateRequest.Builder<JsonData> builder = new CreateRequest.Builder<JsonData>().index(getIndexName())
                    .document(JsonData.of(buildMap(doc)));
            if (id != null) {
                builder.id(id);
            }
            getClient().create(builder.build());
            return true;
        } catch (IOException e) {
            getLogger().error("插入实体失败", e);
            DeleteRequest.Builder builder = new DeleteRequest.Builder().index(getIndexName()).id(extractId(doc));
            try {
                getClient().delete(builder.build());
            } catch (IOException ee) {
                getLogger().error("回滚失败", e);
            }
            return false;
        }
    }

    /**
     * 插入实体列表
     *
     * @param docs 实体列表
     * @return 结果
     */
    default boolean insert(Collection<T> docs) {
        return insert(docs, docs.size());
    }

    /**
     * 插入实体列表
     *
     * @param docs      实体列表
     * @param batchSize 每次数量
     * @return 结果
     */
    default boolean insert(Collection<T> docs, int batchSize) {
        List<T> tempList = new ArrayList<>();
        List<T> saveList = new ArrayList<>();
        try {
            BulkRequest.Builder br = new BulkRequest.Builder();
            for (T doc : docs) {
                String id = extractId(doc);
                br.operations(op -> op.create(idx -> {
                    idx.index(getIndexName()).document(JsonData.of(buildMap(doc)));
                    if (id != null) {
                        idx.id(id);
                    }
                    return idx;
                }));
                tempList.add(doc);
                if (tempList.size() == batchSize) {
                    getClient().bulk(br.build());
                    saveList.addAll(tempList);
                    tempList.clear();
                    br = new BulkRequest.Builder();
                }
            }
            if (!tempList.isEmpty()) {
                getClient().bulk(br.build());
                saveList.addAll(tempList);
            }
            return true;
        } catch (IOException e) {
            getLogger().error("批量添加失败, 开始回滚", e);
            List<String> savedIds = getIds(saveList);
            BulkRequest.Builder br = new BulkRequest.Builder();
            for (String savedId : savedIds) {
                br.operations(op -> op.delete(d -> d.index(getIndexName()).id(savedId)));
            }
            try {
                getClient().bulk(br.build());
            } catch (IOException ee) {
                getLogger().error("回滚失败", e);
            }
            return false;
        }
    }
}