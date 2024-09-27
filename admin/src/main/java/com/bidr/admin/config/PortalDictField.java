package com.bidr.admin.config;

import com.bidr.kernel.constant.dict.Dict;

import java.lang.annotation.*;

/**
 * Title: PortalDictField
 * Description: Copyright: Copyright (c) 2024 Company: Bidr Ltd.
 *
 * @author Sharp
 * @since 2024/9/27 8:53
 */
@Target({ElementType.FIELD})
@Retention(RetentionPolicy.RUNTIME)
@Inherited
@Documented
public @interface PortalDictField {
    Class<? extends Dict> value();
}
