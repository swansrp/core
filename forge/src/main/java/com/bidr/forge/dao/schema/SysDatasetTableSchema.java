package com.bidr.forge.dao.schema;

import com.bidr.forge.dao.entity.SysDatasetTable;
import com.bidr.kernel.mybatis.repository.BaseMybatisSchema;
import org.springframework.stereotype.Service;

/**
 * 数据集关联表 Schema
 *
 * @author sharp
 * @since 2025-11-25
 */
@Service
public class SysDatasetTableSchema extends BaseMybatisSchema<SysDatasetTable> {
    static {
        setCreateDDL("CREATE TABLE IF NOT EXISTS `sys_dataset_table` (\n" +
                "  `id` bigint(20) NOT NULL AUTO_INCREMENT COMMENT '主键ID',\n" +
                "  `dataset_id` bigint(20) NOT NULL COMMENT '关联的数据集ID',\n" +
                "  `data_source` varchar(50) DEFAULT NULL COMMENT '多源数据库配置名称',\n" +
                "  `table_order` int(11) NOT NULL DEFAULT '0' COMMENT '表顺序',\n" +
                "  `table_sql` text COMMENT '关联表SQL',\n" +
                "  `table_alias` varchar(50) DEFAULT NULL COMMENT '表别名',\n" +
                "  `join_type` varchar(20) DEFAULT NULL COMMENT 'JOIN类型（主表可为空）',\n" +
                "  `join_condition` text COMMENT 'ON条件（主表可为空）',\n" +
                "  `remark` text COMMENT '备注',\n" +
                "  `create_by` varchar(50) DEFAULT NULL COMMENT '创建者',\n" +
                "  `create_at` datetime(3) DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',\n" +
                "  `update_by` varchar(50) DEFAULT NULL COMMENT '更新者',\n" +
                "  `update_at` datetime(3) DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',\n" +
                "  PRIMARY KEY (`id`),\n" +
                "  KEY `idx_dataset_id` (`dataset_id`)\n" +
                ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='数据集关联表';");
    }
}
