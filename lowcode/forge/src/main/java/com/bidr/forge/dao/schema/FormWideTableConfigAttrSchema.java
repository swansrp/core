package com.bidr.forge.dao.schema;

import com.bidr.forge.dao.entity.FormWideTableConfigAttr;
import com.bidr.kernel.mybatis.repository.BaseMybatisSchema;
import org.springframework.stereotype.Service;

/**
 * 宽表字段配置 Schema Service
 *
 * @author sharp
 */
@Service
public class FormWideTableConfigAttrSchema extends BaseMybatisSchema<FormWideTableConfigAttr> {
    static {
        setCreateDDL("CREATE TABLE IF NOT EXISTS `form_wide_table_config_attr` (\n" +
                "  `id` bigint(20) NOT NULL AUTO_INCREMENT COMMENT '主键 ID',\n" +
                "  `config_id` bigint(20) NOT NULL COMMENT '关联宽表配置 ID',\n" +
                "  `attribute_id` bigint(20) NOT NULL COMMENT '关联表单字段 ID',\n" +
                "  `column_name` varchar(100) NOT NULL COMMENT '宽表中的物理列名',\n" +
                "  `column_label` varchar(200) DEFAULT NULL COMMENT '宽表列显示名',\n" +
                "  `column_type` varchar(50) DEFAULT 'varchar(500)' COMMENT '列 SQL 类型',\n" +
                "  `is_dict` char(1) DEFAULT '0' COMMENT '是否字典字段: 1=是, 0=否',\n" +
                "  `dict_id` varchar(200) DEFAULT NULL COMMENT '字典 ID',\n" +
                "  `sort` int(11) DEFAULT '0' COMMENT '排序',\n" +
                "  `create_by` varchar(50) DEFAULT NULL COMMENT '创建者',\n" +
                "  `create_at` datetime(3) DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',\n" +
                "  `update_by` varchar(50) DEFAULT NULL COMMENT '更新者',\n" +
                "  `update_at` datetime(3) DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',\n" +
                "  `valid` char(1) DEFAULT '1' COMMENT '有效性',\n" +
                "  PRIMARY KEY (`id`) USING BTREE,\n" +
                "  KEY `idx_config_id` (`config_id`) USING BTREE,\n" +
                "  KEY `idx_attribute_id` (`attribute_id`) USING BTREE\n" +
                ") COMMENT='宽表字段配置';");
        setUpgradeDDL(1, "ALTER TABLE `form_wide_table_config_attr` MODIFY COLUMN `dict_id` varchar(200) DEFAULT NULL COMMENT '字典 ID';");
    }
}
