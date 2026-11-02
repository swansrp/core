package com.bidr.notification;

import java.util.List;

/**
 * Title: NotifyMessage
 * Description: 通知消息——{@link com.bidr.notification.NotifyChannel#send(NotifyMessage)} 的载荷。
 * 语义来自 plsintec FeishuSendMessageService 的文本/卡片两形态（title＋content）与
 * ExceptionAlertFeishuListener.onExceptionAlert(ExceptionAlertEvent) 的告警投递要素
 * （{@code com.bidr.kernel.event.ExceptionAlertEvent} 住 core，此处仅语义引用）。
 *
 * @author Sharp
 * @since 2026-10-31
 */
public class NotifyMessage {

    /** 标题（卡片形态的消息头；纯文本渠道可与 content 合并展示） */
    public String title;

    /** 正文（支持渠道侧自行换行渲染） */
    public String content;

    /** 收件人标识列表（渠道侧账号：openId / accountId 等，由桥自译；空列表表示渠道广播语义由实现自定） */
    public List<String> receivers;

    /** 级别（内核按级别决定展示形态/@提醒策略，渠道可降级处理） */
    public Level level;

    /**
     * 通知级别——INFO（常规通知）/ WARN（需关注）/ ERROR（异常告警）。
     * 源侧映射约定：内核 {@code ErrCodeLevel}（FATAL/ERROR/WARN/INFO/DEBUG）映射到本枚举时
     * FATAL→ERROR、DEBUG→INFO，其余同名直映；另两级无出口——TRACE（纯排障档）与 HIDE（隐藏档）
     * 在源侧通知面就被最低告警级别闸收口，不映射到本枚举，渠道侧不会收到。
     */
    public enum Level {
        INFO, WARN, ERROR
    }
}
