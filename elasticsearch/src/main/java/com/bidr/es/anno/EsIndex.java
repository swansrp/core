package com.bidr.es.anno;

import java.lang.annotation.*;

/**
 * Title: EsIndex
 * Description: Copyright: Copyright (c) 2025 Company: Bidr Ltd.
 *
 * @author Sharp
 * @since 2025/8/1 11:20
 */

@Target(ElementType.TYPE)
@Inherited
@Retention(RetentionPolicy.RUNTIME)
public @interface EsIndex {
    String name() default "";

    String shards() default "1";

    String replicas() default "1";
}