package com.bidr.kernel.mybatis.repository.inf;

import com.baomidou.mybatisplus.core.conditions.Wrapper;

import java.io.Serializable;
import java.util.List;
import java.util.Map;

/**
 * Title: SqlDeleteRepo
 * Description: Copyright: Copyright (c) 2022 Company: Sharp Ltd.
 *
 * @author Sharp
 * @since 2022/12/08 08:53
 */
public interface SqlDeleteRepo<T> {

    boolean delete(T entity);

    boolean delete(Wrapper<T> wrapper);

    boolean delete(Map<String, Object> propertyMap);

    boolean delete(List<T> entity);

    boolean deleteById(Serializable id);

    boolean deleteById(T entity);


}
