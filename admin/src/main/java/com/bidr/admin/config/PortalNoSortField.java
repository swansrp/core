package com.bidr.admin.config;

import java.lang.annotation.*;

/**
 * Title: PortalNoSortField
 * Description: Copyright: Copyright (c) 2024 Company: Bidr Ltd.
 *
 * @author Sharp
 * @since 2024/10/22 15:06
 */

@Target({ElementType.FIELD})
@Retention(RetentionPolicy.RUNTIME)
@Inherited
@Documented
public @interface PortalNoSortField {
}