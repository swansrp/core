package com.bidr.platform.config.log;

import com.bidr.kernel.mybatis.dao.repository.TablesService;
import com.bidr.kernel.utils.DateUtil;
import com.bidr.platform.constant.param.LogParam;
import com.bidr.platform.dao.repository.SysLogService;
import com.bidr.platform.service.cache.SysConfigCacheService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.Date;

/**
 * Title: DbLogbackCron
 * Description: Copyright: Copyright (c) 2025 Company: Bidr Ltd.
 *
 * @author Sharp
 * @since 2025/3/27 9:53
 */
@Component
@RequiredArgsConstructor
public class DbLogbackCron {

    private static final String LOG_DB_NAME = "sys_log";
    private final SysConfigCacheService sysConfigCacheService;
    private final SysLogService sysLogService;
    private final TablesService tablesService;
    @Value("${my.master-db.config.db}")
    private String dbName;

    @Scheduled(cron = "0 15 3 * * ?")
    public void logInDbExpired() {
        if (tablesService.existed(dbName, LOG_DB_NAME)) {
            int expired = sysConfigCacheService.getParamInt(LogParam.DB_LOG_EXPIRED);
            sysLogService.cleanLog(DateUtil.beginTime(DateUtil.addDate(new Date(), -expired)));
        }
    }
}
