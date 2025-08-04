package com.bidr.es.dao.repository;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._types.ElasticsearchException;
import co.elastic.clients.elasticsearch._types.mapping.Property;
import co.elastic.clients.elasticsearch.core.BulkRequest;
import co.elastic.clients.elasticsearch.core.ScrollResponse;
import co.elastic.clients.elasticsearch.core.SearchResponse;
import co.elastic.clients.elasticsearch.core.search.Hit;
import co.elastic.clients.elasticsearch.indices.GetAliasResponse;
import co.elastic.clients.elasticsearch.indices.update_aliases.Action;
import co.elastic.clients.json.JsonData;
import com.bidr.es.config.ElasticsearchMappingConfig;
import com.bidr.es.utils.MappingComparator;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * Title: ElasticsearchSelectRepoInf
 * Description: Copyright: Copyright (c) 2024 Company: Bidr Ltd.
 *
 * @author Sharp
 * @since 2025/8/3 13:47
 */

public interface ElasticsearchInitRepoInf<T> extends ElasticsearchBaseRepoInf<T> {
    /**
     * 初始化索引
     *
     * @throws IOException 异常
     */
    default void init() throws IOException {
        ElasticsearchClient client = getClient();
        Class<T> clazz = getEntityClass();
        String aliasName = getIndexName();
        String currentIndexName = aliasName + "_" + System.currentTimeMillis();

        boolean aliasExists = client.indices().exists(b -> b.index(aliasName)).value();

        Map<String, Property> currentMapping = ElasticsearchMappingConfig.buildMapping(clazz);

        if (!aliasExists) {
            createIndex(client, currentIndexName, clazz, currentMapping);
            bindAlias(client, aliasName, currentIndexName);
            return;
        }

        // 解析 alias 当前指向的 index
        GetAliasResponse aliasResp = client.indices().getAlias(a -> a.name(aliasName));
        String oldIndex = aliasResp.result().keySet().iterator().next();

        // 获取旧 Mapping
        Map<String, Property> oldMapping = client.indices().getMapping(g -> g.index(oldIndex)).result().get(oldIndex).mappings().properties();

        if (!MappingComparator.compare(oldMapping, currentMapping)) {
            createIndex(client, currentIndexName, clazz, currentMapping);
            migrateData(client, oldIndex, currentIndexName);
            bindAlias(client, aliasName, currentIndexName);
            client.indices().delete(b -> b.index(oldIndex));
            LoggerFactory.getLogger(this.getClass()).info("索引 {} 重建完成，旧索引 {} 已删除", aliasName, oldIndex);
        }
    }

    /**
     * 添加索引
     *
     * @param client    连接
     * @param indexName 索引名
     * @param clazz     索引类
     * @param mapping   索引配置
     * @throws IOException 异常
     */
    default void createIndex(ElasticsearchClient client, String indexName, Class<T> clazz, Map<String, Property> mapping) throws IOException {
        client.indices().create(c -> c.index(indexName).settings(ElasticsearchMappingConfig.buildIndexSettings(clazz)).mappings(m -> m.properties(mapping)));
        LoggerFactory.getLogger(this.getClass()).info("创建索引：{}", indexName);
    }

    /**
     * 绑定别名
     *
     * @param client      连接
     * @param alias       别名
     * @param targetIndex 原索引名
     * @throws IOException 异常
     */
    default void bindAlias(ElasticsearchClient client, String alias, String targetIndex) throws IOException {
        GetAliasResponse aliasResp;
        try {
            aliasResp = client.indices().getAlias(b -> b.name(alias));
        } catch (ElasticsearchException e) {
            aliasResp = null;
        }

        if (aliasResp != null) {
            for (String oldIndex : aliasResp.result().keySet()) {
                client.indices().updateAliases(a -> a.actions(Arrays.asList(Action.of(r -> r.remove(rm -> rm.index(oldIndex).alias(alias))),
                        Action.of(a1 -> a1.add(ad -> ad.index(targetIndex).alias(alias))))));
                return;
            }
        }

        client.indices().updateAliases(a -> a.actions(Collections.singletonList(Action.of(ad -> ad.add(a1 -> a1.index(targetIndex).alias(alias))))));
    }

    /**
     * 归并数据
     *
     * @param client   连接
     * @param oldIndex 老索引
     * @param newIndex 新索引
     * @throws IOException 异常
     */
    default void migrateData(ElasticsearchClient client, String oldIndex, String newIndex) throws IOException {
        int batchSize = 500;
        long total = 0;
        String scrollId;

        // 第一次查询返回 ScrollResponse<JsonData>
        SearchResponse<JsonData> response = client.search(s -> s.index(oldIndex).scroll(t -> t.time("1m")).size(batchSize), JsonData.class);
        List<Hit<JsonData>> hits = response.hits().hits();

        while (true) {
            if (hits.isEmpty()) {
                break;
            }

            BulkRequest.Builder bulkBuilder = new BulkRequest.Builder();
            for (Hit<JsonData> hit : hits) {
                bulkBuilder.operations(op -> op.index(idx -> idx.index(newIndex).id(hit.id()).document(hit.source())));
            }

            client.bulk(bulkBuilder.build());

            total += hits.size();
            scrollId = response.scrollId();

            if (scrollId == null || hits.isEmpty()) {
                break;
            }

            // 下一页滚动请求返回 ScrollResponse<JsonData>
            String finalScrollId = scrollId;
            ScrollResponse<JsonData> res = client.scroll(s -> s.scrollId(finalScrollId).scroll(t -> t.time("1m")), JsonData.class);
            hits = res.hits().hits();
        }

        LoggerFactory.getLogger(this.getClass()).info("数据迁移完成：{} → {}，共迁移 {} 条", oldIndex, newIndex, total);
    }
}