package com.bidr.kernel.config.db;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.core.injector.AbstractMethod;
import com.baomidou.mybatisplus.core.metadata.TableInfo;
import com.baomidou.mybatisplus.extension.injector.methods.InsertBatchSomeColumn;
import com.github.jeffreyning.mybatisplus.anno.MppMultiId;
import com.github.jeffreyning.mybatisplus.base.DeleteByMultiIdMethod;
import com.github.jeffreyning.mybatisplus.base.SelectByMultiIdMethod;
import com.github.jeffreyning.mybatisplus.base.UpdateByMultiIdMethod;
import com.github.yulichang.injector.MPJSqlInjector;

import java.lang.reflect.Field;
import java.util.List;

/**
 * Title: MPJConfig
 * Description: Copyright: Copyright (c) 2022 Company: Sharp Ltd.
 *
 * @author Sharp
 * @since 2022/12/09 10:49
 */
public class MPJConfig extends MPJSqlInjector {

    @Override
    public List<AbstractMethod> getMethodList(Class<?> mapperClass, TableInfo tableInfo) {
        List<AbstractMethod> methodList = super.getMethodList(mapperClass, tableInfo);
        if (hasMultiIdAnnotation(tableInfo.getEntityType())) {
            methodList.add(new SelectByMultiIdMethod());
            methodList.add(new UpdateByMultiIdMethod());
            methodList.add(new DeleteByMultiIdMethod());
        }
        methodList.add(new InsertBatchSomeColumn(t -> t.getFieldFill() != FieldFill.UPDATE));
        return methodList;
    }

    /**
     * 判断实体类是否包含 @MppMultiId 注解字段，避免对普通实体做无效 MultiId 方法注入
     */
    private boolean hasMultiIdAnnotation(Class<?> entityClass) {
        Class<?> cls = entityClass;
        while (cls != null && cls != Object.class) {
            for (Field field : cls.getDeclaredFields()) {
                if (field.isAnnotationPresent(MppMultiId.class)) {
                    return true;
                }
            }
            cls = cls.getSuperclass();
        }
        return false;
    }
}


