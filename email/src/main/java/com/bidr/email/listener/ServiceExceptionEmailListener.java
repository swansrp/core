package com.bidr.email.listener;

import com.bidr.email.constant.param.EmailParam;
import com.bidr.email.service.EmailService;
import com.bidr.kernel.constant.err.ErrCodeLevel;
import com.bidr.kernel.event.ServiceExceptionEvent;
import com.bidr.kernel.utils.FuncUtil;
import com.bidr.platform.service.cache.SysConfigCacheService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;

/**
 * Title: ServiceExceptionEmailListener
 * Description: 服务异常邮件监听器，监听全局 ServiceExceptionEvent（来自 ResponseExceptionHandler）并发送邮件告警
 * Copyright: Copyright (c) 2022 Company: Sharp Ltd.
 *
 * @author Sharp
 * @since 2023/03/31 09:24
 */
@Slf4j
@Component
public class ServiceExceptionEmailListener {

    @Resource
    private SysConfigCacheService sysConfigCacheService;

    @Resource
    private EmailService emailService;

    /**
     * 监听服务异常事件，发送告警邮件
     * 根据配置的最低级别判断是否发送告警
     */
    @Async
    @EventListener
    public void onServiceException(ServiceExceptionEvent event) {
        // 检查是否开启异常通知
        if (!isNotifyEnabled()) {
            return;
        }

        // 检查是否达到配置的最低告警级别
        if (!shouldNotify(event)) {
            return;
        }

        try {
            sendEmailAlert(event);
            log.info("异常告警邮件已发送，错误码: {}, 级别: {}", event.getErrCode().getErrCode(), event.getErrLevel().name());
        } catch (Exception e) {
            log.error("发送异常告警邮件失败", e);
        }
    }

    /**
     * 检查是否开启异常通知
     */
    private boolean isNotifyEnabled() {
        String enabled = sysConfigCacheService.getSysConfigValue(EmailParam.EXCEPTION_NOTIFY_ENABLED);
        return FuncUtil.isEmpty(enabled) || "true".equalsIgnoreCase(enabled) || "1".equals(enabled);
    }

    /**
     * 获取堆栈深度配置
     */
    private int getStackDepth() {
        String depthStr = sysConfigCacheService.getSysConfigValue(EmailParam.EXCEPTION_NOTIFY_STACK_DEPTH);
        if (FuncUtil.isEmpty(depthStr)) {
            return 50;
        }
        try {
            return Integer.parseInt(depthStr);
        } catch (NumberFormatException e) {
            return 50;
        }
    }

    /**
     * 判断是否应该发送告警
     */
    private boolean shouldNotify(ServiceExceptionEvent event) {
        ErrCodeLevel minLevel = getMinNotifyLevel();
        ErrCodeLevel eventLevel = event.getErrLevel();

        // 级别数值越大越严重，需要事件级别 >= 配置的最低级别
        return compareLevel(eventLevel, minLevel) >= 0;
    }

    /**
     * 比较错误级别
     *
     * @return 正数表示 level1 更严重，负数表示 level2 更严重，0 表示相同
     */
    private int compareLevel(ErrCodeLevel level1, ErrCodeLevel level2) {
        return Integer.compare(Integer.parseInt(level1.getValue()), Integer.parseInt(level2.getValue()));
    }

    /**
     * 获取配置的最低告警级别
     */
    private ErrCodeLevel getMinNotifyLevel() {
        String levelStr = sysConfigCacheService.getSysConfigValue(EmailParam.EXCEPTION_NOTIFY_MIN_LEVEL);
        if (FuncUtil.isEmpty(levelStr)) {
            return ErrCodeLevel.ERROR;
        }
        try {
            return ErrCodeLevel.valueOf(levelStr.toUpperCase());
        } catch (Exception e) {
            log.warn("无效的异常通知最低级别配置: {}, 使用默认值 ERROR", levelStr);
            return ErrCodeLevel.ERROR;
        }
    }

    /**
     * 发送邮件告警
     */
    private void sendEmailAlert(ServiceExceptionEvent event) {
        String notifyEmails = getNotifyEmails();
        if (FuncUtil.isEmpty(notifyEmails)) {
            log.warn("异常告警邮箱未配置，跳过发送邮件告警");
            return;
        }

        String subject = buildEmailSubject(event);
        String content = buildEmailContent(event);

        emailService.sendHtmlEmail(notifyEmails, subject, content);
        log.info("邮件告警已发送至: {}", notifyEmails);
    }

    /**
     * 获取通知邮箱列表
     */
    private String getNotifyEmails() {
        return sysConfigCacheService.getSysConfigValue(EmailParam.EXCEPTION_NOTIFY_EMAIL);
    }

    /**
     * 构建邮件主题
     */
    private String buildEmailSubject(ServiceExceptionEvent event) {
        String baseSubject = sysConfigCacheService.getSysConfigValue(EmailParam.EXCEPTION_NOTIFY_EMAIL_SUBJECT);
        String severityPrefix = "[" + event.getErrLevel().name() + "]";
        return severityPrefix + baseSubject;
    }

    /**
     * 构建邮件内容
     */
    private String buildEmailContent(ServiceExceptionEvent event) {
        ExceptionAlertEmailBuilder builder = ExceptionAlertEmailBuilder.create();

        builder.appendHeader()
                .severity(event.getErrLevel())
                .appendTitle()
                .appendOccurredTime(event.getOccurredTime())
                .appendErrCode(event.getErrCode().getErrCode(), event.getErrCode().name(), event.getErrorMessage())
                .endSection();

        builder.appendRequestInfo(event.getRequestUrl(), event.getRequestMethod(), event.getClientIp(), event.getQueryString());
        builder.appendStackTrace(event.getStackTrace(), getStackDepth());
        builder.appendFooter();

        return builder.build();
    }
}
