package com.bidr.forge.dao.schema;

import com.bidr.forge.dao.entity.SysMatrixChangeLog;
import com.bidr.kernel.mybatis.repository.BaseMybatisSchema;
import org.springframework.stereotype.Service;

/**
 * 矩阵表结构变更日志 Schema
 *
 * @author sharp
 * @since 2025-11-21
 */
@Service
public class SysMatrixChangeLogSchema extends BaseMybatisSchema<SysMatrixChangeLog> {

    static {
        setCreateDDL("CREATE TABLE IF NOT EXISTS `sys_matrix_change_log` (\n" +
                "  `id` bigint(20) NOT NULL AUTO_INCREMENT COMMENT '主键ID',\n" +
                "  `matrix_id` bigint(20) NOT NULL COMMENT '矩阵ID',\n" +
                "  `version` int(11) NOT NULL COMMENT '版本号',\n" +
                "  `change_type` varchar(2) NOT NULL COMMENT '变更类型：1-创建表，2-添加字段，3-调整字段顺序，4-添加索引，5-删除索引',\n" +
                "  `change_desc` varchar(500) DEFAULT NULL COMMENT '变更描述',\n" +
                "  `ddl_statement` text COMMENT '执行的DDL语句',\n" +
                "  `affected_column` varchar(100) DEFAULT NULL COMMENT '影响的字段名',\n" +
                "  `execute_status` varchar(1) NOT NULL DEFAULT '1' COMMENT '执行状态：0-失败，1-成功',\n" +
                "  `error_msg` text COMMENT '错误信息',\n" +
                "  `sort` int(11) DEFAULT NULL COMMENT '排序',\n" +
                "  `create_at` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',\n" +
                "  `create_by` varchar(20) DEFAULT NULL COMMENT '创建人工号',\n" +
                "  `update_at` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',\n" +
                "  `update_by` varchar(20) DEFAULT NULL COMMENT '更新人工号',\n" +
                "  PRIMARY KEY (`id`),\n" +
                "  KEY `idx_matrix_id` (`matrix_id`),\n" +
                "  KEY `idx_version` (`version`)\n" +
                ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='矩阵表结构变更日志';");
    }
}
