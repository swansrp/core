package com.bidr.forge.dao.schema;

import com.bidr.forge.dao.entity.SysDatasetColumn;
import com.bidr.kernel.mybatis.repository.BaseMybatisSchema;
import org.springframework.stereotype.Service;

/**
 * 数据集列配置 Schema
 *
 * @author sharp
 * @since 2025-11-25
 */
@Service
public class SysDatasetColumnSchema extends BaseMybatisSchema<SysDatasetColumn> {
    static {
        setCreateDDL("CREATE TABLE IF NOT EXISTS `sys_dataset_column` (\n" +
                "  `id` bigint(20) NOT NULL AUTO_INCREMENT COMMENT '主键ID',\n" +
                "  `dataset_id` bigint(20) NOT NULL COMMENT '关联的数据集ID',\n" +
                "  `column_sql` text COMMENT '字段SQL表达式',\n" +
                "  `column_alias` varchar(100) DEFAULT NULL COMMENT '字段别名',\n" +
                "  `is_aggregate` char(1) DEFAULT '0' COMMENT '是否是聚合字段',\n" +
                "  `display_order` int(11) NOT NULL DEFAULT '0' COMMENT '前端显示排序',\n" +
                "  `is_visible` char(1) DEFAULT '1' COMMENT '是否显示在结果集中',\n" +
                "  `remark` text COMMENT '备注',\n" +
                "  `create_by` varchar(50) DEFAULT NULL COMMENT '创建者',\n" +
                "  `create_at` datetime(3) DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',\n" +
                "  `update_by` varchar(50) DEFAULT NULL COMMENT '更新者',\n" +
                "  `update_at` datetime(3) DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',\n" +
                "  PRIMARY KEY (`id`),\n" +
                "  KEY `idx_dataset_id` (`dataset_id`)\n" +
                ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='数据集列配置';");
    }
}
