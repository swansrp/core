package com.bidr.kernel.common.convert;

import java.lang.annotation.*;

/**
 * Title: Convert
 * Description: Copyright: Copyright (c) 2024 Company: Bidr Ltd.
 *
 * @author Sharp
 * @since 2024/8/15 11:45
 */

@Target({ElementType.FIELD})
@Retention(RetentionPolicy.RUNTIME)
@Inherited
@Documented
public @interface Convert {
    Class<?> util() default Object.class;

    String bean() default "";

    String method() default "";

    String field() default "";

    boolean ignoreNull() default true;
}
