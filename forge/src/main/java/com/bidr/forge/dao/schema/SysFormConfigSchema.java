package com.bidr.forge.dao.schema;

import com.bidr.forge.dao.entity.SysFormConfig;
import com.bidr.kernel.mybatis.repository.BaseMybatisSchema;
import org.springframework.stereotype.Service;

/**
 * 动态表单配置Schema
 *
 * @author sharp
 * @since 2025-11-20
 */
@Service
public class SysFormConfigSchema extends BaseMybatisSchema<SysFormConfig> {
    static {
        setCreateDDL("CREATE TABLE IF NOT EXISTS `sys_form_config` (\n" +
                "  `id` bigint(20) NOT NULL AUTO_INCREMENT COMMENT '主键ID',\n" +
                "  `matrix_id` bigint(20) NOT NULL COMMENT '矩阵ID',\n" +
                "  `column_id` bigint(20) NOT NULL COMMENT '字段ID',\n" +
                "  `label` varchar(100) NOT NULL COMMENT '显示标签',\n" +
                "  `description` varchar(200) DEFAULT NULL COMMENT '描述',\n" +
                "  `field_type` varchar(2) NOT NULL COMMENT '字段类型',\n" +
                "  `dict` varchar(50) DEFAULT NULL COMMENT '所属字典',\n" +
                "  `unit` varchar(20) DEFAULT NULL COMMENT '单位',\n" +
                "  `default_value` varchar(200) DEFAULT NULL COMMENT '默认值',\n" +
                "  `max_value` varchar(50) DEFAULT NULL COMMENT '最大值',\n" +
                "  `min_value` varchar(50) DEFAULT NULL COMMENT '最小值',\n" +
                "  `is_required` char(1) NOT NULL DEFAULT '0' COMMENT '是否必填',\n" +
                "  `readonly` char(1) NOT NULL DEFAULT '0' COMMENT '是否只读',\n" +
                "  `validation_rule` varchar(200) DEFAULT NULL COMMENT '正则表达式',\n" +
                "  `width` int(11) DEFAULT NULL COMMENT '宽',\n" +
                "  `height` int(11) DEFAULT NULL COMMENT '高',\n" +
                "  `position_x` int(11) DEFAULT NULL COMMENT '横轴坐标',\n" +
                "  `position_y` int(11) DEFAULT NULL COMMENT '纵轴坐标',\n" +
                "  `sort` int(11) NOT NULL DEFAULT '0' COMMENT '排序',\n" +
                "  `create_by` varchar(50) DEFAULT NULL COMMENT '创建者',\n" +
                "  `create_at` datetime(3) DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',\n" +
                "  `update_by` varchar(50) DEFAULT NULL COMMENT '更新者',\n" +
                "  `update_at` datetime(3) DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',\n" +
                "  `valid` char(1) DEFAULT '1' COMMENT '有效性',\n" +
                "  PRIMARY KEY (`id`),\n" +
                "  KEY `idx_matrix_id` (`matrix_id`),\n" +
                "  KEY `idx_column_id` (`column_id`)\n" +
                ") COMMENT='动态表单配置';");
    }
}
