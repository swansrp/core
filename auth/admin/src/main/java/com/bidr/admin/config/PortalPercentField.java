package com.bidr.admin.config;

import java.lang.annotation.*;

/**
 * Title: PortalPercentField
 * Description: Copyright: Copyright (c) 2024 Company: Bidr Ltd.
 *
 * @author Sharp
 * @since 2024/9/27 8:59
 */

@Target({ElementType.FIELD})
@Retention(RetentionPolicy.RUNTIME)
@Inherited
@Documented
public @interface PortalPercentField {
    int unit() default 100;

    int fix() default 2;
}