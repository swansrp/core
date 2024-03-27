package com.bidr.kernel.mybatis.repository.inf;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.bidr.kernel.vo.portal.AdvancedQueryReq;
import com.bidr.kernel.vo.portal.QueryConditionReq;
import com.bidr.kernel.vo.query.QueryReqVO;
import com.github.yulichang.wrapper.MPJLambdaWrapper;

import java.io.Serializable;
import java.util.List;
import java.util.Map;

/**
 * Title: SqlSelectRepo
 * Description: Copyright: Copyright (c) 2019 Company: BHFAE
 *
 * @author Sharp
 * @since 2022/6/8 15:53
 */
public interface SqlSelectRepo<T> {

    List<T> select();

    Page<T> select(long currentPage, long pageSize);

    Page<T> select(QueryReqVO req);

    List<T> select(Wrapper<T> wrapper);

    Page<T> select(Wrapper<T> wrapper, long currentPage, long pageSize);

    Page<T> select(QueryConditionReq req);

    <VO> Page<VO> select(AdvancedQueryReq req, Map<String, String> aliasMap, Class<VO> vo);

    <VO> Page<VO> select(AdvancedQueryReq req, Map<String, String> aliasMap, MPJLambdaWrapper<T> wrapper, boolean needGroup, Class<VO> vo);

    Page<T> select(Wrapper<T> wrapper, QueryReqVO req);

    Page<T> select(Wrapper<T> wrapper, long currentPage, long pageSize, boolean searchCount);

    Page<T> select(Wrapper<T> wrapper, QueryReqVO req, boolean searchCount);

    List<T> select(Map<String, Object> propertyMap);

    Page<T> select(Map<String, Object> propertyMap, long currentPage, long pageSize);

    Page<T> select(Map<String, Object> propertyMap, QueryReqVO req);

    Page<T> select(Map<String, Object> propertyMap, long currentPage, long pageSize, boolean searchCount);

    Page<T> select(Map<String, Object> propertyMap, QueryReqVO req, boolean searchCount);

    List<T> select(String propertyName, List<?> propertyList);

    Page<T> select(String propertyName, List<?> propertyList, long currentPage, long pageSize);

    Page<T> select(String propertyName, List<?> propertyList, QueryReqVO req);

    Page<T> select(String propertyName, List<?> propertyList, long currentPage, long pageSize, boolean searchCount);

    Page<T> select(String propertyName, List<?> propertyList, QueryReqVO req, boolean searchCount);

    T selectOne(Wrapper<T> wrapper);

    T selectOne(Map<String, Object> propertyMap);

    T selectById(T entity);

    T selectById(Serializable id);


}
