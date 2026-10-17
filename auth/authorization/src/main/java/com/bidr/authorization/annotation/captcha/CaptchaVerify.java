package com.bidr.authorization.annotation.captcha;

import com.bidr.kernel.utils.StringUtil;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Title: CaptchaVerify
 * Description: Copyright: Copyright (c) 2022 Company: Sharp Ltd.
 *
 * @author Sharp
 * @since 2023/04/27 10:22
 */
@Target({ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
public @interface CaptchaVerify {
    /**
     * 验证码类型
     */
    String value();

    /**
     * 参数字段
     */
    String field() default StringUtil.EMPTY;

}
