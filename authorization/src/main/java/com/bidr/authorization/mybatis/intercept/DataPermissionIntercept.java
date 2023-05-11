package com.bidr.authorization.mybatis.intercept;

import com.bidr.authorization.mybatis.anno.DataPermission;
import com.bidr.authorization.mybatis.permission.DataPermissionInf;
import com.bidr.kernel.mybatis.intercept.BaseIntercept;
import com.bidr.kernel.mybatis.parse.SqlParseUtil;
import com.bidr.kernel.utils.FuncUtil;
import com.bidr.kernel.utils.ReflectionUtil;
import lombok.extern.slf4j.Slf4j;
import net.sf.jsqlparser.expression.Expression;
import net.sf.jsqlparser.expression.Parenthesis;
import net.sf.jsqlparser.expression.operators.conditional.OrExpression;
import org.apache.ibatis.cache.CacheKey;
import org.apache.ibatis.mapping.BoundSql;
import org.apache.ibatis.mapping.MappedStatement;
import org.apache.ibatis.session.ResultHandler;
import org.apache.ibatis.session.RowBounds;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;

/**
 * Title: DataPermissionIntercept
 * Description: Copyright: Copyright (c) 2023 Company: Sharp Ltd.
 *
 * @author Sharp
 * @since 2023/05/02 21:23
 */
@Slf4j
@Component
public class DataPermissionIntercept extends BaseIntercept {

    @Lazy
    @Autowired(required = false)
    private List<DataPermissionInf> dataPermissionList;

    @Override
    public void proceed(MappedStatement mappedStatement, Object parameter, RowBounds rowBounds,
                        ResultHandler resultHandler, CacheKey cacheKey, BoundSql boundSql) {
        if (FuncUtil.isNotEmpty(dataPermissionList)) {
            String sql = boundSql.getSql();
            Map<String, String> tableAliasMap = SqlParseUtil.buildTableAliasMap(sql);
            Expression dataPermissionExpress = null;
            for (DataPermissionInf dataPermissionInf : dataPermissionList) {
                if (dataPermissionInf.needFilter(tableAliasMap)) {
                    Expression expression = dataPermissionInf.expression(tableAliasMap);
                    if (FuncUtil.isNotEmpty(expression)) {
                        if (FuncUtil.isEmpty(dataPermissionExpress)) {
                            dataPermissionExpress = new Parenthesis(expression);
                        } else {
                            dataPermissionExpress = new OrExpression(dataPermissionExpress, expression);
                        }
                    }
                }
            }
            sql = SqlParseUtil.mergeWhere(sql, dataPermissionExpress);
            replaceSql(boundSql, sql);
        }
    }

    private static void replaceSql(BoundSql boundSql, String newSql) {
        try {
            Field field = boundSql.getClass().getDeclaredField("sql");
            field.setAccessible(true);
            field.set(boundSql, newSql);
        } catch (NoSuchFieldException | IllegalAccessException e) {
            throw new RuntimeException(e);
        }
    }

    private Class<? extends DataPermissionInf>[] getPermission(String fullMethodName) {
        int methodIndex = fullMethodName.lastIndexOf(".");
        String className = fullMethodName.substring(0, methodIndex);
        String methodName = fullMethodName.substring(methodIndex + 1);
        Class<?> targetCls;
        try {
            targetCls = Class.forName(className);
        } catch (ClassNotFoundException e) {
            return null;
        }
        Method method = ReflectionUtil.getMethod(targetCls, methodName);
        if (FuncUtil.isNotEmpty(method)) {
            DataPermission annotation = method.getAnnotation(DataPermission.class);
            return annotation.value();
        }
        return null;
    }
}
