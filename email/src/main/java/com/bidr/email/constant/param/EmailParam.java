package com.bidr.email.constant.param;

import com.bidr.kernel.constant.param.MetaParam;
import com.bidr.kernel.constant.param.Param;
import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * Title: EmailParam
 * Description: Copyright: Copyright (c) 2024 Company: Bidr Ltd.
 *
 * @author Sharp
 * @since 2026/3/9 20:18
 */

@AllArgsConstructor
@Getter
@MetaParam
public enum EmailParam implements Param {
    /**
     * 异常通知邮箱
     */
    EXCEPTION_NOTIFY_EMAIL("异常通知邮箱", "56093273@qq.com", "接收异常通知的邮箱地址，多个邮箱用逗号分隔"),

    /**
     * 异常通知邮件标题
     */
    EXCEPTION_NOTIFY_EMAIL_SUBJECT("异常通知邮件标题", "【系统异常通知】", "异常通知邮件的标题"),

    /**
     * 异常通知开关
     */
    EXCEPTION_NOTIFY_ENABLED("异常通知开关", "1", "是否开启异常通知邮件功能"),

    /**
     * 异常通知堆栈深度
     */
    EXCEPTION_NOTIFY_STACK_DEPTH("异常通知堆栈深度", "50", "异常通知邮件中堆栈信息的最大行数，-1表示不限制"),

    /**
     * 异常通知最低级别
     */
    EXCEPTION_NOTIFY_MIN_LEVEL("异常通知最低级别", "ERROR", "异常通知的最低错误级别，只有达到或超过此级别的异常才会发送邮件通知。可选值：FATAL、ERROR、WARN、INFO、DEBUG、TRACE");

    private final String title;
    private final String defaultValue;
    private final String remark;
}