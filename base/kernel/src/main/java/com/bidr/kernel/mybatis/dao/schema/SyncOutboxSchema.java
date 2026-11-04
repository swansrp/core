package com.bidr.kernel.mybatis.dao.schema;

import com.bidr.kernel.mybatis.dao.entity.SyncOutbox;
import com.bidr.kernel.mybatis.repository.BaseMybatisSchema;
import org.springframework.stereotype.Service;

/**
 * 变更事件发件箱 Schema
 * <p>
 * 无条件 {@code @Service}（D1）：recorder 不自建表，若建表跟着开关走，第一个调用方撞的就是
 * "表不存在"。落表本身不产生业务行为——只有真调 {@code record} 才写行。
 * ⚠️ 代价：所有引入 core 的宿主启动后都会多一张空的 {@code sync_outbox}。
 * </p>
 * <p>
 * 列与索引一次定型（D3）：{@code id} 是游标故必须自增；{@code occurred_at} 与 {@code create_at}
 * 分开放，是因为保留期必须按"事件发生"裁而不是按"落库"裁——补录或积压会让两者差很远。
 * </p>
 *
 * @author Sharp
 * @since 2026/11/03
 */
@Service
public class SyncOutboxSchema extends BaseMybatisSchema<SyncOutbox> {
    static {
        setCreateDDL("CREATE TABLE IF NOT EXISTS `sync_outbox`\n" +
                "(\n" +
                "    `id`          bigint(20)   NOT NULL AUTO_INCREMENT COMMENT 'id（同时是增量游标：只增不改，按 id 升序拉取）',\n" +
                "    `tenant_id`   bigint(20)   DEFAULT NULL COMMENT '租户归属（建表即带；空＝全局事件）',\n" +
                "    `agg_type`    varchar(64)  NOT NULL COMMENT '聚合类型（USER/ORG/PROJECT 等，消费方自定义字符串）',\n" +
                "    `agg_id`      bigint(20)   NOT NULL COMMENT '聚合标识（被变更实体主键）',\n" +
                "    `event_type`  varchar(64)  NOT NULL COMMENT '事件类型（CREATED/UPDATED/DELETED 等）',\n" +
                "    `payload`     text         DEFAULT NULL COMMENT '事件载荷 JSON（核心不解析，原样转发）',\n" +
                "    `occurred_at` datetime(3)  NOT NULL COMMENT '事件发生时刻（保留期按它算，不是落库时刻）',\n" +
                "    `create_at`   datetime(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '落库时刻（与 occurred_at 错开时以 occurred_at 为准）',\n" +
                "    PRIMARY KEY (`id`),\n" +
                "    KEY `idx_sync_outbox_occurred` (`occurred_at`),\n" +
                "    KEY `idx_sync_outbox_agg` (`agg_type`, `agg_id`)\n" +
                ") COMMENT='变更事件发件箱（T3.3a）：只追加不改写，按 id 游标供外部系统增量拉取'");
    }
}