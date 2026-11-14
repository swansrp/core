package com.bidr.mcp.anno;

import com.bidr.mcp.config.McpResultConverter;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Title: ToolMapping
 * Description: MCP 工具声明（方法级），标在 {@code @McpServerEndpoint} 类的公开方法上。
 * 注解名与语义沿用旧 Solon 注解，业务侧迁移只换 import。
 *
 * @author sharp
 * @since 2026/11/14
 */
@Documented
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface ToolMapping {

    /**
     * 工具名；留空则取方法名
     */
    String name() default "";

    /**
     * 工具描述（写进 sys_mcp.description 与 tools/list schema）
     */
    String description() default "";

    /**
     * 返回值转换器：把方法返回值转成 JSON 文本 content
     */
    Class<? extends McpResultConverter> resultConverter() default McpResultConverter.class;
}
