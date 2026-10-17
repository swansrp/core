package com.bidr.td.annotation;

import com.bidr.td.constant.TdDataType;
import java.lang.annotation.*;

@Target(ElementType.FIELD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface TdTag {
    String name() default "";                    // tag 名，默认取字段名
    TdDataType type() default TdDataType.BINARY; // tag 类型
    int length() default 64;                     // BINARY/NCHAR 长度
}
