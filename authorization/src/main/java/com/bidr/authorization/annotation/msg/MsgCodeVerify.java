package com.bidr.authorization.annotation.msg;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;


@Target({ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
public @interface MsgCodeVerify {
    /**
     * 验证码类型
     */
    String value();

}
