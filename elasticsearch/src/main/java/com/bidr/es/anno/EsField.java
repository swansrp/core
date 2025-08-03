package com.bidr.es.anno;

import com.bidr.es.config.EsFieldType;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Title: EsIndex
 * Description: Copyright: Copyright (c) 2025 Company: Bidr Ltd.
 *
 * @author Sharp
 * @since 2025/8/1 11:20
 */

@Target(ElementType.FIELD)
@Retention(RetentionPolicy.RUNTIME)
public @interface EsField {
    EsFieldType type() default EsFieldType.TEXT; // text, keyword, completion, etc.

    boolean index() default false;


    boolean keyword() default false; // 是否同时生成 keyword 字段

    boolean useIk() default false;

    boolean usePinyin() default false;

    boolean useStConvert() default false;

    boolean useHanLP() default true;


}