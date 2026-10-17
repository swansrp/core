package com.bidr.admin.dao.schema;

import com.bidr.admin.dao.entity.SysPortalPivotColumn;
import com.bidr.kernel.mybatis.repository.BaseMybatisSchema;
import org.springframework.stereotype.Service;

/**
 * 透视报表父表头列配置 Schema Service
 *
 * @author Sharp
 */
@Service
public class SysPortalPivotColumnSchema extends BaseMybatisSchema<SysPortalPivotColumn> {
    static {
        setCreateDDL("CREATE TABLE IF NOT EXISTS `sys_portal_pivot_column` (\n" +
                "  `id` bigint NOT NULL AUTO_INCREMENT COMMENT 'id',\n" +
                "  `table_id` bigint NOT NULL COMMENT 'table_id',\n" +
                "  `item_value` varchar(50) NOT NULL COMMENT '列标识',\n" +
                "  `item_name` varchar(100) NOT NULL COMMENT '表头名称',\n" +
                "  `condition` longtext NULL COMMENT '列条件json',\n" +
                "  `display_order` int NOT NULL DEFAULT '99' COMMENT '显示顺序',\n" +
                "  `status` char(1) NOT NULL DEFAULT '1' COMMENT '状态',\n" +
                "  PRIMARY KEY (`id`),\n" +
                "  KEY `table_id` (`table_id`)\n" +
                ") COMMENT='透视报表父表头列配置';");

        // 树形多层表头: 叶子列的父链路径(自外向内 label 数组 JSON), 仅 row 布局渲染消费
        setUpgradeDDL(1, "ALTER TABLE `sys_portal_pivot_column` ADD COLUMN `group_path` longtext NULL COMMENT '父链路径JSON(叶子列多层父表头, 自外向内, 仅row树形表头使用)' AFTER `condition`;");
    }
}
