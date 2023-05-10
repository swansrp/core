package com.bidr.authorization.mybatis.intercept;

import com.bidr.authorization.mybatis.anno.DataPermission;
import com.bidr.authorization.mybatis.permission.DataPermissionInf;
import com.bidr.kernel.mybatis.intercept.BaseIntercept;
import com.bidr.kernel.mybatis.parse.SqlParseUtil;
import com.bidr.kernel.utils.BeanUtil;
import com.bidr.kernel.utils.FuncUtil;
import com.bidr.kernel.utils.ReflectionUtil;
import lombok.extern.slf4j.Slf4j;
import net.sf.jsqlparser.expression.Expression;
import net.sf.jsqlparser.expression.operators.conditional.OrExpression;
import org.apache.ibatis.cache.CacheKey;
import org.apache.ibatis.mapping.BoundSql;
import org.apache.ibatis.mapping.MappedStatement;
import org.apache.ibatis.session.ResultHandler;
import org.apache.ibatis.session.RowBounds;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Map;

/**
 * Title: DataPermissionIntercept
 * Description: Copyright: Copyright (c) 2023 Company: Sharp Ltd.
 *
 * @author Sharp
 * @since 2023/05/02 21:23
 */
@Slf4j
public class DataPermissionIntercept extends BaseIntercept {

    @Override
    public void proceed(MappedStatement mappedStatement, Object parameter, RowBounds rowBounds,
                        ResultHandler resultHandler, CacheKey cacheKey, BoundSql boundSql) {
        Class<? extends DataPermissionInf>[] permissions = getPermission(mappedStatement.getId());
        if (FuncUtil.isNotEmpty(permissions)) {
            String sql = boundSql.getSql();
            Map<String, String> tableAliasMap = SqlParseUtil.buildTableAliasMap(sql);
            Expression dataPermissionExpress = buildDataPermissionExpress(permissions, tableAliasMap);
            sql = SqlParseUtil.mergeWhere(sql, dataPermissionExpress);
            replaceSql(boundSql, sql);
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

    private Expression buildDataPermissionExpress(Class<? extends DataPermissionInf>[] permissionList,
                                                  Map<String, String> tableAliasMap) {
        Expression dataPermissionExpress = null;
        for (Class<? extends DataPermissionInf> permissionInfClass : permissionList) {
            DataPermissionInf permissionInf = BeanUtil.getBean(permissionInfClass);
            if (FuncUtil.isEmpty(permissionInf)) {
                log.error("未定义数据权限 {}", permissionInfClass.getSimpleName());
                continue;
            }
            Expression dataPermit = permissionInf.expression(tableAliasMap);
            if (FuncUtil.isNotEmpty(dataPermit)) {
                if (FuncUtil.isEmpty(dataPermissionExpress)) {
                    dataPermissionExpress = dataPermit;
                } else {
                    dataPermissionExpress = new OrExpression(dataPermissionExpress, dataPermit);
                }
            }
        }
        return dataPermissionExpress;
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
}
