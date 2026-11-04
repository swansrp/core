package com.bidr.notification;
import com.bidr.kernel.event.ExceptionAlertEvent;
import com.bidr.kernel.event.ServiceExceptionEvent;

import com.bidr.notification.NotifyChannel;
import com.bidr.notification.NotifyMessage;
import com.bidr.kernel.constant.err.ErrCodeLevel;
import com.bidr.kernel.utils.FuncUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Title: NotifyChannelDispatcher
 * Description: 通知渠道派发器（T1.1 棒5，ADR-0001"内核 SPI 派发"半边）——监听内核两类告警事件
 * （{@link ExceptionAlertEvent}＝@ExceptionAlert 注解链、{@link com.bidr.kernel.event.ServiceExceptionEvent}＝全局异常
 * 处理器链），映为 pack-spi {@link NotifyMessage} 纯数据后遍历投递给全部启用渠道
 * （{@code ObjectProvider<NotifyChannel>} 逐个 {@code enabled()} 过滤后 {@code send}）。
 * 内核零具体渠道代码：本类只认 pack-spi {@link NotifyChannel} 接口，飞书/企微/邮件等具体渠道
 * 全部住各自 pack/模块实现（原 plsintec 侧两监听器随本派发器退役为 NotifyChannel 实现）。
 *
 * <p>职责面（原 plsintec 两监听器监听半边的内核化，逐项对账）：①监听两事件（@Async 保持原监听器
 * 非阻塞语义——事件发布线程不等 HTTP 投递）；②级别映射——内核 {@link ErrCodeLevel} 七档收敛到
 * {@link NotifyMessage.Level} 三档（FATAL→ERROR、DEBUG→INFO、其余同名直映；TRACE/HIDE 无出口，
 * 在源侧通知面直接不产出消息，见 NotifyMessage javadoc 的源侧映射约定）；③消息组装——标题/正文/
 * 级别三要素的<b>渠道无关</b>通用格式（富文本标记行，渠道按自身方言渲染），收件人不在内核填
 * （NotifyMessage.receivers 留空，由渠道按各自配置自解析——pack-spi 契约参考语义）；④投递——
 * 单渠道 send 失败记录日志继续下家（pack-spi 契约：失败抛运行时异常由内核记录，渠道内不重试）。</p>
 *
 * <p>🔴 渠道缺席空安全（bidr 形态无 NotifyChannel bean）：{@code orderedStream()} 空流即静默返回，
 * 不抛不告警（SPI 缺席＝零渠道＝零投递，与 IdentityBridgeLoginService 的 ObjectProvider 空安全同口径）。
 * 堆栈截断：ExceptionAlertEvent 腿用事件自带 {@code getLimitedStackTrace()}（深度随事件）；
 * ServiceExceptionEvent 腿内核通用截断 50 行（原 plsintec 监听器缺省值同源，登记于棒5 报告）。</p>
 * Copyright: Copyright (c) 2026 Company: Bidr Ltd.
 *
 * @author Sharp
 * @since 2026-11-01
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class NotifyChannelDispatcher {

    private static final String DATE_FORMAT = "yyyy-MM-dd HH:mm:ss";

    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern(DATE_FORMAT);

    /** ServiceExceptionEvent 腿堆栈通用截断行数（原 plsintec 监听器 getStackDepth 缺省值同源） */
    private static final int GENERIC_STACK_LINES = 50;

    /** SPI 取用：无渠道 bean＝空流（零渠道零投递，不抛）；多渠道按 @Order 顺序投递 */
    private final ObjectProvider<NotifyChannel> notifyChannels;

    @Value("${app.projectId:}")
    private String projectId;

    @Value("${app.moduleId:}")
    private String moduleId;

    /**
     * 异常告警事件派发（@ExceptionAlert 注解链）：级别映射→通用消息组装→渠道遍历
     */
    @Async
    @EventListener
    public void onExceptionAlert(ExceptionAlertEvent event) {
        NotifyMessage.Level level = mapLevel(event.getSeverity());
        if (level == null) {
            // TRACE/HIDE 无出口：源侧通知面直接不产出消息（NotifyMessage javadoc 源侧映射约定）
            return;
        }
        NotifyMessage message = buildAlertMessage(event, level);
        dispatch(message, event.getSeverity().name());
    }

    /**
     * 服务异常事件派发（全局异常处理器链）：级别映射→通用消息组装→渠道遍历
     */
    @Async
    @EventListener
    public void onServiceException(ServiceExceptionEvent event) {
        NotifyMessage.Level level = mapLevel(event.getErrLevel());
        if (level == null) {
            return;
        }
        NotifyMessage message = buildServiceExceptionMessage(event, level);
        dispatch(message, event.getErrLevel().name());
    }

    /**
     * 遍历投递：enabled() 过滤在前，单渠道失败记录日志继续下家（渠道间故障隔离）
     */
    private void dispatch(NotifyMessage message, String severityName) {
        List<NotifyChannel> channels = notifyChannels.orderedStream().collect(Collectors.toList());
        if (channels.isEmpty()) {
            return;
        }
        for (NotifyChannel channel : channels) {
            if (!channel.enabled()) {
                log.debug("通知渠道 {} 未启用，跳过投递", channel.channelId());
                continue;
            }
            try {
                channel.send(message);
                log.info("通知已派发至渠道 {}，级别: {}", channel.channelId(), severityName);
            } catch (Exception e) {
                log.error("通知渠道 {} 投递失败（继续下家，渠道内不重试）", channel.channelId(), e);
            }
        }
    }

    /**
     * 内核七档 → SPI 三档级别映射（FATAL→ERROR、DEBUG→INFO、其余同名直映）；
     * TRACE/HIDE 无出口回 null（调用方据此不产出消息）
     */
    private NotifyMessage.Level mapLevel(ErrCodeLevel errLevel) {
        if (errLevel == null) {
            return null;
        }
        switch (errLevel) {
            case FATAL:
                return NotifyMessage.Level.ERROR;
            case DEBUG:
                return NotifyMessage.Level.INFO;
            case ERROR:
                return NotifyMessage.Level.ERROR;
            case WARN:
                return NotifyMessage.Level.WARN;
            case INFO:
                return NotifyMessage.Level.INFO;
            case TRACE:
            case HIDE:
            default:
                return null;
        }
    }

    /**
     * 通用告警消息组装（渠道无关富文本行，字段序同原 plsintec ExceptionAlertFeishuBuilder）：
     * 级别/项目/模块/时间/描述/位置/异常类型/异常消息/用户/请求/堆栈。
     * 标题留空由渠道按各自配置补——原飞书侧标题/收件人均来自渠道侧 sys_config。
     */
    private NotifyMessage buildAlertMessage(ExceptionAlertEvent event, NotifyMessage.Level level) {
        StringBuilder sb = new StringBuilder();
        sb.append("**级别**: ").append(event.getSeverity() == null ? "UNKNOWN" : event.getSeverity().name()).append("\n");
        appendProject(sb);
        sb.append("**时间**: ").append(DATE_FORMATTER.format(event.getOccurredTime())).append("\n");
        if (FuncUtil.isNotEmpty(event.getDescription())) {
            sb.append("**描述**: ").append(event.getDescription()).append("\n");
        }
        if (FuncUtil.isNotEmpty(event.getClassName()) || FuncUtil.isNotEmpty(event.getMethodName())) {
            sb.append("**位置**: ").append(FuncUtil.isEmpty(event.getClassName()) ? "" : event.getClassName());
            if (FuncUtil.isNotEmpty(event.getMethodName())) {
                sb.append("#").append(event.getMethodName());
            }
            sb.append("\n");
        }
        if (FuncUtil.isNotEmpty(event.getExceptionType())) {
            sb.append("**异常类型**: ").append(event.getExceptionType()).append("\n");
        }
        if (FuncUtil.isNotEmpty(event.getExceptionMessage())) {
            sb.append("**异常消息**: ").append(event.getExceptionMessage()).append("\n");
        }
        if (event.hasUserInfo()) {
            sb.append("**用户**: ").append(joinNonEmpty("/", event.getName(), event.getUserName(),
                    event.getCustomerNumber())).append("\n");
        }
        if (event.hasRequestInfo()) {
            appendRequestInfo(sb, event.getRequestUrl(), event.getRequestMethod(),
                    event.getClientIp(), event.getQueryString());
        }
        if (event.isIncludeStackTrace() && FuncUtil.isNotEmpty(event.getLimitedStackTrace())) {
            sb.append("**堆栈**:\n```\n").append(event.getLimitedStackTrace()).append("\n```\n");
        }
        return buildMessage(sb.toString(), level);
    }

    /**
     * 通用服务异常消息组装（字段序同原 plsintec 侧组装）：
     * 级别/项目/模块/时间/错误码/错误信息/请求/堆栈（内核通用截断 50 行＝原渠道侧缺省值）
     */
    private NotifyMessage buildServiceExceptionMessage(ServiceExceptionEvent event, NotifyMessage.Level level) {
        StringBuilder sb = new StringBuilder();
        sb.append("**级别**: ").append(event.getErrLevel() == null ? "UNKNOWN" : event.getErrLevel().name()).append("\n");
        appendProject(sb);
        sb.append("**时间**: ").append(DATE_FORMATTER.format(event.getOccurredTime())).append("\n");
        if (event.getErrCode() != null) {
            sb.append("**错误码**: ").append(event.getErrCode().getErrCode());
            sb.append(" (").append(event.getErrCode().name()).append(")\n");
        }
        if (FuncUtil.isNotEmpty(event.getErrorMessage())) {
            sb.append("**错误信息**: ").append(event.getErrorMessage()).append("\n");
        }
        appendRequestInfo(sb, event.getRequestUrl(), event.getRequestMethod(),
                event.getClientIp(), event.getQueryString());
        String stackTrace = limitStackTrace(event.getStackTrace(), GENERIC_STACK_LINES);
        if (FuncUtil.isNotEmpty(stackTrace)) {
            sb.append("**堆栈**:\n```\n").append(stackTrace).append("\n```\n");
        }
        return buildMessage(sb.toString(), level);
    }

    private NotifyMessage buildMessage(String content, NotifyMessage.Level level) {
        NotifyMessage message = new NotifyMessage();
        // 标题留空＝渠道按各自配置补（原飞书侧标题来自渠道 sys_config，非空时渠道优先用自有配置）
        message.title = null;
        message.content = content;
        // receivers 留空＝渠道按各自配置自解析（pack-spi 契约：空列表语义由实现自定，参考实现跳过不广播）
        message.receivers = new ArrayList<>();
        message.level = level;
        return message;
    }

    private void appendProject(StringBuilder sb) {
        if (FuncUtil.isNotEmpty(projectId)) {
            sb.append("**项目**: ").append(projectId).append("\n");
        }
        if (FuncUtil.isNotEmpty(moduleId)) {
            sb.append("**模块**: ").append(moduleId).append("\n");
        }
    }

    /** 请求信息三行（原 requestInfo 同款：请求/参数/IP，逐项非空才出） */
    private void appendRequestInfo(StringBuilder sb, String requestUrl, String requestMethod,
                                   String clientIp, String queryString) {
        if (FuncUtil.isNotEmpty(requestUrl)) {
            sb.append("**请求**: ").append(FuncUtil.isEmpty(requestMethod) ? "" : requestMethod + " ")
                    .append(requestUrl).append("\n");
        }
        if (FuncUtil.isNotEmpty(queryString)) {
            sb.append("**参数**: ").append(queryString).append("\n");
        }
        if (FuncUtil.isNotEmpty(clientIp)) {
            sb.append("**IP**: ").append(clientIp).append("\n");
        }
    }

    private static String joinNonEmpty(String separator, String... parts) {
        StringBuilder out = new StringBuilder();
        for (String part : parts) {
            if (FuncUtil.isEmpty(part)) {
                continue;
            }
            if (out.length() > 0) {
                out.append(separator);
            }
            out.append(part);
        }
        return out.toString();
    }

    /** 按行数截断堆栈（原渠道侧 limitStackTrace 同款口径；<=0 或不足行数时原样返回） */
    private static String limitStackTrace(String stackTrace, int maxLines) {
        if (FuncUtil.isEmpty(stackTrace) || maxLines <= 0) {
            return stackTrace;
        }
        String[] lines = stackTrace.split("\n");
        if (lines.length <= maxLines) {
            return stackTrace;
        }
        StringBuilder limited = new StringBuilder();
        for (int i = 0; i < maxLines; i++) {
            limited.append(lines[i]).append("\n");
        }
        limited.append("... ").append(lines.length - maxLines).append(" more lines omitted");
        return limited.toString();
    }
}
