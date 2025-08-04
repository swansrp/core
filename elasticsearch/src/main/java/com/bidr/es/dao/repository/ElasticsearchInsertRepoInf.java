package com.bidr.es.dao.repository;

import java.util.Collection;

/**
 * Title: ElasticsearchSelectRepoInf
 * Description: Copyright: Copyright (c) 2024 Company: Bidr Ltd.
 *
 * @author Sharp
 * @since 2025/8/3 13:47
 */

public interface ElasticsearchInsertRepoInf<T> extends ElasticsearchBaseRepoInf<T> {

    boolean insert(T doc);

    boolean insert(Collection<T> docs);
}