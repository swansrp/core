package com.bidr.authorization.mybatis.intercept;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.bidr.authorization.bo.account.AccountInfo;
import com.bidr.authorization.bo.role.RoleInfo;
import com.bidr.authorization.constants.dict.DataPermitScopeDict;
import com.bidr.authorization.holder.AccountContext;
import com.bidr.authorization.mybatis.anno.DataPermission;
import com.bidr.authorization.mybatis.permission.DataPermissionInf;
import com.bidr.kernel.constant.err.ErrCodeSys;
import com.bidr.kernel.mybatis.intercept.BaseIntercept;
import com.bidr.kernel.mybatis.parse.SqlParseUtil;
import com.bidr.kernel.utils.FuncUtil;
import com.bidr.kernel.utils.LambdaUtil;
import com.bidr.kernel.utils.ReflectionUtil;
import com.bidr.kernel.validate.Validator;
import lombok.AllArgsConstructor;
import lombok.Data;
import net.sf.jsqlparser.expression.Expression;
import net.sf.jsqlparser.expression.operators.conditional.OrExpression;
import org.apache.ibatis.cache.CacheKey;
import org.apache.ibatis.mapping.BoundSql;
import org.apache.ibatis.mapping.MappedStatement;
import org.apache.ibatis.session.ResultHandler;
import org.apache.ibatis.session.RowBounds;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Title: DataPermissionIntercept
 * Description: Copyright: Copyright (c) 2023 Company: Sharp Ltd.
 *
 * @author Sharp
 * @since 2023/05/02 21:23
 */
@Component
public class DataPermissionIntercept extends BaseIntercept implements CommandLineRunner {

    private static final Map<String, List<DataPermissionBo>> map = new ConcurrentHashMap<>();
    @Lazy
    @Autowired(required = false)
    private List<DataPermissionInf> dataPermissionList;

    @Override
    public void proceed(MappedStatement mappedStatement, Object parameter, RowBounds rowBounds,
                        ResultHandler resultHandler, CacheKey cacheKey, BoundSql boundSql) {
        if (!getPermission(mappedStatement.getId())) {
            String sql = boundSql.getSql();
            Map<String, String> tableAliasMap = SqlParseUtil.buildTableAliasMap(sql);
            Expression dataPermissionExpress = buildDataPermissionExpress(tableAliasMap);
            String newSql = SqlParseUtil.mergeWhere(sql, dataPermissionExpress);
            replaceSql(boundSql, newSql);
        }
    }

    private boolean getPermission(String fullMethodName) {
        int methodIndex = fullMethodName.lastIndexOf(".");
        String className = fullMethodName.substring(0, methodIndex);
        String methodName = fullMethodName.substring(methodIndex + 1);
        Class<?> targetCls;
        try {
            targetCls = Class.forName(className);
        } catch (ClassNotFoundException e) {
            return false;
        }
        Method method = ReflectionUtil.getMethod(targetCls, methodName);
        if (FuncUtil.isNotEmpty(method)) {
            DataPermission annotation = method.getAnnotation(DataPermission.class);
            return annotation != null;
        }
        return false;
    }

    private Expression buildDataPermissionExpress(Map<String, String> tableAliasMap) {
        Expression dataPermissionExpress = null;
        AccountInfo accountInfo = AccountContext.get();
        if (FuncUtil.isNotEmpty(accountInfo)) {
            Map<Long, RoleInfo> roleInfoMap = accountInfo.getRoleInfoMap();
            if (FuncUtil.isNotEmpty(roleInfoMap)) {
                for (RoleInfo roleInfo : roleInfoMap.values()) {
                    for (Map.Entry<String, List<DataPermissionBo>> entry : map.entrySet()) {
                        String tableName = entry.getKey();
                        List<DataPermissionBo> permissionList = entry.getValue();
                        if (tableAliasMap.containsKey(tableName)) {
                            for (DataPermissionBo permission : permissionList) {
                                String columnName = getColumnName(tableAliasMap, tableName, permission.getColumnName());
                                Expression dataPermit = permission.getPermission()
                                        .expression(columnName, DataPermitScopeDict.of(roleInfo.getDataScope()));
                                if (FuncUtil.isEmpty(dataPermissionExpress)) {
                                    dataPermissionExpress = dataPermit;
                                } else {
                                    dataPermissionExpress = new OrExpression(dataPermissionExpress, dataPermit);
                                }
                            }
                        }
                    }
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

    private String getColumnName(Map<String, String> tableAliasMap, String tableName, String columnName) {
        String alias = tableAliasMap.get(tableName);
        if (FuncUtil.isNotEmpty(alias)) {
            columnName = alias + "." + columnName;
        }
        return columnName;
    }

    @Override
    public void run(String... args) {
        if (FuncUtil.isNotEmpty(dataPermissionList)) {
            for (DataPermissionInf dataPermissionInf : dataPermissionList) {
                DataPermissionBo bo = new DataPermissionBo(dataPermissionInf);
                List<DataPermissionBo> permissionList = map.getOrDefault(bo.getTableName(), new ArrayList<>());
                permissionList.add(bo);
                map.put(bo.getTableName(), permissionList);
            }
        }
    }

    @Data
    @AllArgsConstructor
    static class DataPermissionBo {
        private String tableName;
        private String columnName;
        private DataPermissionInf permission;

        public DataPermissionBo(DataPermissionInf inf) {
            this.permission = inf;
            Class<?> entityClass = LambdaUtil.getRealClass(inf.getFunc());
            TableName tableNameAnno = entityClass.getAnnotation(TableName.class);
            Validator.assertNotNull(tableNameAnno, ErrCodeSys.SYS_ERR_MSG, "找不到表名");
            this.tableName = tableNameAnno.value();
            Field field = LambdaUtil.getField(inf.getFunc());
            TableField tableField = field.getAnnotation(TableField.class);
            Validator.assertNotNull(tableNameAnno, ErrCodeSys.SYS_ERR_MSG, "找不到字段名");
            this.columnName = tableField.value();
        }

    }
}
