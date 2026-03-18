package com.bidr.admin.dao.schema;

import com.bidr.admin.dao.entity.SysPortalTableFilter;
import com.bidr.kernel.mybatis.repository.BaseMybatisSchema;
import org.springframework.stereotype.Service;

/**
 * 表格报表筛选项 Schema Service
 *
 * @author Sharp
 */
@Service
public class SysPortalTableFilterSchema extends BaseMybatisSchema<SysPortalTableFilter> {
    static {
        setCreateDDL("CREATE TABLE IF NOT EXISTS `sys_portal_table_filter` (\n" +
                "  `id` bigint NOT NULL AUTO_INCREMENT COMMENT 'id',\n" +
                "  `table_id` bigint NOT NULL COMMENT 'table_id',\n" +
                "  `filter_type` varchar(50) NOT NULL COMMENT '筛选条目类型',\n" +
                "  `label` varchar(50) NOT NULL COMMENT '筛选条目标签',\n" +
                "  `condition` varchar(500) NOT NULL COMMENT '筛选条件',\n" +
                "  `dict_code` varchar(50) NOT NULL COMMENT '字典项',\n" +
                "  `default_value` varchar(50) NOT NULL COMMENT '默认值',\n" +
                "  `placeholder` varchar(50) NOT NULL COMMENT '占位文本',\n" +
                "  `allow_clear` char(1) NOT NULL DEFAULT '1' COMMENT '是否允许清空',\n" +
                "  `multiple` char(1) NOT NULL DEFAULT '1' COMMENT '是否多选',\n" +
                "  `display_order` int NOT NULL DEFAULT '99' COMMENT '显示顺序',\n" +
                "  `status` char(1) NOT NULL DEFAULT '1' COMMENT '状态',\n" +
                "  PRIMARY KEY (`id`),\n" +
                "  KEY `table_id` (`table_id`)\n" +
                ") COMMENT='表格报表筛选项';");
        setUpgradeDDL(1, "ALTER TABLE `sys_portal_table_filter`\n" +
                "\tCHANGE COLUMN `condition` `condition` LONGTEXT NULL COMMENT '筛选条件' AFTER `label`;\n");
    }
}
