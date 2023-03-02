package com.bidr.kernel.mybatis.intercept;

import org.apache.ibatis.cache.CacheKey;
import org.apache.ibatis.executor.parameter.ParameterHandler;
import org.apache.ibatis.executor.statement.StatementHandler;
import org.apache.ibatis.mapping.BoundSql;
import org.apache.ibatis.mapping.MappedStatement;
import org.apache.ibatis.session.ResultHandler;
import org.apache.ibatis.session.RowBounds;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Title: BaseIntercept
 * Description: Copyright: Copyright (c) 2019 Company: BHFAE
 *
 * @author Sharp
 * @date 2022/7/26 14:27
 */
@Component
public class BaseIntercept implements ExecutorIntercept, StatementIntercept, ParameterHandlerIntercept, ResultSetHandlerIntercept {
    @Override
    public void proceed(MappedStatement mappedStatement, Object parameter, RowBounds rowBounds, ResultHandler resultHandler, CacheKey cacheKey, BoundSql boundSql) {

    }

    @Override
    public void proceed(ParameterHandler parameterHandler) {

    }

    @Override
    public void proceed(List<Object> resultList) {

    }

    @Override
    public void proceed(StatementHandler statementHandler) {

    }
}
