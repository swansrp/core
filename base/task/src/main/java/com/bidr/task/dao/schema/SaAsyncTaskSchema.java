package com.bidr.task.dao.schema;

import com.bidr.kernel.mybatis.repository.BaseMybatisSchema;
import com.bidr.task.dao.entity.SaAsyncTask;
import org.springframework.stereotype.Service;

/**
 * 通用异步任务表结构定义（DDL 正源，建表即冻结，变更只走 setUpgradeDDL 追加；M16 棒 2 新表）。
 *
 * <p>通用列一列不认识业务形态：无租户列、无任何业务对象列——跨阶段载体只有
 * {@code payload} 与 {@code external_id} 两个通用列（纪律 §4.2.4-④）。</p>
 *
 * @author sharp
 */
@Service
public class SaAsyncTaskSchema extends BaseMybatisSchema<SaAsyncTask> {

    static {
        setCreateDDL("CREATE TABLE IF NOT EXISTS `sa_async_task`\n" +
                "(\n" +
                "    `id`                bigint(20) NOT NULL AUTO_INCREMENT COMMENT 'id',\n" +
                "    `task_type`         varchar(32)   NOT NULL COMMENT '任务类型（业务流程标识）',\n" +
                "    `stage`             varchar(32)   NOT NULL COMMENT '当前阶段标识（只许原地或前进）',\n" +
                "    `status`            varchar(20)   NOT NULL DEFAULT 'PENDING' COMMENT '状态 PENDING/RUNNING/SUCCESS/FAILED/DEAD',\n" +
                "    `payload`           text COMMENT '业务提交的入参 JSON（跨阶段载体一）',\n" +
                "    `biz_ref`           varchar(128)  NOT NULL COMMENT '业务参照（幂等键后半，业务对象唯一定位串）',\n" +
                "    `external_id`       varchar(64)   DEFAULT NULL COMMENT '外部引擎任务id（跨阶段载体二）',\n" +
                "    `external_status`   varchar(20)   DEFAULT NULL COMMENT '外部状态观测（对账用）',\n" +
                "    `external_snapshot` text COMMENT '外部子状态快照JSON（对账用）',\n" +
                "    `attempt`           int(11)       NOT NULL DEFAULT '0' COMMENT '已尝试次数',\n" +
                "    `next_run_at`       datetime(3)   DEFAULT NULL COMMENT '下次可执行时间（退避用）',\n" +
                "    `error`             varchar(1024) DEFAULT NULL COMMENT '最近错误',\n" +
                "    `owner`             varchar(64)   DEFAULT NULL COMMENT '抢占者（实例标识）',\n" +
                "    `lease_expire`      datetime(3)   DEFAULT NULL COMMENT '租约到期',\n" +
                "    `create_by`         varchar(50)   DEFAULT NULL COMMENT '创建者',\n" +
                "    `create_at`         datetime(3)   DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',\n" +
                "    `update_by`         varchar(50)   DEFAULT NULL COMMENT '更新者',\n" +
                "    `update_at`         datetime(3)   DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',\n" +
                "    `valid`             char(1)       DEFAULT '1' COMMENT '有效性',\n" +
                "    PRIMARY KEY (`id`),\n" +
                "    UNIQUE KEY `uk_type_biz` (`task_type`, `biz_ref`),\n" +
                "    KEY `scan` (`status`, `next_run_at`)\n" +
                ") COMMENT='通用异步任务（受理落库＋阶段推进＋重试记账）';");
    }
}
