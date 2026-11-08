package com.bidr.feishu.listener;

import com.bidr.feishu.config.OnFeishuDirectForm;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Conditional;
import com.bidr.kernel.event.ExceptionAlertEvent;
import com.bidr.kernel.utils.FuncUtil;
import com.bidr.platform.service.cache.SysConfigCacheService;
import com.bidr.feishu.constant.param.FeishuParam;
import com.bidr.feishu.service.message.FeishuSendMessageService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Title: ExceptionAlertFeishuListener
 * Description: 异常告警飞书监听器（对标 ExceptionAlertAnnotationWxListener），
 * 监听 @ExceptionAlert 注解触发的 ExceptionAlertEvent 并发送飞书告警
 * Copyright: Copyright (c) 2025 Company: Bidr Ltd.
 *
 * @author Sharp
 * @since 2025/01/01 00:00
 */
@Slf4j
@ConditionalOnProperty(prefix = "feishu", name = "app-id")
@Conditional(OnFeishuDirectForm.class)
@Component
public class ExceptionAlertFeishuListener {

    @Value("${app.projectId:}")
    private String projectId;

    @Value("${app.moduleId:}")
    private String moduleId;

    @Resource
    private SysConfigCacheService sysConfigCacheService;

    @Resource
    private FeishuSendMessageService feishuSendMessageService;

    /**
     * 监听异常告警事件，发送飞书告警
     */
    @Async
    @EventListener
    public void onExceptionAlert(ExceptionAlertEvent event) {
        // 检查是否开启异常通知
        if (!isNotifyEnabled()) {
            return;
        }

        try {
            sendFeishuAlert(event);
            log.info("异常告警飞书已发送，级别: {}, 描述: {}", event.getSeverity().name(), event.getDescription());
        } catch (Exception e) {
            log.error("发送异常告警飞书失败", e);
        }
    }

    /**
     * 检查是否开启异常通知
     */
    private boolean isNotifyEnabled() {
        String enabled = sysConfigCacheService.getSysConfigValue(FeishuParam.EXCEPTION_NOTIFY_FEISHU_ENABLED);
        return "true".equalsIgnoreCase(enabled) || "1".equals(enabled);
    }

    /**
     * 发送飞书告警
     */
    private void sendFeishuAlert(ExceptionAlertEvent event) {
        List<String> openIds = getNotifyOpenIds();
        if (FuncUtil.isEmpty(openIds)) {
            log.warn("异常告警飞书接收人未配置，跳过发送飞书告警");
            return;
        }

        String subject = sysConfigCacheService.getSysConfigValue(FeishuParam.EXCEPTION_NOTIFY_FEISHU_SUBJECT);
        String content = buildFeishuContent(event);

        feishuSendMessageService.sendCardMessage(openIds, subject, content);
        log.info("飞书告警已发送至: {}", openIds);
    }

    /**
     * 获取通知接收人（open_id 列表）
     */
    private List<String> getNotifyOpenIds() {
        String openIds = sysConfigCacheService.getSysConfigValue(FeishuParam.EXCEPTION_NOTIFY_FEISHU);
        if (FuncUtil.isEmpty(openIds)) {
            return Collections.emptyList();
        }
        return Arrays.stream(openIds.split("[,，]"))
                .map(String::trim)
                .filter(FuncUtil::isNotEmpty)
                .collect(Collectors.toList());
    }

    /**
     * 构建飞书卡片内容
     */
    private String buildFeishuContent(ExceptionAlertEvent event) {
        ExceptionAlertFeishuBuilder builder = ExceptionAlertFeishuBuilder.create();

        builder.severity(event.getSeverity())
                .project(projectId, moduleId)
                .occurredTime(event.getOccurredTime())
                .location(event.getDescription(), event.getClassName(), event.getMethodName())
                .exception(event.getExceptionType(), event.getExceptionMessage());

        if (event.hasUserInfo()) {
            builder.userInfo(event.getUserName(), event.getName(), event.getCustomerNumber());
        }
        if (event.hasRequestInfo()) {
            builder.requestInfo(event.getRequestUrl(), event.getRequestMethod(), event.getClientIp(),
                    event.getQueryString());
        }
        if (event.isIncludeStackTrace()) {
            builder.stackTrace(event.getLimitedStackTrace(), event.getStackTraceDepth());
        }

        return builder.build();
    }
}
