package com.bidr.es.dao.repository;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import com.bidr.es.anno.EsIndex;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;

import javax.annotation.Resource;
import java.io.IOException;

/**
 * Title: BaseElasticsearchRepo
 * Description: Copyright: Copyright (c) 2025 Company: Bidr Ltd.
 *
 * @author Sharp
 * @since 2025/8/1 9:41
 */
@Slf4j
public abstract class BaseElasticsearchRepo<T> implements CommandLineRunner, ElasticsearchInsertRepoInf<T>, ElasticsearchUpdateRepoInf<T>,
        ElasticsearchDeleteRepoInf<T>, ElasticsearchCountRepoInf<T>, ElasticsearchSelectRepoInf<T>, ElasticsearchInitRepoInf<T> {
    @Resource
    private ElasticsearchClient elasticsearchClient;

    @Override
    public void run(String... args) throws IOException {
        EsIndex esIndex = getEntityClass().getAnnotation(EsIndex.class);
        if (esIndex != null) {
            init();
        }
    }

    @Override
    public ElasticsearchClient getClient() {
        return elasticsearchClient;
    }

    public void setClient(ElasticsearchClient elasticsearchClient) {
        this.elasticsearchClient = elasticsearchClient;
    }


}