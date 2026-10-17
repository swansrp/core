package com.bidr.kernel.config.response;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Title: Accept
 * Description: 异名复制映射（替代 diboot com.diboot.core.data.copy.Accept）。
 * convert 复制时从源对象读取 name 指定字段的值，写入当前标注字段（类型不匹配时转为字符串）。
 * Copyright: Copyright (c) 2026 Company: Bidr Ltd.
 *
 * @author Sharp
 * @since 2026/09/08
 */
@Target(ElementType.FIELD)
@Retention(RetentionPolicy.RUNTIME)
public @interface Accept {

    /**
     * 源对象中被复制的字段名
     */
    String name();
}
