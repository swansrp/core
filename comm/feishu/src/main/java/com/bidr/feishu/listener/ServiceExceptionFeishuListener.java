package com.bidr.feishu.listener;

import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Conditional;
import com.bidr.kernel.constant.err.ErrCodeLevel;
import com.bidr.kernel.event.ServiceExceptionEvent;
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
import java.util.List;
import java.util.stream.Collectors;

/**
 * Title: ServiceExceptionFeishuListener
 * Description: 服务异常飞书监听器（对标 ServiceExceptionWxListener），
 * 监听全局 ServiceExceptionEvent（来自 ResponseExceptionHandler）并发送飞书告警
 * Copyright: Copyright (c) 2025 Company: Bidr Ltd.
 *
 * @author Sharp
 * @since 2025/01/01 00:00
 */
@Slf4j
@ConditionalOnProperty(prefix = "feishu", name = "app-id")
@ConditionalOnExpression("'${feishu.direct:false}' == 'true'")
@Component
public class ServiceExceptionFeishuListener {

    @Value("${app.projectId:}")
    private String projectId;

    @Value("${app.moduleId:}")
    private String moduleId;

    @Resource
    private SysConfigCacheService sysConfigCacheService;

    @Resource
    private FeishuSendMessageService feishuSendMessageService;

    /**
     * 监听服务异常事件，发送飞书告警
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
            sendFeishuAlert(event);
            log.info("异常告警飞书已发送，错误码: {}, 级别: {}", event.getErrCode().getErrCode(), event.getErrLevel().name());
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
     * 获取堆栈深度配置
     */
    private int getStackDepth() {
        String depthStr = sysConfigCacheService.getSysConfigValue(FeishuParam.EXCEPTION_NOTIFY_FEISHU_STACK_DEPTH);
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
        return Integer.compare(parseIntLevel(level1), parseIntLevel(level2));
    }

    /**
     * 将 ErrCodeLevel 转换为可比较的整数
     * HIDE 等无数值的级别视为 -1（最低优先级）
     */
    private int parseIntLevel(ErrCodeLevel level) {
        String value = level.getValue();
        if (FuncUtil.isEmpty(value)) {
            return -1;
        }
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    /**
     * 获取配置的最低告警级别
     */
    private ErrCodeLevel getMinNotifyLevel() {
        String levelStr = sysConfigCacheService.getSysConfigValue(FeishuParam.EXCEPTION_NOTIFY_FEISHU_MIN_LEVEL);
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
     * 发送飞书告警
     */
    private void sendFeishuAlert(ServiceExceptionEvent event) {
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
            return java.util.Collections.emptyList();
        }
        return Arrays.stream(openIds.split("[,，]"))
                .map(String::trim)
                .filter(FuncUtil::isNotEmpty)
                .collect(Collectors.toList());
    }

    /**
     * 构建飞书卡片内容
     */
    private String buildFeishuContent(ServiceExceptionEvent event) {
        ExceptionAlertFeishuBuilder builder = ExceptionAlertFeishuBuilder.create();

        builder.severity(event.getErrLevel())
                .project(projectId, moduleId)
                .occurredTime(event.getOccurredTime())
                .errCode(String.valueOf(event.getErrCode().getErrCode()), event.getErrCode().name(), event.getErrorMessage());

        builder.requestInfo(event.getRequestUrl(), event.getRequestMethod(), event.getClientIp(),
                event.getQueryString());
        builder.stackTrace(event.getStackTrace(), getStackDepth());

        return builder.build();
    }
}
