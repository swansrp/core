package com.bidr.platform.config.portal;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Title: PortalEntity
 * Description: Copyright: Copyright (c) 2023
 *
 * @author Sharp
 * @since 2023/11/22 09:08
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
public @interface PortalEntity {
    /**
     * 实体名
     */
    String[] value();


}
