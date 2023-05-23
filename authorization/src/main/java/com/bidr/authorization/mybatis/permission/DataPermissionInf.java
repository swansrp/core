package com.bidr.authorization.mybatis.permission;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.bidr.kernel.common.func.GetFunc;
import com.bidr.kernel.utils.FuncUtil;
import com.bidr.kernel.utils.LambdaUtil;
import net.sf.jsqlparser.expression.Expression;
import org.springframework.boot.CommandLineRunner;

import java.util.Map;

/**
 * Title: DataPermissionInf
 * Description: Copyright: Copyright (c) 2023 Company: Sharp Ltd.
 *
 * @author Sharp
 * @since 2023/05/02 22:18
 */
public interface DataPermissionInf extends CommandLineRunner {

    /**
     * 构建控制查询语句
     *
     * @param tableAliasMap
     * @return 查询条件
     */
    Expression expression(Map<String, String> tableAliasMap);

    /**
     * 根据表名->表别名映射关系 计算指定列名
     *
     * @param tableAliasMap 映射关系
     * @param tableName     表名
     * @param columnName    列名
     * @return 别名+列名
     */
    default String getAliasColumnName(Map<String, String> tableAliasMap, String tableName, String columnName) {
        String alias = tableAliasMap.get(tableName);
        if (FuncUtil.isNotEmpty(alias)) {
            columnName = alias + "." + columnName;
        }
        return columnName;
    }

    /**
     * 根据传入适用字段 初始化适用表
     *
     * @param function 权限字段
     */
    default <T> void init(GetFunc<T, ?> function) {
        TableName tableName = LambdaUtil.getRealClass(function).getAnnotation(TableName.class);
        TableField tableField = LambdaUtil.getField(function).getAnnotation(TableField.class);
        if (FuncUtil.isNotEmpty(tableName) && FuncUtil.isNotEmpty(tableField)) {
            getFilterMap().put(tableName.value(), tableField.value());
        }
    }

    /**
     * 配置该数据权限适用 表名 -> 字段名
     *
     * @return 数据权限适用表
     */
    Map<String, String> getFilterMap();

    /**
     * 判定该sql语句是否需要进行本数据权限规则就行权限校验
     *
     * @param tableAliasMap sql语句别名表
     * @return 判定结果
     */
    default boolean needFilter(Map<String, String> tableAliasMap) {
        boolean res = false;
        for (String table : tableAliasMap.keySet()) {
            res |= getFilterMap().containsKey(table);
        }
        return res;
    }
}
