package com.bidr.kernel.mybatis.repository.inf;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;

import java.io.Serializable;
import java.util.List;
import java.util.Map;

/**
 * Title: SqlSelectRepo
 * Description: Copyright: Copyright (c) 2019 Company: BHFAE
 *
 * @author Sharp
 * @date 2022/6/8 15:53
 */
public interface SqlSelectRepo<T> {

    List<T> select();

    Page<T> select(long currentPage, long pageSize);

    List<T> select(Wrapper<T> wrapper);

    Page<T> select(Wrapper<T> wrapper, long currentPage, long pageSize);

    List<T> select(Map<String, Object> propertyMap);

    Page<T> select(Map<String, Object> propertyMap, long currentPage, long pageSize);

    List<T> select(String propertyName, List<?> propertyList);

    Page<T> select(String propertyName, List<?> propertyList, long currentPage, long pageSize);

    T selectOne(Wrapper<T> wrapper);

    T selectOne(Map<String, Object> propertyMap);

    T selectById(T entity);

    T selectById(Serializable id);


}
