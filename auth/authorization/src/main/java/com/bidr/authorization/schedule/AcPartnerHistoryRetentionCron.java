package com.bidr.authorization.schedule;

import com.bidr.authorization.constants.param.AccountParam;
import com.bidr.authorization.dao.repository.AcPartnerHistoryService;
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
 * Title: AcPartnerHistoryRetentionCron
 * Description: 对接接口访问留痕保留期清理（T3.3a）。
 * <p>
 * 03:45 起跑，排在 03:15（日志）与 03:30（发件箱）之后：三条清理任务都在夜里删表，
 * 错开只是为了不互相抢锁。
 * </p>
 * <p>
 * 两个早退都是必需的：
 * <ol>
 *   <li>表不存在直接跳过——没启用对接的宿主没有这张表，清理任务不该成为它的启动报错；</li>
 *   <li>天数 ≤ 0 直接跳过——误配 0 或负数会把边界推到"现在"，一夜清空全部留痕，
 *       回头等保审计要查的正是这些行。</li>
 * </ol>
 * 删到东西才落日志：留痕清理本身不产生"事件"，没有删除量的夜里不该刷日志。
 * </p>
 *
 * @author Sharp
 * @since 2026/11/03
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AcPartnerHistoryRetentionCron {

    private static final String PARTNER_HISTORY_TABLE_NAME = "ac_partner_history";

    private final SysConfigCacheService sysConfigCacheService;
    private final AcPartnerHistoryService acPartnerHistoryService;
    private final TablesService tablesService;
    @Value("${my.master-db.config.db}")
    private String dbName;

    @Scheduled(cron = "0 45 3 * * ?")
    public void purgeExpiredHistory() {
        if (!tablesService.existed(dbName, PARTNER_HISTORY_TABLE_NAME)) {
            return;
        }
        int days = sysConfigCacheService.getParamInt(AccountParam.AC_PARTNER_HISTORY_RETENTION_DAYS);
        if (days <= 0) {
            return;
        }
        Date expired = DateUtil.beginTime(DateUtil.addDate(new Date(), -days));
        int deleted = acPartnerHistoryService.purgeBefore(expired);
        if (deleted > 0) {
            log.info("ac_partner_history 保留期清理：删除 {} 行（request_at 早于 {}，保留 {} 天）", deleted, expired, days);
        }
    }
}