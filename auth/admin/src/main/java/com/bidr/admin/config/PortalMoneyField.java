package com.bidr.admin.config;

import java.lang.annotation.*;

/**
 * Title: PortalMoneyField
 * Description: Copyright: Copyright (c) 2024 Company: Bidr Ltd.
 *
 * @author Sharp
 * @since 2024/9/27 8:59
 */

@Target({ElementType.FIELD})
@Retention(RetentionPolicy.RUNTIME)
@Inherited
@Documented
public @interface PortalMoneyField {
    int unit() default 10000;

    int fix() default 2;
}