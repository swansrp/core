package com.bidr.mcp.anno;

import java.lang.annotation.*;

/**
 * @author Sharp
 */
@Target({ElementType.FIELD})
@Retention(RetentionPolicy.RUNTIME)
@Inherited
@Documented
public @interface McpIgnore {
}
