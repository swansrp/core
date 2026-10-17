package com.bidr.platform.dao.schema;

import com.bidr.kernel.mybatis.repository.BaseMybatisSchema;
import com.bidr.platform.dao.entity.SysDynamicDictConfig;
import org.springframework.stereotype.Service;

/**
 * 动态字典配置表 Schema
 *
 * @author Sharp
 * @since 2026-07-14
 */
@Service
public class SysDynamicDictConfigSchema extends BaseMybatisSchema<SysDynamicDictConfig> {
    static {
        setCreateDDL("CREATE TABLE IF NOT EXISTS `sys_dynamic_dict_config` (\n" +
                "  `id` bigint(20) NOT NULL AUTO_INCREMENT COMMENT '主键ID',\n" +
                "  `dict_code` varchar(200) NOT NULL COMMENT '字典编码（业务字典中的dict_code）',\n" +
                "  `dict_name` varchar(200) NOT NULL COMMENT '字典显示名称',\n" +
                "  `data_source` varchar(100) DEFAULT NULL COMMENT '数据源名称',\n" +
                "  `database_name` varchar(100) DEFAULT NULL COMMENT '数据库名',\n" +
                "  `table_name` varchar(200) NOT NULL COMMENT '表名',\n" +
                "  `value_column` varchar(100) NOT NULL COMMENT 'value字段列名',\n" +
                "  `label_column` varchar(100) NOT NULL COMMENT 'label字段列名',\n" +
                "  `order_by` varchar(100) DEFAULT NULL COMMENT '排序方式',\n" +
                "  `conditions` text DEFAULT NULL COMMENT '筛选条件（JSON格式）',\n" +
                "  `create_by` varchar(50) DEFAULT NULL COMMENT '创建者',\n" +
                "  `create_at` datetime(3) DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',\n" +
                "  `update_by` varchar(50) DEFAULT NULL COMMENT '更新者',\n" +
                "  `update_at` datetime(3) DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',\n" +
                "  `valid` char(1) DEFAULT '1' COMMENT '是否有效',\n" +
                "  PRIMARY KEY (`id`) USING BTREE,\n" +
                "  UNIQUE KEY `uk_dict_code` (`dict_code`) USING BTREE\n" +
                ") COMMENT='动态字典配置表';");
        setUpgradeDDL(1, "ALTER TABLE `sys_dynamic_dict_config`\n" +
                "  ADD COLUMN `pid_column` varchar(100) DEFAULT NULL COMMENT '父级ID列名（有值则为树形字典模式）' AFTER `order_by`;");
    }
}
