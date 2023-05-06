package com.bidr.kernel.mybatis.repository.inf;

import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;

import java.util.Collection;
import java.util.Map;

/**
 * Title: SqlUpdateRepo
 * Description: Copyright: Copyright (c) 2022 Company: Sharp Ltd.
 *
 * @author Sharp
 * @date 2022/12/07 18:38
 */
public interface SqlUpdateRepo<T> {

    boolean updateById(T entity);

    boolean updateById(T entity, boolean ignoreNull);

    boolean update(T entity, UpdateWrapper<T> wrapper);

    boolean update(T entity, UpdateWrapper<T> wrapper, boolean ignoreNull);

    boolean update(T entity, Map<String, Object> propertyMap);

    boolean update(T entity, Map<String, Object> propertyMap, boolean ignoreNull);

    boolean insertOrUpdate(T entity, UpdateWrapper<T> wrapper);

    boolean insertOrUpdate(Collection<T> entity, UpdateWrapper<T> wrapper);

    boolean insertOrUpdate(T entity);

    boolean insertOrUpdate(Collection<T> entity);

}
