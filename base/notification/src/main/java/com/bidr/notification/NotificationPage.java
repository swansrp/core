package com.bidr.notification;

import com.bidr.notification.dao.entity.NotificationRecord;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * 站内信分页结果（core 自有类型，M21 review 修复）：SPI 与控制器之间的<b>稳定契约</b>。
 *
 * <p>🔴 SPI 不返回 MyBatis-Plus 的 {@code Page}——那是默认实现的持久化细节；宿主覆盖
 * {@code NotificationStore}（用自己的表/自己的查询）时照样能构造本类型，控制器不再强转，
 * 不会 ClassCastException（review P2-6）。</p>
 *
 * @author sharp (M21 review 修复 2026-10-25)
 */
@Data
public class NotificationPage {

    /** 当前页行（pushed_at 降序） */
    private List<NotificationRecord> records = new ArrayList<>();

    /** 总行数（含未取回的页） */
    private long total;

    /** 当前页码（1 起） */
    private long current;

    /** 页大小 */
    private long size;
}
