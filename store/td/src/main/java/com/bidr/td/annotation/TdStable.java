package com.bidr.td.annotation;

import java.lang.annotation.*;

@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface TdStable {
    String value();                    // 超级表名
    boolean autoDrop() default false;  // 字段删除时是否自动 DROP
}
