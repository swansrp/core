package com.bidr.es.service;

import com.bidr.es.anno.EsIndex;
import com.bidr.es.dao.repository.BaseElasticsearchRepo;
import com.bidr.es.dao.repository.ElasticsearchInitRepoInf;
import org.springframework.boot.CommandLineRunner;

import java.io.IOException;

/**
 * Title: BaseElasticsearchRepo
 * Description: Copyright: Copyright (c) 2025 Company: Bidr Ltd.
 *
 * @author Sharp
 * @since 2025/8/1 9:41
 */
public abstract class BaseElasticsearchService<T> extends BaseElasticsearchRepo<T> implements CommandLineRunner, ElasticsearchInitRepoInf<T> {

    @Override
    public void run(String... args) throws IOException {
        EsIndex esIndex = getEntityClass().getAnnotation(EsIndex.class);
        if (esIndex != null) {
            init();
        }
    }
}