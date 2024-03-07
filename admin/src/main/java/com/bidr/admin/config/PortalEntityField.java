package com.bidr.admin.config;

import java.lang.annotation.*;

/**
 * Title: PortalEntity
 * Description: Copyright: Copyright (c) 2023
 *
 * @author Sharp
 * @since 2024/01/24 16:09
 */
@Target({ElementType.FIELD})
@Retention(RetentionPolicy.RUNTIME)
@Inherited
@Documented
public @interface PortalEntityField {
    /***
     * 对应的entity类
     * @return
     */
    Class<?> entity();

    /**
     * 表别名
     *
     * @return
     */
    String alias() default "";

    /**
     * 对应字段名
     *
     * @return
     */
    String field();
}
