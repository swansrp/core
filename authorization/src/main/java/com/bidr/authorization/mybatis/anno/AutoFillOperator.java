package com.bidr.authorization.mybatis.anno;

import java.lang.annotation.*;

/**
 * Title: AccountContextFill
 * Description: Copyright: Copyright (c) 2023 Company: Sharp Ltd.
 *
 * @author Sharp
 * @since 2023/04/28 13:11
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface AutoFillOperator {
    boolean force() default false;
}
