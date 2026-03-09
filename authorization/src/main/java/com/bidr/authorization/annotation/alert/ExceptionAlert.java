package com.bidr.authorization.annotation.alert;

import com.bidr.kernel.constant.err.ErrCodeLevel;

import java.lang.annotation.*;

/**
 * Title: ExceptionAlert
 * Description: 异常告警注解，被注解的方法发生异常时会发送告警通知（邮件、短信等）
 * Copyright: Copyright (c) 2022 Company: Sharp Ltd.
 *
 * @author Sharp
 * @since 2023/03/31 09:24
 */
@Target({ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
@Inherited
@Documented
public @interface ExceptionAlert {
    /**
     * 异常描述，用于告警内容中标识异常来源
     */
    String value() default "";

    /**
     * 是否包含方法参数信息
     */
    boolean includeArgs() default true;

    /**
     * 是否包含堆栈信息
     */
    boolean includeStackTrace() default true;

    /**
     * 是否包含用户信息（当前登录用户的ID、用户名等）
     */
    boolean includeUserInfo() default true;

    /**
     * 是否包含请求信息（请求URL、请求方法、客户端IP等）
     */
    boolean includeRequestInfo() default true;

    /**
     * 指定通知邮箱列表，多个邮箱用逗号分隔
     * 为空时使用系统默认配置的异常通知邮箱
     */
    String notifyEmails() default "";

    /**
     * 异常严重级别
     */
    ErrCodeLevel severity() default ErrCodeLevel.ERROR;

    /**
     * 是否异步发送告警
     * 建议在生产环境开启，避免告警发送阻塞业务线程
     */
    boolean async() default true;

    /**
     * 堆栈信息最大深度，默认50行
     * 设置为-1表示不限制
     */
    int stackTraceDepth() default 50;
}
