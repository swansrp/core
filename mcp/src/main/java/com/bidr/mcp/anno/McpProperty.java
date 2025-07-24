package com.bidr.mcp.anno;

import java.lang.annotation.*;

/**
 * @author Sharp
 */
@Target({ElementType.FIELD})
@Retention(RetentionPolicy.RUNTIME)
@Inherited
@Documented
public @interface McpProperty {
    String name() default "";

    boolean ignoreEmpty() default false;

    boolean ignore() default false;

}
