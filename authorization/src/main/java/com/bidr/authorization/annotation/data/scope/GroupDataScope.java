package com.bidr.authorization.annotation.data.scope;

import com.bidr.authorization.constants.group.Group;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Title: GroupDataScope
 * Description: Copyright: Copyright (c) 2023
 *
 * @author Sharp
 * @since 2023/06/14 15:25
 */
@Target({ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
public @interface GroupDataScope {
    String value();
}
