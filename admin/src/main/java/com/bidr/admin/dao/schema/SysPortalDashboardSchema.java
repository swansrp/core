package com.bidr.admin.dao.schema;

import com.bidr.admin.dao.entity.SysPortalDashboard;
import com.bidr.kernel.mybatis.repository.BaseMybatisSchema;
import org.springframework.stereotype.Service;

/**
 * 个人件表板配置Schema Service
 *
 * @author sharp
 * @since 2025-11-17
 */
@Service
public class SysPortalDashboardSchema extends BaseMybatisSchema<SysPortalDashboard> {

    static {
        setCreateDDL("CREATE TABLE IF NOT EXISTS `sys_portal_dashboard` (\n" +
                "  `id` varchar(50) NOT NULL DEFAULT '',\n" +
                "  `statistic_id` bigint NOT NULL COMMENT '数据id',\n" +
                "  `customer_number` varchar(50) NOT NULL COMMENT '所属用户',\n" +
                "  `x_position` int NOT NULL COMMENT '图表横坐标',\n" +
                "  `y_position` int NOT NULL COMMENT '图表纵坐标',\n" +
                "  `x_grid` int NOT NULL COMMENT '图表宽度',\n" +
                "  `y_grid` int NOT NULL COMMENT '图表高度',\n" +
                "  PRIMARY KEY (`id`),\n" +
                "  UNIQUE KEY `statistic_id_customer_number` (`statistic_id`,`customer_number`)\n" +
                ") COMMENT='个人仪表盘配置';");
        setUpgradeDDL(1, "ALTER TABLE sys_portal_dashboard MODIFY COLUMN customer_number varchar(50) NULL COMMENT '所属用户';");
    }
}
