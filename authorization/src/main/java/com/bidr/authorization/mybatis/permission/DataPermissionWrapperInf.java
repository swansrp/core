package com.bidr.authorization.mybatis.permission;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.bidr.kernel.common.func.GetFunc;
import com.bidr.kernel.utils.FuncUtil;
import com.bidr.kernel.utils.LambdaUtil;
import org.springframework.boot.CommandLineRunner;


import java.util.Map;

/**
 * Title: DataPermissionWrapperInf
 * Description: Copyright: Copyright (c) 2023
 *
 * @author Sharp
 * @since 2023/05/10 17:23
 */
public interface DataPermissionWrapperInf extends CommandLineRunner {
    void buildDataPermissionWrapper(Wrapper wrapper);

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
}
