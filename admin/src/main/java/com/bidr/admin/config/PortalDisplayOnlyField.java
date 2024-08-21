package com.bidr.admin.config;

import com.bidr.kernel.constant.CommonConst;

import java.lang.annotation.*;

/**
 * Title: PortalDisplayOnlyField
 * Description: Copyright: Copyright (c) 2024 Company: Bidr Ltd.
 *
 * @author Sharp
 * @since 2024/8/14 13:44
 */

@Target({ElementType.FIELD})
@Retention(RetentionPolicy.RUNTIME)
@Inherited
@Documented
public @interface PortalDisplayOnlyField {
    String table() default CommonConst.YES;

    String detail() default CommonConst.YES;
}
