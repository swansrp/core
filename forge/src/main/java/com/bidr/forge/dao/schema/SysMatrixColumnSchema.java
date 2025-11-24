package com.bidr.forge.dao.schema;

import com.bidr.forge.dao.entity.SysMatrixColumn;
import com.bidr.kernel.mybatis.repository.BaseMybatisSchema;
import org.springframework.stereotype.Service;

/**
 * 矩阵字段配置Schema
 *
 * @author sharp
 * @since 2025-11-20
 */
@Service
public class SysMatrixColumnSchema extends BaseMybatisSchema<SysMatrixColumn> {
    static {
        setCreateDDL("CREATE TABLE IF NOT EXISTS `sys_matrix_column` (\n" +
                "  `id` bigint(20) NOT NULL AUTO_INCREMENT COMMENT '主键ID',\n" +
                "  `matrix_id` bigint(20) NOT NULL COMMENT '矩阵ID',\n" +
                "  `column_name` varchar(100) NOT NULL COMMENT '字段名',\n" +
                "  `column_comment` varchar(200) DEFAULT NULL COMMENT '字段注释',\n" +
                "  `column_type` varchar(50) NOT NULL COMMENT '字段类型',\n" +
                "  `field_type` varchar(2) NOT NULL COMMENT '表单字段类型',\n" +
                "  `column_length` int(11) DEFAULT NULL COMMENT '字段长度',\n" +
                "  `decimal_places` int(11) DEFAULT NULL COMMENT '小数位数',\n" +
                "  `is_nullable` char(1) DEFAULT '1' COMMENT '是否可空',\n" +
                "  `default_value` varchar(200) DEFAULT NULL COMMENT '默认值',\n" +
                "  `is_primary_key` char(1) DEFAULT '0' COMMENT '是否主键',\n" +
                "  `is_index` char(1) DEFAULT '0' COMMENT '是否索引',\n" +
                "  `is_unique` char(1) DEFAULT '0' COMMENT '是否唯一',\n" +
                "  `sort` int(11) NOT NULL DEFAULT '0' COMMENT '排序',\n" +
                "  `create_by` varchar(50) DEFAULT NULL COMMENT '创建者',\n" +
                "  `create_at` datetime(3) DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',\n" +
                "  `update_by` varchar(50) DEFAULT NULL COMMENT '更新者',\n" +
                "  `update_at` datetime(3) DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',\n" +
                "  `valid` char(1) DEFAULT '1' COMMENT '有效性',\n" +
                "  PRIMARY KEY (`id`),\n" +
                "  KEY `idx_matrix_id` (`matrix_id`)\n" +
                ") COMMENT='矩阵字段配置';");

        // 添加新字段的升级脚本
        setUpgradeDDL(1, "ALTER TABLE `sys_matrix_column` " +
                "ADD COLUMN `is_display_name_field` char(1) DEFAULT '0' COMMENT '名称字段' AFTER `sort`, " +
                "ADD COLUMN `is_order_field` char(1) DEFAULT '0' COMMENT '顺序字段' AFTER `is_display_name_field`, " +
                "ADD COLUMN `is_pid_field` char(1) DEFAULT '0' COMMENT '父节点字段' AFTER `is_order_field`, " +
                "ADD COLUMN `reference_matrix_id` varchar(50) DEFAULT NULL COMMENT '关联矩阵' AFTER `is_pid_field`, " +
                "ADD COLUMN `reference_dict` varchar(50) DEFAULT NULL COMMENT '关联字典' AFTER `reference_matrix_id`, " +
                "ADD COLUMN `sequence` varchar(50) DEFAULT NULL COMMENT '序列' AFTER `default_value`;");
    }
}
