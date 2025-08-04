package com.bidr.es.dao.repository;

import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;

import java.util.Collection;

/**
 * Title: ElasticsearchSelectRepoInf
 * Description: Copyright: Copyright (c) 2024 Company: Bidr Ltd.
 *
 * @author Sharp
 * @since 2025/8/3 13:47
 */

public interface ElasticsearchUpdateRepoInf<T> extends ElasticsearchBaseRepoInf<T> {

    boolean updateById(T doc, boolean ignoreNull);

    boolean updateById(Collection<T> docs, boolean ignoreNull);

    boolean updateById(T doc, UpdateWrapper<T> wrapper, boolean ignoreNull);

    default boolean updateById(T doc) {
        return updateById(doc, true);
    }

    default boolean updateById(Collection<T> docs) {
        return updateById(docs, true);
    }

    default boolean updateById(T doc, UpdateWrapper<T> wrapper) {
        return updateById(doc, wrapper, true);
    }

}