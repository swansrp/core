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
    Class<?> entity() default Object.class;

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
    String field() default "";

    /**
     * 引用实体侧的回填取值字段（写入 sys_portal_column.entity_field）。
     * 缺省回落 field()——但两者语义不同：field 是 SELECT/别名的取列，编辑回填要取的是实体的行 id 列。
     * 例：deptName 关联 AcDept，field="name"（显示名取列），entityField="deptId"（回填进 db_field 的值）。
     *
     * @return
     */
    String entityField() default "";

    /**
     * 本实体对应字段
     *
     * @return
     */
    String joinField() default "";

    boolean group() default false;

    boolean aggregation() default false;

    String origFieldName() default "";

    boolean select() default true;
}
