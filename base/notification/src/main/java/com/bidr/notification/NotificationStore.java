package com.bidr.notification;

import com.bidr.notification.dao.entity.NotificationRecord;

import java.util.List;

/**
 * 站内消息存取 SPI（core 定义、宿主可覆盖——覆盖者保留 receiver 判权与幂等语义）。
 *
 * <p><b>契约（宿主实现必须守住）</b>：①{@link #markRead} 强制 {@code receiver=登录人}
 * （越权传他人消息 id＝0 行生效）；②读面（{@link #page}/{@link #unreadCount}）只按 receiver
 * （＋实现自定义的租户归属条件）取数，绝不接受调用方传 receiver；③写腿独立事务
 * （{@code REQUIRES_NEW} 语义）——失败提醒不能随业务事务回滚被吞。</p>
 *
 * <p>core 默认实现＝{@code SaNotificationStore}（表 sa_notification，含去重）；不能迁表的
 * 宿主实现本接口包住自己的既有表。</p>
 *
 * @author sharp (M21：站内信机制 2026-10-25)
 */
public interface NotificationStore {

    /**
     * 落行（已按收件人扇开；实现负责去重语义：同 target_user＋dedupe_key＋未读＝只更新不新增，
     * dedupe_key 空＝直接插入；已读后再次命中＝新行）。
     */
    void save(List<NotificationRecord> rows);

    /**
     * 收件箱分页（pushed_at 降序、id 降序兜稳定序）。返回 core 自有的
     * {@link NotificationPage}——🔴 不返回持久化框架的 Page 类型（宿主覆盖实现时不会 ClassCastException）。
     *
     * @param receiver 收件人（服务端取登录人，不接受参数指定）
     * @param current  页码（1 起）
     * @param size     页大小
     * @param readFlag 已读过滤（null/空＝全部，"0"＝未读，"1"＝已读）
     */
    NotificationPage page(String receiver, long current, long size, String readFlag);

    /** 未读数（铃铛徽标数据源） */
    long unreadCount(String receiver);

    /**
     * 标记已读（幂等：已读行重复调用＝0 行生效）。
     *
     * @return 是否有行被更新
     */
    boolean markRead(List<Long> ids, String receiver);

    /** 全部已读（收件人维度） */
    boolean markAllRead(String receiver);
}
