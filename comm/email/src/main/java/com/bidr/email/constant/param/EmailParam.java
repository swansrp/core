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
@MetaParam("邮件")
public enum EmailParam implements Param {
    /**
     * 异常通知开关
     */
    EXCEPTION_NOTIFY_ENABLED("异常通知开关", "1", "【按需调整】1＝达到下面级别的异常自动发邮件告警（须已配好 SMTP）；不想被邮件轰炸先置 0，比清空收件人可靠"),

    EXCEPTION_NOTIFY_EMAIL("异常通知邮箱", "56093273@qq.com", "【现场必配·启用邮件时】收件人，多个用英文逗号分隔；⚠️ 出厂值是开发者个人邮箱，部署时务必改成团队告警邮箱"),

    EXCEPTION_NOTIFY_EMAIL_SUBJECT("异常通知邮件标题", "【系统异常通知】", "【按需调整】邮件标题前缀，便于收件箱过滤"),

    EXCEPTION_NOTIFY_MIN_LEVEL("异常通知最低级别", "ERROR", "【按需调整】达到或超过该级别才发（FATAL/ERROR/WARN/INFO/DEBUG/TRACE）；告警太吵调高、联调期调低"),

    EXCEPTION_NOTIFY_STACK_DEPTH("异常通知堆栈深度", "50", "【出厂默认即可】邮件内堆栈最多保留行数，-1＝不限制；只影响邮件可读性，不影响日志");

    private final String title;
    private final String defaultValue;
    private final String remark;
}