package com.bidr.es.dao.repository;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch.core.BulkRequest;
import co.elastic.clients.elasticsearch.core.IndexRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;

import javax.annotation.Resource;
import java.io.IOException;
import java.util.Collection;
import java.util.Map;

/**
 * Title: BaseElasticsearchRepo
 * Description: Copyright: Copyright (c) 2025 Company: Bidr Ltd.
 *
 * @author Sharp
 * @since 2025/8/1 9:41
 */
@Slf4j
public abstract class BaseElasticsearchRepo<T> implements CommandLineRunner, ElasticsearchInsertRepoInf<T>, ElasticsearchUpdateRepoInf<T>,
        ElasticsearchDeleteRepoInf<T>, ElasticsearchSelectRepoInf<T>, ElasticsearchInitRepoInf<T> {
    @Resource
    private ElasticsearchClient elasticsearchClient;

    @Override
    public ElasticsearchClient getClient() {
        return elasticsearchClient;
    }

    @Override
    public void run(String... args) throws IOException {
        init();
    }

    @Override
    public boolean insert(T doc) {
        try {
            String id = extractId(doc);
            IndexRequest.Builder<T> builder = new IndexRequest.Builder<T>().index(getIndexName()).document(doc);
            if (id != null) {
                builder.id(id);
            }
            getClient().index(builder.build());
            return true;
        } catch (IOException e) {
            log.error("", e);
            return false;
        }
    }

    @Override
    public boolean insert(Collection<T> docs) {
        try {
            BulkRequest.Builder br = new BulkRequest.Builder();
            for (T doc : docs) {
                String id = extractId(doc);
                br.operations(op -> op.index(idx -> {
                    idx.index(getIndexName()).document(doc);
                    if (id != null) {
                        idx.id(id);
                    }
                    return idx;
                }));
            }
            getClient().bulk(br.build());
            return true;
        } catch (IOException e) {
            log.error("", e);
            return false;
        }
    }

    @Override
    @SuppressWarnings("unchecked")
    public boolean updateById(T doc, boolean ignoreNull) {
        try {
            if (ignoreNull) {
                Map<String, Object> nonNullFields = extractNonNullFields(doc);
                if (nonNullFields.isEmpty()) {
                    return false;
                }
                getClient().update(u -> u.index(getIndexName()).id(extractId(doc)).doc(nonNullFields), Object.class);
            } else {
                getClient().update(u -> u.index(getIndexName()).id(extractId(doc)).doc(doc), (Class<T>) doc.getClass());
            }
            return true;
        } catch (IOException e) {
            log.error("", e);
            return false;
        }
    }
}