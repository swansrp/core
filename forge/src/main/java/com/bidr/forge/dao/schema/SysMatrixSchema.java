package com.bidr.forge.dao.schema;

import com.bidr.forge.dao.entity.SysMatrix;
import com.bidr.kernel.mybatis.repository.BaseMybatisSchema;
import org.springframework.stereotype.Service;

/**
 * 矩阵配置Schema
 *
 * @author sharp
 * @since 2025-11-20
 */
@Service
public class SysMatrixSchema extends BaseMybatisSchema<SysMatrix> {
    static {
        setCreateDDL("CREATE TABLE IF NOT EXISTS `sys_matrix` (\n" +
                "  `id` bigint(20) NOT NULL AUTO_INCREMENT COMMENT '主键ID',\n" +
                "  `table_name` varchar(100) NOT NULL COMMENT '表名',\n" +
                "  `table_comment` varchar(200) DEFAULT NULL COMMENT '表注释',\n" +
                "  `data_source` varchar(50) DEFAULT NULL COMMENT '数据源名称',\n" +
                "  `primary_key` varchar(100) DEFAULT 'id' COMMENT '主键字段',\n" +
                "  `index_config` text COMMENT '索引配置(JSON)',\n" +
                "  `engine` varchar(20) DEFAULT 'InnoDB' COMMENT '存储引擎',\n" +
                "  `charset` varchar(20) DEFAULT 'utf8mb4' COMMENT '字符集',\n" +
                "  `status` char(1) DEFAULT '0' COMMENT '状态(0:未创建,1:已创建,2:已同步)',\n" +
                "  `sort` int(11) NOT NULL DEFAULT '0' COMMENT '排序',\n" +
                "  `create_by` varchar(50) DEFAULT NULL COMMENT '创建者',\n" +
                "  `create_at` datetime(3) DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',\n" +
                "  `update_by` varchar(50) DEFAULT NULL COMMENT '更新者',\n" +
                "  `update_at` datetime(3) DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',\n" +
                "  `valid` char(1) DEFAULT '1' COMMENT '有效性',\n" +
                "  PRIMARY KEY (`id`),\n" +
                "  UNIQUE KEY `uk_table_name` (`table_name`)\n" +
                ") COMMENT='矩阵配置';");
    }
}
