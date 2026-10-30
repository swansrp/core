package com.bidr.forge.dao.schema;

import com.bidr.forge.dao.entity.SysDatasetSqlSnapshot;
import com.bidr.kernel.mybatis.repository.BaseMybatisSchema;
import org.springframework.stereotype.Service;

/**
 * 数据集 SQL 快照 Schema
 *
 * @author sharp
 * @since 2026-10-30
 */
@Service
public class SysDatasetSqlSnapshotSchema extends BaseMybatisSchema<SysDatasetSqlSnapshot> {
    static {
        setCreateDDL("CREATE TABLE IF NOT EXISTS `sys_dataset_sql_snapshot` (\n" +
                "  `id` bigint(20) NOT NULL AUTO_INCREMENT COMMENT '主键ID',\n" +
                "  `dataset_id` bigint(20) NOT NULL COMMENT '关联的数据集ID',\n" +
                "  `sql_text` mediumtext NOT NULL COMMENT '该次保存的完整SQL',\n" +
                "  `data_source` varchar(50) DEFAULT NULL COMMENT '数据源配置名称',\n" +
                "  `remark` varchar(200) DEFAULT NULL COMMENT '数据集备注(保存时点)',\n" +
                "  `create_by` varchar(50) DEFAULT NULL COMMENT '创建者',\n" +
                "  `create_at` datetime(3) DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',\n" +
                "  PRIMARY KEY (`id`),\n" +
                "  KEY `idx_dataset_id` (`dataset_id`)\n" +
                ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='数据集SQL快照';");
    }
}
