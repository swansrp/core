package com.bidr.kernel.mybatis.intercept;

import org.apache.ibatis.cache.CacheKey;
import org.apache.ibatis.executor.parameter.ParameterHandler;
import org.apache.ibatis.executor.statement.StatementHandler;
import org.apache.ibatis.mapping.BoundSql;
import org.apache.ibatis.mapping.MappedStatement;
import org.apache.ibatis.session.ResultHandler;
import org.apache.ibatis.session.RowBounds;

import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.util.List;

/**
 * Title: BaseIntercept
 * Description: Copyright: Copyright (c) 2019 Company: BHFAE
 *
 * @author Sharp
 * @since 2022/7/26 14:27
 */
public class BaseIntercept implements ExecutorIntercept, StatementIntercept, ParameterHandlerIntercept,
        ResultSetHandlerIntercept {

    @Override
    public void proceed(MappedStatement mappedStatement, Object parameter, RowBounds rowBounds,
                        ResultHandler resultHandler, CacheKey cacheKey, BoundSql boundSql) {

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

    protected <T extends Annotation> T getAnnotation(MappedStatement mappedStatement, Class<T> annoClass) {
        try {
            String id = mappedStatement.getId();
            String className = id.substring(0, id.lastIndexOf("."));
            String methodName = id.substring(id.lastIndexOf(".") + 1);
            final Method[] method = Class.forName(className).getMethods();
            for (Method me : method) {
                if (me.getName().equals(methodName) && me.isAnnotationPresent(annoClass)) {
                    return me.getAnnotation(annoClass);
                }
            }
        } catch (Exception ex) {
            return null;
        }
        return null;
    }
}
