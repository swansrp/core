package com.bidr.kernel.config.response;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 字典绑定注解 —— 将字典类型的 value 翻译为 label 回填到当前字段。
 * <p>
 * 由 {@link RespConvert#dictBindConvert} 统一处理，数据源经 {@link DictBinder} SPI 提供
 * （platform 模块的 DictBindProvider 实现了该 SPI，走平台字典缓存）。
 * <p>
 * 使用示例：
 * <pre>
 * // platform 字段的 label 翻译到 platformLabel
 * &#64;BindDict(type = "MDM_PLATFORM_DICT", field = "platform")
 * private String platformLabel;
 *
 * // 翻译结果覆盖当前字段自身（值字段与 label 字段相同）
 * &#64;BindDict(type = "QCC_ENTERPRISE_TYPE_DICT")
 * private String enterpriseType;
 * </pre>
 *
 * @author Sharp
 * @since 2026/09/08
 */
@Target({ElementType.FIELD})
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface BindDict {

    /**
     * 字典类型编码
     *
     * @return 字典类型
     */
    String type();

    /**
     * 源字段名称（取值字段）；为空时取被注解字段自身
     *
     * @return 源字段名称
     */
    String field() default "";
}
