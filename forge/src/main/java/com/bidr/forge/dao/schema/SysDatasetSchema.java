package com.bidr.forge.dao.schema;

import com.bidr.forge.dao.entity.SysDataset;
import com.bidr.kernel.mybatis.repository.BaseMybatisSchema;
import org.springframework.stereotype.Service;

/**
 * 数据集主表 Schema
 *
 * @author sharp
 * @since 2025-11-25
 */
@Service
public class SysDatasetSchema extends BaseMybatisSchema<SysDataset> {
    static {
        setCreateDDL("CREATE TABLE IF NOT EXISTS `sys_dataset` (\n" +
                "  `id` bigint(20) NOT NULL AUTO_INCREMENT COMMENT '数据集ID',\n" +
                "  `dataset_name` varchar(100) DEFAULT NULL COMMENT '数据集名称',\n" +
                "  `data_source` varchar(50) DEFAULT NULL COMMENT '数据源配置名称',\n" +
                "  `remark` text COMMENT '备注',\n" +
                "  `create_by` varchar(50) DEFAULT NULL COMMENT '创建者',\n" +
                "  `create_at` datetime(3) DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',\n" +
                "  `update_by` varchar(50) DEFAULT NULL COMMENT '更新者',\n" +
                "  `update_at` datetime(3) DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',\n" +
                "  `valid` char(1) DEFAULT '1' COMMENT '有效性',\n" +
                "  PRIMARY KEY (`id`)\n" +
                ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='数据集主表';");
    }
}
