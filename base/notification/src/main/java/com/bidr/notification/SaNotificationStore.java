package com.bidr.notification;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.bidr.authorization.holder.TenantContext;
import com.bidr.notification.dao.entity.NotificationRecord;
import com.bidr.notification.dao.repository.SaNotificationRepoService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.Date;
import java.util.List;

/**
 * 默认存取实现（表 {@code sa_notification}；宿主实现 {@link NotificationStore} 即覆盖，
 * 本 bean 自动让位）。
 *
 * <p><b>租户过滤（恒定条件，不靠拦截器）</b>：读面恒为
 * {@code tenant_id IS NULL OR tenant_id IN (TenantContext.getTenantIds())}——🔴 空集合
 * <b>不跳过条件</b>，自然退化成只看 NULL（无租户用户只见全局消息；被移出租户的人不回看旧租户
 * 私有消息）。绝不能写裸 {@code tenant_id = 当前}（无租户消息会集体消失）。</p>
 *
 * <p><b>独立事务</b>：save 走 {@code REQUIRES_NEW}——上传等 @Transactional 业务里 catch→publish→
 * rethrow 的失败提醒不能随业务回滚被吞（否则用户永远收不到"上传失败"）。</p>
 *
 * @author sharp (M21：站内信机制 2026-10-25)
 */
@Slf4j
@RequiredArgsConstructor
public class SaNotificationStore implements NotificationStore {

    private static final String READ_NO = "0";
    private static final String READ_YES = "1";

    private final SaNotificationRepoService repo;

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void save(List<NotificationRecord> rows) {
        if (rows == null) {
            return;
        }
        for (NotificationRecord row : rows) {
            if (row == null || NotificationText.blank(row.getTargetUser())) {
                continue;
            }
            NotificationRecord existing = findUnreadByDedupe(row.getTargetUser(), row.getDedupeKey());
            if (existing != null) {
                row.setId(existing.getId());
                row.setReadFlag(READ_NO);
                row.setReadAt(null);
                row.setPushedAt(new Date());
                repo.updateById(row);
            } else {
                row.setReadFlag(READ_NO);
                if (row.getPushedAt() == null) {
                    row.setPushedAt(new Date());
                }
                row.setValid("1");
                repo.insert(row);
            }
        }
    }

    @Override
    public Object page(String receiver, long current, long size, String readFlag) {
        QueryWrapper<NotificationRecord> wrapper = new QueryWrapper<NotificationRecord>()
                .eq("target_user", receiver)
                .eq("valid", "1");
        applyTenantScope(wrapper);
        if (readFlag != null && !readFlag.trim().isEmpty()) {
            wrapper.eq("read_flag", readFlag.trim());
        }
        wrapper.orderByDesc("pushed_at").orderByDesc("id");
        return repo.select(wrapper, current, Math.max(1, Math.min(size, 100)));
    }

    @Override
    public long unreadCount(String receiver) {
        QueryWrapper<NotificationRecord> wrapper = new QueryWrapper<NotificationRecord>()
                .eq("target_user", receiver)
                .eq("valid", "1")
                .eq("read_flag", READ_NO);
        applyTenantScope(wrapper);
        return repo.count(wrapper);
    }

    @Override
    public boolean markRead(List<Long> ids, String receiver) {
        if (ids == null || ids.isEmpty()) {
            return false;
        }
        // 双闸：receiver=登录人（越权 id 不命中任何行）＋ read_flag='0'（幂等，read_at 不二次刷新）
        NotificationRecord patch = new NotificationRecord();
        patch.setReadFlag(READ_YES);
        patch.setReadAt(new Date());
        return repo.update(patch, new QueryWrapper<NotificationRecord>()
                .in("id", ids)
                .eq("target_user", receiver)
                .eq("read_flag", READ_NO));
    }

    @Override
    public boolean markAllRead(String receiver) {
        NotificationRecord patch = new NotificationRecord();
        patch.setReadFlag(READ_YES);
        patch.setReadAt(new Date());
        return repo.update(patch, new QueryWrapper<NotificationRecord>()
                .eq("target_user", receiver)
                .eq("read_flag", READ_NO)
                .eq("valid", "1"));
    }

    /** 去重命中腿：同收件人＋同键＋未读的第一行（键空＝不去重） */
    private NotificationRecord findUnreadByDedupe(String receiver, String dedupeKey) {
        if (NotificationText.blank(dedupeKey)) {
            return null;
        }
        return repo.getOne(new QueryWrapper<NotificationRecord>()
                .eq("target_user", receiver)
                .eq("dedupe_key", dedupeKey)
                .eq("read_flag", READ_NO)
                .eq("valid", "1")
                .orderByDesc("id")
                .last("limit 1"));
    }

    /** 租户归属过滤：恒定 IS NULL OR IN（空集退化只看 NULL，不跳过条件） */
    static void applyTenantScope(QueryWrapper<NotificationRecord> wrapper) {
        List<Long> tenantIds = TenantContext.getTenantIds();
        if (tenantIds == null || tenantIds.isEmpty()) {
            wrapper.and(w -> w.isNull("tenant_id"));
        } else {
            wrapper.and(w -> w.isNull("tenant_id").or(t -> t.in("tenant_id", tenantIds)));
        }
    }
}
