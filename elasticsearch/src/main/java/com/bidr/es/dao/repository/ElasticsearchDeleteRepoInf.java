package com.bidr.es.dao.repository;

import java.io.Serializable;
import java.util.List;

/**
 * Title: ElasticsearchSelectRepoInf
 * Description: Copyright: Copyright (c) 2024 Company: Bidr Ltd.
 *
 * @author Sharp
 * @since 2025/8/3 13:47
 */

public interface ElasticsearchDeleteRepoInf<T> extends ElasticsearchBaseRepoInf<T> {
    
    boolean deleteById(Serializable id);

    boolean deleteById(T entity);

    boolean deleteById(List<T> entity);
}