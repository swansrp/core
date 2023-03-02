package com.bidr.kernel.mybatis.intercept;

import org.apache.ibatis.cache.CacheKey;
import org.apache.ibatis.mapping.BoundSql;
import org.apache.ibatis.mapping.MappedStatement;
import org.apache.ibatis.session.ResultHandler;
import org.apache.ibatis.session.RowBounds;

/**
 * Title: ExecutorIntercept
 * Description: Copyright: Copyright (c) 2019 Company: BHFAE
 *
 * @author Sharp
 * @date 2022/7/25 11:18
 */
public interface ExecutorIntercept extends MybatisIntercept {

    /**
     *
     * @param mappedStatement
     * @param parameter
     * @param rowBounds
     * @param resultHandler
     * @param cacheKey
     * @param boundSql
     */
    void proceed(MappedStatement mappedStatement, Object parameter, RowBounds rowBounds, ResultHandler resultHandler, CacheKey cacheKey, BoundSql boundSql);
}
