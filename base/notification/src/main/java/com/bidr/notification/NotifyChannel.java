package com.bidr.notification;

import com.bidr.notification.NotifyMessage;

/**
 * Title: NotifyChannel
 * Description: 通知渠道 SPI——内核把告警/通知投递给渠道实现的单点契约。
 * 语义来自 plsintec FeishuSendMessageService.sendTextMessage(List&lt;String&gt; openIds, String text)
 * 与 sendCardMessage(openIds, title, content)（飞书文本/卡片消息），以及
 * ExceptionAlertFeishuListener.onExceptionAlert(ExceptionAlertEvent)——T0.3 将把该监听器
 * 改造为 NotifyChannel 实现（{@code com.bidr.kernel.event.ExceptionAlertEvent} 住 core，
 * 本契约仅 javadoc 引用，不构成代码依赖）。
 *
 * @author Sharp
 * @since 2026-10-31
 */
public interface NotifyChannel {

    /**
     * 渠道标识（如 feishu / email / webhook），内核用它做渠道注册与路由去重。
     */
    String channelId();

    /**
     * 渠道是否启用（如未配置应用凭证时回 false，内核跳过投递且不报错）。
     */
    boolean enabled();

    /**
     * 投递一条通知。message.receivers 是渠道侧收件人标识（openId / accountId 等），
     * 由桥自行翻译为渠道账号体系；receivers 为空列表时语义由实现自定
     * （参考实现 ExceptionAlertFeishuListener 是 log.warn 跳过不发送，不广播）；
     * 发送失败抛运行时异常由内核记录，不做渠道内重试。
     *
     * @param message 通知内容（标题＋正文＋收件人＋级别）
     */
    void send(NotifyMessage message);
}
