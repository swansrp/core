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

    /**
     * 如果使用 batch 对应的处理函数应接受Set<Object>为参数
     * Map<Object, Object>作为返回值类型
     *
     * @return 是否支持列表并行处理
     */
    boolean batch() default false;

    /**
     * DiBoot 模块 bind处理之后进行
     *
     * @return 是否在bind之后处理
     */
    boolean after() default false;
}
