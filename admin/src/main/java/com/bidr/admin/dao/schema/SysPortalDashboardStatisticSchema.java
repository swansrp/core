package com.bidr.admin.dao.schema;

import com.bidr.admin.dao.entity.SysPortalDashboardStatistic;
import com.bidr.kernel.mybatis.repository.BaseMybatisSchema;
import org.springframework.stereotype.Service;

/**
 * 件表板统计Schema Service
 *
 * @author sharp
 * @since 2025-11-17
 */
@Service
public class SysPortalDashboardStatisticSchema extends BaseMybatisSchema<SysPortalDashboardStatistic> {

    static {
        setCreateDDL("CREATE TABLE IF NOT EXISTS `sys_portal_dashboard_statistic` (\n" +
                "  `id` bigint NOT NULL COMMENT 'id',\n" +
                "  `pid` bigint NULL COMMENT 'pid',\n" +
                "  `table_id` varchar(50) NOT NULL COMMENT '表名称',\n" +
                "  `title` varchar(50) NOT NULL COMMENT '显示名称',\n" +
                "  `customer_number` varchar(20) DEFAULT NULL COMMENT '所属人',\n" +
                "  `order` int NOT NULL DEFAULT '0' COMMENT '指标树顺序',\n" +
                "  `indicator` longtext DEFAULT NULL COMMENT '指标配置',\n" +
                "  PRIMARY KEY (`id`),\n" +
                "  KEY `pid` (`pid`),\n" +
                "  KEY `customer_number` (`customer_number`),\n" +
                "  KEY `table_id` (`table_id`)\n" +
                ") COMMENT='通用仪表盘数据';");
        setUpgradeDDL(1,
                "ALTER TABLE sys_portal_dashboard_statistic MODIFY COLUMN id bigint auto_increment NOT NULL COMMENT 'id';");
        setUpgradeDDL(2,
                "ALTER TABLE `sys_portal_dashboard_statistic` \n" +
                        "ADD COLUMN `default_x_grid` INT DEFAULT 2 COMMENT '默认宽度',\n" +
                        "ADD COLUMN `default_y_grid` INT DEFAULT 2 COMMENT '默认高度';");
    }
}
