package com.bidr.feishu.notify;

import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Conditional;
import com.bidr.notification.NotifyChannel;
import com.bidr.notification.NotifyMessage;
import com.bidr.kernel.constant.err.ErrCodeLevel;
import com.bidr.kernel.utils.FuncUtil;
import com.bidr.platform.service.cache.SysConfigCacheService;
import com.bidr.feishu.constant.param.FeishuParam;
import com.bidr.feishu.service.message.FeishuSendMessageService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Title: FeishuNotifyChannel
 * Description: 飞书通知渠道（T1.1 棒5）——core SPI {@link NotifyChannel} 在飞书身份源形态的实现，
 * 原 ExceptionAlertFeishuListener/ServiceExceptionFeishuListener/ExceptionAlertFeishuBuilder 三件
 * 收口为本渠道实现：监听半边上移内核 {@code com.bidr.kernel.event.NotifyChannelDispatcher}
 * （事件→NotifyMessage 纯数据→遍历 enabled 渠道），本类只管渠道侧闸门与投递。
 *
 * <p>职责面（与原两监听器逐项对账）：</p>
 * <ul>
 *   <li>{@link #enabled()}：EXCEPTION_NOTIFY_FEISHU_ENABLED 开关（原两监听器 isNotifyEnabled 同口径，
 *       "true"/"1" 为开；内核对 disabled 渠道跳过投递且不报错）；</li>
 *   <li>{@link #send(NotifyMessage)}：①最低级别闸——EXCEPTION_NOTIFY_FEISHU_MIN_LEVEL（原
 *       ServiceExceptionFeishuListener 的 shouldNotify 同口径，默认 ERROR，数值越大越严重）；
 *       ②接收人解析——EXCEPTION_NOTIFY_FEISHU（open_id 逗号/中文逗号分隔；空＝渠道广播语义自定为
 *       log.warn 跳过不发送，即 core SPI javadoc 点名的参考实现语义）；③卡片投递——标题取
 *       EXCEPTION_NOTIFY_FEISHU_SUBJECT（原两监听器同源），正文用 message.content（内核已按通用
 *       格式组装），经 {@link FeishuSendMessageService#sendCardMessage} 发送。</li>
 * </ul>
 *
 * <p>与原链的<b>已知行为差异</b>（登记于棒5 报告）：①最低级别闸原只挂在 ServiceExceptionEvent
 * 腿，现对经派发器的所有消息统一生效——@ExceptionAlert 注解显式给低级别（如 WARN）的告警在
 * 开关＝ERROR 档时不再发飞书（默认注解级别 ERROR 不受影响）；②堆栈深度键
 * EXCEPTION_NOTIFY_FEISHU_STACK_DEPTH 随内容组装上移内核后不再被本渠道读取（内核通用截断 50 行
 * ＝原缺省值），sys_config 行保留不再消费。</p>
 * Copyright: Copyright (c) 2026 Company: Bidr Ltd.
 *
 * @author Sharp
 * @since 2026-11-01
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "feishu", name = "app-id")
@ConditionalOnExpression("'${feishu.direct:false}' != 'true'")
public class FeishuNotifyChannel implements NotifyChannel {

    /** 渠道标识（core SPI 契约：内核用于渠道注册与路由去重） */
    private static final String CHANNEL_ID = "feishu";

    private final SysConfigCacheService sysConfigCacheService;
    private final FeishuSendMessageService feishuSendMessageService;

    @Override
    public String channelId() {
        return CHANNEL_ID;
    }

    /**
     * 渠道开关：EXCEPTION_NOTIFY_FEISHU_ENABLED（sys_config），"true"/"1" 为开——
     * 原两监听器 isNotifyEnabled 同口径；未开启时内核跳过本渠道（不报错、不投递）
     */
    @Override
    public boolean enabled() {
        String enabled = sysConfigCacheService.getSysConfigValue(FeishuParam.EXCEPTION_NOTIFY_FEISHU_ENABLED);
        return "true".equalsIgnoreCase(enabled) || "1".equals(enabled);
    }

    /**
     * 投递一条通知（卡片形态）：最低级别闸→接收人解析→sendCardMessage。
     * 发送失败抛运行时异常由内核记录（core SPI 契约：渠道内不做重试、不吞异常）。
     */
    @Override
    public void send(NotifyMessage message) {
        if (!isLevelReachingFloor(message)) {
            log.info("飞书告警级别 {} 未达配置最低级别，跳过发送",
                    message == null || message.level == null ? "-" : message.level.name());
            return;
        }
        List<String> openIds = getNotifyOpenIds();
        if (FuncUtil.isEmpty(openIds)) {
            // core SPI 契约参考语义：接收人未配置＝渠道广播语义自定，log.warn 跳过不发送、不广播
            log.warn("异常告警飞书接收人未配置，跳过发送飞书告警");
            return;
        }
        String subject = sysConfigCacheService.getSysConfigValue(FeishuParam.EXCEPTION_NOTIFY_FEISHU_SUBJECT);
        if (FuncUtil.isEmpty(subject)) {
            subject = message.title;
        }
        feishuSendMessageService.sendCardMessage(openIds, subject, message.content);
        log.info("飞书告警已发送至: {}", openIds);
    }

    /**
     * 最低级别闸：EXCEPTION_NOTIFY_FEISHU_MIN_LEVEL（默认 ERROR，数值越大越严重），
     * 与消息级别的比较沿用原 shouldNotify/parseIntLevel 口径（HIDE 等无数值档视为 -1）
     */
    private boolean isLevelReachingFloor(NotifyMessage message) {
        if (message == null || message.level == null) {
            // 级别缺席（非本内核派发器产出的通用消息）：不设闸直通，投递语义由开关与接收人闸兜底
            return true;
        }
        ErrCodeLevel minLevel = getMinNotifyLevel();
        return parseIntLevel(toErrLevel(message.level)) >= parseIntLevel(minLevel);
    }

    /**
     * 将 ErrCodeLevel 转换为可比较的整数（原 parseIntLevel 平移）：数值档直读；
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
     * 获取配置的最低告警级别（原 getMinNotifyLevel 平移：无效配置回退 ERROR 并告警）
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
     * SPI 三值级别 → 内核 ErrCodeLevel（同名直映；级别数值面只在 ERROR/WARN/INFO 三档内比较）
     */
    private ErrCodeLevel toErrLevel(NotifyMessage.Level level) {
        switch (level) {
            case WARN:
                return ErrCodeLevel.WARN;
            case INFO:
                return ErrCodeLevel.INFO;
            case ERROR:
            default:
                return ErrCodeLevel.ERROR;
        }
    }

    /**
     * 获取通知接收人（open_id 列表，EXCEPTION_NOTIFY_FEISHU，原 getNotifyOpenIds 平移）
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
}
