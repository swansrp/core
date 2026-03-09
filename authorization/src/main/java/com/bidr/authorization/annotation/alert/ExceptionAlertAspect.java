package com.bidr.authorization.annotation.alert;

import com.bidr.authorization.constants.param.EmailParam;
import com.bidr.email.service.EmailService;
import com.bidr.kernel.constant.err.ErrCodeLevel;
import com.bidr.kernel.utils.FuncUtil;
import com.bidr.platform.service.cache.SysConfigCacheService;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.annotation.AfterThrowing;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Pointcut;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.stereotype.Component;
import org.springframework.core.task.TaskExecutor;

import javax.annotation.Resource;
import java.lang.reflect.Method;
import java.time.LocalDateTime;

/**
 * Title: ExceptionAlertAspect
 * Description: 异常告警切面，捕获被 @ExceptionAlert 注解的方法异常并发送告警通知
 * Copyright: Copyright (c) 2022 Company: Sharp Ltd.
 *
 * @author Sharp
 * @since 2023/03/31 09:24
 */
@Slf4j
@Aspect
@Component
public class ExceptionAlertAspect {

    @Resource
    private SysConfigCacheService sysConfigCacheService;

    @Resource
    private EmailService emailService;

    @Resource
    private TaskExecutor taskExecutor;

    @Pointcut("@annotation(com.bidr.authorization.annotation.alert.ExceptionAlert)")
    public void exceptionAlert() {
    }

    @AfterThrowing(pointcut = "exceptionAlert() && @annotation(alert)", throwing = "e")
    public void afterThrowing(JoinPoint joinPoint, ExceptionAlert alert, Throwable e) {
        try {
            // 发送邮件告警
            sendEmailAlert(joinPoint, alert, e);

            // TODO: 根据严重级别扩展其他告警方式
            // if (alert.severity() == ErrCodeLevel.FATAL) {
            //     sendSmsAlert(...);
            //     sendDingTalkAlert(...);
            // }

            log.info("异常告警已发送，严重级别: {}", alert.severity().name());
        } catch (Exception ex) {
            log.error("发送异常告警失败", ex);
        }
    }

    /**
     * 发送邮件告警
     */
    private void sendEmailAlert(JoinPoint joinPoint, ExceptionAlert alert, Throwable e) {
        String notifyEmails = getNotifyEmails(alert);
        if (FuncUtil.isEmpty(notifyEmails)) {
            log.warn("异常告警邮箱未配置，跳过发送邮件告警");
            return;
        }

        String subject = buildEmailSubject(alert);
        String content = buildEmailContent(joinPoint, alert, e);

        if (alert.async()) {
            sendEmailAsync(notifyEmails, subject, content);
        } else {
            emailService.sendHtmlEmail(notifyEmails, subject, content);
        }
        log.info("邮件告警已发送至: {}", notifyEmails);
    }

    /**
     * 获取通知邮箱列表
     * 优先使用注解中指定的邮箱，否则使用系统默认配置
     */
    private String getNotifyEmails(ExceptionAlert alert) {
        String emails = alert.notifyEmails();
        if (FuncUtil.isNotEmpty(emails)) {
            return emails;
        }
        return sysConfigCacheService.getSysConfigValue(EmailParam.EXCEPTION_NOTIFY_EMAIL);
    }

    /**
     * 构建邮件主题
     */
    private String buildEmailSubject(ExceptionAlert alert) {
        String baseSubject = sysConfigCacheService.getSysConfigValue(EmailParam.EXCEPTION_NOTIFY_EMAIL_SUBJECT);
        String severityPrefix = "[" + alert.severity().name() + "]";
        return severityPrefix + baseSubject;
    }

    /**
     * 异步发送邮件
     */
    public void sendEmailAsync(String to, String subject, String content) {
        taskExecutor.execute(() -> {
            try {
                emailService.sendHtmlEmail(to, subject, content);
            } catch (Exception e) {
                log.error("异步发送邮件告警失败", e);
            }
        });
    }

    /**
     * 构建邮件内容
     */
    private String buildEmailContent(JoinPoint joinPoint, ExceptionAlert alert, Throwable e) {
        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        Method method = signature.getMethod();
        String className = joinPoint.getTarget().getClass().getName();
        String methodName = method.getName();

        ExceptionAlertEmailBuilder builder = ExceptionAlertEmailBuilder.create();

        builder.appendHeader()
                .severity(alert.severity())
                .appendTitle()
                .appendOccurredTime(LocalDateTime.now())
                .appendDescription(alert.value())
                .endSection();

        if (alert.includeUserInfo()) {
            builder.appendUserInfo();
        }

        if (alert.includeRequestInfo()) {
            builder.appendRequestInfo();
        }

        builder.appendMethodInfo(className, methodName, alert.includeArgs() ? joinPoint.getArgs() : null);

        builder.appendExceptionInfo(e.getClass().getName(), e.getMessage());

        if (alert.includeStackTrace()) {
            builder.appendStackTrace(e, alert.stackTraceDepth());
        }

        builder.appendFooter();

        return builder.build();
    }
}
