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
    // 强制指定 ES 类型（可选）
    EsFieldType type() default EsFieldType.TEXT;

    // 是否加 .keyword 子字段
    boolean keyword() default false;

    // 是否参与索引
    boolean index() default true;

    // 是否开启 doc_values
    boolean docValues() default true;

    // keyword 默认长度限制
    int ignoreAbove() default 256;

    int scalingFactor() default 100;

    boolean useIk() default false;

    String ikAnalyzer() default "ik_max_word";

    String ikFieldSuffix() default "ik";

    boolean usePinyin() default false;

    String pinyinAnalyzer() default "pinyin_analyzer";

    String pinyinFieldSuffix() default "pinyin";

    boolean useStConvert() default false;

    String stConvertAnalyzer() default "ik_smart_stconvert";

    String stConvertFieldSuffix() default "stConvert";

    boolean useHanLP() default true;

    String hanlpFieldSuffix() default "hanlp";

}