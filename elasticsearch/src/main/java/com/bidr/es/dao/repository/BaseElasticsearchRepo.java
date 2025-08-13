package com.bidr.es.dao.repository;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import lombok.extern.slf4j.Slf4j;

import javax.annotation.Resource;

/**
 * Title: BaseElasticsearchRepo
 * Description: Copyright: Copyright (c) 2025 Company: Bidr Ltd.
 *
 * @author Sharp
 * @since 2025/8/1 9:41
 */
@Slf4j
public abstract class BaseElasticsearchRepo<T> implements ElasticsearchInsertRepoInf<T>, ElasticsearchUpdateRepoInf<T>, ElasticsearchDeleteRepoInf<T>, ElasticsearchCountRepoInf<T>, ElasticsearchSelectRepoInf<T> {
    @Resource
    private ElasticsearchClient elasticsearchClient;

    @Override
    public ElasticsearchClient getClient() {
        return elasticsearchClient;
    }

    public void setClient(ElasticsearchClient elasticsearchClient) {
        this.elasticsearchClient = elasticsearchClient;
    }


}