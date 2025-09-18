package com.bidr.admin.dao.repository;

import com.bidr.admin.dao.entity.SysPortalDashboard;
import com.bidr.admin.dao.mapper.SysPortalDashboardMapper;
import com.bidr.kernel.mybatis.repository.BaseSqlRepo;
import org.springframework.stereotype.Service;

/**
 * @author Sharp
 */
@Service
public class SysPortalDashboardService extends BaseSqlRepo<SysPortalDashboardMapper, SysPortalDashboard> {

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
    }
}
