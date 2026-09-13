package com.bidr.admin.dao.schema;

import com.bidr.admin.dao.entity.SysPortalTable;
import com.bidr.kernel.mybatis.repository.BaseMybatisSchema;
import org.springframework.stereotype.Service;

/**
 * 表格展示配置 Schema Service
 *
 * @author Sharp
 */
@Service
public class SysPortalTableSchema extends BaseMybatisSchema<SysPortalTable> {
    static {
        setCreateDDL("CREATE TABLE IF NOT EXISTS `sys_portal_table` (\n" +
                "  `id` bigint NOT NULL AUTO_INCREMENT COMMENT 'id',\n" +
                "  `portal_name` varchar(50) NOT NULL COMMENT '表格配置名称',\n" +
                "  `table_code` varchar(50) NOT NULL DEFAULT '' COMMENT '表格 code',\n" +
                "  `filter_width` int NOT NULL DEFAULT '300' COMMENT '左侧筛选栏的宽度',\n" +
                "  `padding_th` int DEFAULT NULL COMMENT '标题间隔',\n" +
                "  `padding_td` int DEFAULT NULL COMMENT '筛选条目间隔',\n" +
                "  `status` varchar(50) NOT NULL DEFAULT '1' COMMENT '状态',\n" +
                "  PRIMARY KEY (`id`),\n" +
                "  KEY `portal_name` (`portal_name`),\n" +
                "  UNIQUE KEY `table_code` (`table_code`)\n" +
                ") COMMENT='表格展示配置';");
        setUpgradeDDL(1, "ALTER TABLE `sys_portal_table`\n" +
                "\tADD COLUMN `filter_columns` VARCHAR(500) NULL DEFAULT NULL COMMENT '要排除显示的列' AFTER `padding_td`;\n");
        setUpgradeDDL(2, "ALTER TABLE `sys_portal_table`\n" +
                "\tADD COLUMN `download_able` CHAR(1) NOT NULL DEFAULT '1' COMMENT '是否允许下载' AFTER `filter_columns`;\n");
        setUpgradeDDL(3, "ALTER TABLE `sys_portal_table`\n" +
                "\tADD COLUMN `pivot_mode` CHAR(1) NOT NULL DEFAULT '0' COMMENT '是否透视报表模式' AFTER `download_able`,\n" +
                "\tADD COLUMN `group_by_fields` VARCHAR(500) NULL DEFAULT NULL COMMENT '透视行维度字段(逗号分隔)' AFTER `pivot_mode`,\n" +
                "\tADD COLUMN `pivot_measures` VARCHAR(2000) NULL DEFAULT NULL COMMENT '透视度量列配置JSON' AFTER `group_by_fields`;\n");
        // 多Tab共享筛选栏: 非空=宿主记录(页面级路由抓手), JSON成员[tableId,label,order]即其右侧tab(平级portalTable记录)
        setUpgradeDDL(4, "ALTER TABLE `sys_portal_table`\n" +
                "\tADD COLUMN `tab_items` TEXT NULL COMMENT 'Tab成员JSON(仅宿主): [{tableId,label,order}]' AFTER `pivot_measures`;\n");
        // 宽表场景容量升级: filter_columns 存排除列逗号串超 VARCHAR(500); group_by_fields 存行维度JSON、pivot_measures 存度量JSON同样有超限风险
        setUpgradeDDL(5, "ALTER TABLE `sys_portal_table`\n" +
                "\tMODIFY COLUMN `filter_columns` TEXT NULL COMMENT '要排除显示的列',\n" +
                "\tMODIFY COLUMN `group_by_fields` TEXT NULL COMMENT '透视行维度字段(逗号分隔)',\n" +
                "\tMODIFY COLUMN `pivot_measures` TEXT NULL COMMENT '透视度量列配置JSON';\n");
    }
}