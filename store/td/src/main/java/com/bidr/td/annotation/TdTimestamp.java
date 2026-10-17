package com.bidr.td.annotation;

import java.lang.annotation.*;

@Target(ElementType.FIELD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface TdTimestamp {
    String name() default "ts";  // 时间戳列名
}
