package com.bidr.td.annotation;

import com.bidr.td.constant.TdDataType;
import java.lang.annotation.*;

@Target(ElementType.FIELD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface TdColumn {
    String name() default "";                    // 列名，默认取字段名
    TdDataType type() default TdDataType.DOUBLE; // 列类型
    int length() default 0;                      // NCHAR/BINARY 时有效
}
