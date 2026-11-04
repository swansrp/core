package com.bidr.platform.schedule;

import com.bidr.kernel.constant.param.ParamSync;
import com.bidr.kernel.mybatis.dao.repository.SyncOutboxService;
import com.bidr.kernel.mybatis.dao.repository.TablesService;
import com.bidr.kernel.utils.DateUtil;
import com.bidr.platform.service.cache.SysConfigCacheService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.Date;

/**
 * Title: SyncOutboxRetentionCron
 * Description: 变更事件发件箱保留期清理（T3.3a）。
 * <p>
 * 错开到 03:30（{@code DbLogbackCron} 是 03:15）：两条都在夜里删表，挤在同一分钟只是自找锁等待。
 * </p>
 * <p>
 * 两个早退都是必需的：
 * <ol>
 *   <li>表不存在直接跳过——新宿主还没建表、老库没升级上来，清理任务不该成为启动后的第一个报错；</li>
 *   <li>天数 ≤ 0 直接跳过——误配 0 或负数，{@code addDate(-0)} 就把边界推到"现在"，
 *       等于一夜清空整个发件箱，对端从此永久丢增量。</li>
 * </ol>
 * 删到东西才落日志：每夜的"删了 0 行"没有信息量。
 * </p>
 *
 * @author Sharp
 * @since 2026/11/03
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SyncOutboxRetentionCron {

    private static final String OUTBOX_TABLE_NAME = "sync_outbox";

    private final SysConfigCacheService sysConfigCacheService;
    private final SyncOutboxService syncOutboxService;
    private final TablesService tablesService;
    @Value("${my.master-db.config.db}")
    private String dbName;

    @Scheduled(cron = "0 30 3 * * ?")
    public void purgeExpiredEvents() {
        if (!tablesService.existed(dbName, OUTBOX_TABLE_NAME)) {
            return;
        }
        int days = sysConfigCacheService.getParamInt(ParamSync.OUTBOX_RETENTION_DAYS);
        if (days <= 0) {
            return;
        }
        Date expired = DateUtil.beginTime(DateUtil.addDate(new Date(), -days));
        int deleted = syncOutboxService.purgeBefore(expired);
        if (deleted > 0) {
            log.info("sync_outbox 保留期清理：删除 {} 行（occurred_at 早于 {}，保留 {} 天）", deleted, expired, days);
        }
    }
}