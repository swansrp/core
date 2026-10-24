package com.bidr.notification.dao.schema;

import com.bidr.kernel.mybatis.repository.BaseMybatisSchema;
import com.bidr.notification.dao.entity.NotificationRecord;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

/**
 * 站内消息表结构定义（DDL 正源，建表即冻结，变更只走 setUpgradeDDL 追加；M21）。
 *
 * <p>条件装配：宿主覆盖 {@code NotificationStore} 用自己的表时置
 * {@code notification.store.enabled=false}，本 Schema 不建表（防凭空多一张空表）。</p>
 *
 * @author sharp (M21：站内信机制 2026-10-25)
 */
@Service
@ConditionalOnProperty(prefix = "notification.store", name = "enabled",
        havingValue = "true", matchIfMissing = true)
public class SaNotificationSchema extends BaseMybatisSchema<NotificationRecord> {

    static {
        setCreateDDL("CREATE TABLE IF NOT EXISTS `sa_notification`\n" +
                "(\n" +
                "    `id`             bigint(20)   NOT NULL AUTO_INCREMENT COMMENT 'id',\n" +
                "    `msg_type`       varchar(64)  NOT NULL COMMENT '消息类型（宿主自定义字符串）',\n" +
                "    `level`          varchar(16)  NOT NULL DEFAULT 'INFO' COMMENT '级别 INFO/WARN/ERROR',\n" +
                "    `title`          varchar(255) NOT NULL COMMENT '标题',\n" +
                "    `summary`        varchar(300) DEFAULT NULL COMMENT '摘要（人话原因）',\n" +
                "    `content`        varchar(2000) DEFAULT NULL COMMENT '正文（详情态）',\n" +
                "    `action_type`    varchar(64)  DEFAULT NULL COMMENT '点击动作码（空＝不可点）',\n" +
                "    `action_params`  varchar(1000) DEFAULT NULL COMMENT '点击参数 JSON（只放 id/枚举）',\n" +
                "    `biz_type`       varchar(64)  DEFAULT NULL COMMENT '业务对象类型',\n" +
                "    `biz_id`         varchar(128) DEFAULT NULL COMMENT '业务对象标识',\n" +
                "    `dedupe_key`     varchar(128) DEFAULT NULL COMMENT '去重键（同收件人+同键+未读＝只更新）',\n" +
                "    `target_user`    varchar(50)  NOT NULL COMMENT '收件人 ac_user.customer_number',\n" +
                "    `tenant_id`      bigint(20)   DEFAULT NULL COMMENT '租户归属（可空＝全局；信息列不进拦截器）',\n" +
                "    `read_flag`      char(1)      NOT NULL DEFAULT '0' COMMENT '已读标志 0 未读 1 已读',\n" +
                "    `read_at`        datetime(3)  DEFAULT NULL COMMENT '已读时间',\n" +
                "    `pushed_at`      datetime(3)  NOT NULL COMMENT '消息产生时间（排序）',\n" +
                "    `extra`          varchar(1000) DEFAULT NULL COMMENT '业务上下文 JSON',\n" +
                "    `create_by`      varchar(50)  DEFAULT NULL COMMENT '创建者',\n" +
                "    `create_at`      datetime(3)  DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',\n" +
                "    `update_by`      varchar(50)  DEFAULT NULL COMMENT '更新者',\n" +
                "    `update_at`      datetime(3)  DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',\n" +
                "    `valid`          char(1)      DEFAULT '1' COMMENT '有效性',\n" +
                "    PRIMARY KEY (`id`),\n" +
                "    KEY `idx_receiver_read` (`target_user`, `read_flag`, `pushed_at`),\n" +
                "    KEY `idx_receiver_dedupe` (`target_user`, `dedupe_key`, `read_flag`),\n" +
                "    KEY `idx_tenant_receiver` (`tenant_id`, `target_user`, `read_flag`)\n" +
                ") COMMENT='站内消息（个人收件箱：写时扇开一人一行）';");
    }
}
