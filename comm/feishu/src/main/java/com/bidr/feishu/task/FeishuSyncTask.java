package com.bidr.feishu.task;

import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Conditional;
import com.bidr.feishu.dto.sync.FeishuSyncResult;
import com.bidr.feishu.service.contact.FeishuSyncService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

/**
 * Title: FeishuSyncTask
 * Description: 飞书组织定时同步任务：部门/人员变动自动同步至基础库，
 * 并经同步完成事件联动网盘预创建部门文件夹。cron 由环境变量 FEISHU_SYNC_CRON
 * （属性 feishu.sync.cron）覆盖，默认每天 02:00 执行
 * Copyright: Copyright (c) 2026 Company: Bidr Ltd.
 *
 * @author sharp
 * @since 2026/09/05 15:00
 */
@Slf4j
@ConditionalOnProperty(prefix = "feishu", name = "app-id")
@ConditionalOnExpression("'${feishu.direct:false}' == 'true'")
@Service
@RequiredArgsConstructor
public class FeishuSyncTask {

    private final FeishuSyncService feishuSyncService;

    /**
     * 定时全量同步（失败仅告警，等待下个周期）
     */
    @Scheduled(cron = "${feishu.sync.cron:0 0 2 * * ?}")
    public void sync() {
        try {
            FeishuSyncResult result = feishuSyncService.syncAll();
            log.info("定时同步飞书组织完成，部门 {} 个，用户 {} 个", result.getDeptCount(), result.getAccountCount());
        } catch (Exception e) {
            log.error("定时同步飞书组织失败", e);
        }
    }
}
