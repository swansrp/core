/**
 * Copyright © 2018 SRC-TJ Service TG. All rights reserved.
 *
 * @Package: com.srct.service.annotation
 * @author: xu1223.zhang
 * @since: 2018-08-02 16:44
 */
package com.bidr.authorization.annotation.auth;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
public @interface Auth {
    Class<? extends AuthRole>[] value() default AuthLogin.class;

    String perms() default "";

    String extraData() default "";
}
