package com.bidr.kernel.mybatis.repository.inf;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;

import java.io.Serializable;
import java.util.List;
import java.util.Map;

/**
 * Title: SqlCountRepo
 * Description: Copyright: Copyright (c) 2019 Company: BHFAE
 *
 * @author Sharp
 * @date 2022/6/8 16:04
 */
public interface SqlCountRepo<T> {

    long count(T entity);

    long count(Wrapper<T> wrapper);

    long count(Map<String, Object> propertyMap);

    boolean existed(Wrapper<T> wrapper);

    boolean existed(Map<String, Object> propertyMap);

    boolean existedById(T entity);

    boolean existedById(Serializable id);

}
