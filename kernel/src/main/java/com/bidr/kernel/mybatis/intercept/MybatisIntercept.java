package com.bidr.kernel.mybatis.intercept;

import org.apache.ibatis.mapping.MappedStatement;

import java.lang.annotation.Annotation;
import java.lang.reflect.Method;

/**
 * Title: MybatisIntercept
 * Description: Copyright: Copyright (c) 2019 Company: BHFAE
 *
 * @author Sharp
 * @since 2022/7/25 11:20
 */
public interface MybatisIntercept {
    /**
     * 获取注解
     *
     * @param mappedStatement
     * @param annoClass
     * @param <T>
     * @return
     */
    default <T extends Annotation> T getAnnotation(MappedStatement mappedStatement, Class<T> annoClass) {
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
