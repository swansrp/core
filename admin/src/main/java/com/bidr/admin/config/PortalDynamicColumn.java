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
@Repeatable(PortalDynamicColumns.class)
public @interface PortalDynamicColumn {
    String condition() default "true";

    String script();

    String prefix() default "";

    String suffix() default "";
}
