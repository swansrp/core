package com.bidr.mcp.anno;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Title: Param
 * Description: MCP 工具参数声明。名字解析单一来源：{@link #name()} 非空取 name，
 * 否则取 {@link #value()}，再否则反射参数名——绑定与 inputSchema 两条路读的是同一个
 * 解析结果，旧 Solon @Param 的 value/name 别名两处分裂坑（schema 宣称 logDate、绑定
 * 只认 log_date）在本实现里结构上不可能复现，业务无需再两处写死。
 *
 * @author sharp
 * @since 2026/11/14
 */
@Documented
@Target(ElementType.PARAMETER)
@Retention(RetentionPolicy.RUNTIME)
public @interface Param {

    /**
     * 参数名（wire 上的 arguments 键，推荐 snake_case）；name() 优先于 value()
     */
    String value() default "";

    /**
     * 参数名别名，与 value() 同义，兼容旧代码的书写习惯
     */
    String name() default "";

    /**
     * 参数描述（写进 inputSchema.properties）
     */
    String description() default "";

    /**
     * 是否必填；默认必填，可选参数显式标 required = false
     */
    boolean required() default true;
}
