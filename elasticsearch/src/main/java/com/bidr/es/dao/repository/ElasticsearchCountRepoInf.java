package com.bidr.es.dao.repository;

import co.elastic.clients.elasticsearch._types.query_dsl.Query;
import co.elastic.clients.elasticsearch.core.CountRequest;

import java.io.IOException;

/**
 * Title: ElasticsearchSelectRepoInf
 * Description: Copyright: Copyright (c) 2024 Company: Bidr Ltd.
 *
 * @author Sharp
 * @since 2025/8/3 13:47
 */

public interface ElasticsearchCountRepoInf<T> extends ElasticsearchBaseRepoInf<T> {
    /**
     * 索引所有数据数量
     *
     * @return 数量
     * @throws IOException 异常
     */
    default long count() throws IOException {
        return count(null);
    }

    /**
     * 索引所有数据数量
     *
     * @param query 查询条件
     * @return 数量
     * @throws IOException 异常
     */
    default long count(Query query) throws IOException {
        CountRequest request = CountRequest.of(c -> c.index(getIndexName()).query(query));
        return getClient().count(request).count();
    }

}