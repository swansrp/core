package com.bidr.notification;

/**
 * 消息级别（前端配色与外发渠道分流判据；core 只定档不定值域，msg_type/action_type 由宿主自定义）。
 *
 * @author sharp (M21：站内信机制 2026-10-25)
 */
public enum NotificationLevel {

    /** 信息（结果通知类，如申请通过） */
    INFO,
    /** 警告（可用性受损但可自愈，如摘要失败） */
    WARN,
    /** 错误（需用户处理的失败，如解析死透） */
    ERROR
}
