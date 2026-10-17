package com.bidr.kernel.mybatis.anno;

import java.lang.annotation.*;

/**
 * Title: AutoUpdate
 * Description: Copyright: Copyright (c) 2023 Company: Sharp Ltd.
 *
 * @author Sharp
 * @since 2023/04/28 11:40
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.FIELD)
public @interface AutoUpdate {
    String sql() default "";
}
