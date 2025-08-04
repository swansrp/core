package com.bidr.es.dao.repository;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.bidr.kernel.vo.query.QueryReqVO;

import java.io.Serializable;
import java.util.List;
import java.util.Map;

/**
 * Title: ElasticsearchSelectRepoInf
 * Description: Copyright: Copyright (c) 2024 Company: Bidr Ltd.
 *
 * @author Sharp
 * @since 2025/8/3 13:47
 */

public interface ElasticsearchSelectRepoInf<T> extends ElasticsearchBaseRepoInf<T> {
    List<T> select();

    Page<T> select(long currentPage, long pageSize);

    Page<T> select(QueryReqVO req);

    List<T> select(Wrapper<T> wrapper);

    Page<T> select(Wrapper<T> wrapper, long currentPage, long pageSize);

    Page<T> select(Wrapper<T> wrapper, QueryReqVO req);

    Page<T> select(Wrapper<T> wrapper, long currentPage, long pageSize, boolean searchCount);

    Page<T> select(Wrapper<T> wrapper, QueryReqVO req, boolean searchCount);

    List<T> select(Map<String, Object> propertyMap);

    T selectOne(Wrapper<T> wrapper);

    T selectById(T entity);

    T selectById(Serializable id);
}