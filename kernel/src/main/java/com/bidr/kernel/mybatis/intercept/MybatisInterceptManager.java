package com.bidr.kernel.mybatis.intercept;

import lombok.extern.slf4j.Slf4j;
import org.apache.ibatis.cache.CacheKey;
import org.apache.ibatis.executor.Executor;
import org.apache.ibatis.executor.parameter.ParameterHandler;
import org.apache.ibatis.executor.resultset.DefaultResultSetHandler;
import org.apache.ibatis.executor.resultset.ResultSetHandler;
import org.apache.ibatis.executor.statement.StatementHandler;
import org.apache.ibatis.mapping.BoundSql;
import org.apache.ibatis.mapping.MappedStatement;
import org.apache.ibatis.plugin.Interceptor;
import org.apache.ibatis.plugin.Intercepts;
import org.apache.ibatis.plugin.Invocation;
import org.apache.ibatis.plugin.Signature;
import org.apache.ibatis.session.ResultHandler;
import org.apache.ibatis.session.RowBounds;
import org.springframework.context.annotation.Lazy;

import javax.annotation.Resource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.Statement;
import java.util.List;

/**
 * Title: MybatisInterceptManager
 * Description: Copyright: Copyright (c) 2019 Company: BHFAE
 *
 * @author Sharp
 * @date 2022/7/25 14:54
 */
@Slf4j
@Intercepts({
        @Signature(
                type = Executor.class,
                method = "query",
                args = {MappedStatement.class, Object.class, RowBounds.class, ResultHandler.class, CacheKey.class, BoundSql.class}),
        @Signature(
                type = StatementHandler.class,
                method = "prepare",
                args = {Connection.class, Integer.class}),
        @Signature(
                type = ParameterHandler.class,
                method = "setParameters",
                args = {PreparedStatement.class}),
        @Signature(
                type = ResultSetHandler.class,
                method = "handleResultSets",
                args = {Statement.class})})
// @Component
public class MybatisInterceptManager implements Interceptor {

    @Lazy
    @Resource
    private MybatisInterceptManager mybatisInterceptManager;
    @Resource
    private List<ExecutorIntercept> executorIntercepts;
    @Resource
    private List<StatementIntercept> statementIntercepts;
    @Resource
    private List<ParameterHandlerIntercept> parameterHandlerIntercepts;
    @Resource
    private List<ResultSetHandlerIntercept> resultSetHandlerIntercepts;

    @Override
    public Object intercept(Invocation invocation) throws Throwable {
        if (invocation.getTarget() instanceof Executor) {
            MappedStatement mappedStatement = (MappedStatement) invocation.getArgs()[0];
            Object parameter = invocation.getArgs()[1];
            RowBounds rowBounds = (RowBounds) invocation.getArgs()[2];
            ResultHandler resultHandler = (ResultHandler) invocation.getArgs()[3];
            CacheKey cacheKey = (CacheKey) invocation.getArgs()[4];
            BoundSql boundSql = (BoundSql) invocation.getArgs()[5];
            for (ExecutorIntercept executorIntercept : executorIntercepts) {
                executorIntercept.proceed(mappedStatement, parameter, rowBounds, resultHandler, cacheKey, boundSql);
            }
            return invocation.proceed();
        } else if (invocation.getTarget() instanceof StatementHandler) {
            StatementHandler statementHandler = (StatementHandler) invocation.getTarget();
            for (StatementIntercept statementIntercept : statementIntercepts) {
                statementIntercept.proceed(statementHandler);
            }
            return invocation.proceed();
        } else if (invocation.getTarget() instanceof ParameterHandler) {
            ParameterHandler parameterHandler = (ParameterHandler) invocation.getTarget();
            for (ParameterHandlerIntercept parameterHandlerIntercept : parameterHandlerIntercepts) {
                parameterHandlerIntercept.proceed(parameterHandler);
            }
            return invocation.proceed();
        } else if (invocation.getTarget() instanceof ResultSetHandler) {
            DefaultResultSetHandler handler = (DefaultResultSetHandler) invocation.getTarget();
            Statement statement = (Statement) invocation.getArgs()[0];
            List<Object> resultList = handler.handleResultSets(statement);
            for (ResultSetHandlerIntercept resultSetHandlerIntercept : resultSetHandlerIntercepts) {
                resultSetHandlerIntercept.proceed(resultList);
            }
            return resultList;
        } else {
            return invocation.proceed();
        }
    }
}
