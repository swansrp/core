package com.bidr.admin.dao.repository;

import com.bidr.admin.dao.entity.SysPortalDashboardStatistic;
import com.bidr.admin.dao.mapper.SysPortalDashboardStatisticMapper;
import com.bidr.kernel.mybatis.inf.MybatisPlusTableInitializerInf;
import com.bidr.kernel.mybatis.repository.BaseSqlRepo;
import org.springframework.stereotype.Service;

/**
 * @author Sharp
 */
@Service
public class SysPortalDashboardStatisticService extends BaseSqlRepo<SysPortalDashboardStatisticMapper, SysPortalDashboardStatistic> implements MybatisPlusTableInitializerInf {

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
    }
}
