package com.bidr.kernel.mybatis.anno;

import java.lang.annotation.*;

/**
 * Title: AutoInsert
 * Description: Copyright: Copyright (c) 2023 Company: Sharp Ltd.
 *
 * @author Sharp
 * @date 2023/04/28 11:40
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.FIELD)
public @interface AutoInsert {
    String sql() default "";

    String seq() default "";

    boolean override() default false;
}
