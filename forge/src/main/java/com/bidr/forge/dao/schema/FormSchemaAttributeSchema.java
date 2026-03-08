package com.bidr.forge.dao.schema;

import com.bidr.forge.dao.entity.FormSchemaAttribute;
import com.bidr.kernel.mybatis.repository.BaseMybatisSchema;
import org.springframework.stereotype.Service;

/**
 * 表单字段属性 Schema Service
 *
 * @author sharp
 */
@Service
public class FormSchemaAttributeSchema extends BaseMybatisSchema<FormSchemaAttribute> {
    static {
        setCreateDDL("CREATE TABLE IF NOT EXISTS `form_schema_attribute` (\n" +
                "  `id` bigint(20) NOT NULL AUTO_INCREMENT COMMENT 'id',\n" +
                "  `parent_attribute_id` bigint(20) DEFAULT NULL COMMENT '依赖属性 ID',\n" +
                "  `section_id` bigint(20) NOT NULL COMMENT '区块 id',\n" +
                "  `group_id` bigint(20) NOT NULL COMMENT '所属分组 id',\n" +
                "  `name` varchar(100) NOT NULL COMMENT '字段名',\n" +
                "  `label` varchar(100) NOT NULL COMMENT '显示标签',\n" +
                "  `label_width` int(11) DEFAULT NULL COMMENT '标签宽度',\n" +
                "  `description` varchar(50) DEFAULT NULL COMMENT '描述',\n" +
                "  `field_type` varchar(2) NOT NULL COMMENT '数据类型',\n" +
                "  `dict` varchar(200) DEFAULT NULL COMMENT '所属字典',\n" +
                "  `unit` varchar(10) DEFAULT NULL COMMENT '单位',\n" +
                "  `default_value` varchar(200) DEFAULT NULL COMMENT '默认值',\n" +
                "  `max_value` varchar(10) DEFAULT NULL COMMENT '最大值',\n" +
                "  `min_value` varchar(10) DEFAULT NULL COMMENT '最小值',\n" +
                "  `is_required` varchar(1) NOT NULL DEFAULT '0' COMMENT '是否必填',\n" +
                "  `readonly` varchar(1) NOT NULL DEFAULT '0' COMMENT '是否只读',\n" +
                "  `width` int(11) DEFAULT NULL COMMENT '宽',\n" +
                "  `height` int(11) DEFAULT NULL COMMENT '高',\n" +
                "  `position_x` int(11) DEFAULT NULL COMMENT '横轴坐标',\n" +
                "  `position_y` int(11) DEFAULT NULL COMMENT '纵轴坐标',\n" +
                "  `validation_rule` longtext COMMENT '正则表达式',\n" +
                "  `visibility_condition` longtext COMMENT '显示条件',\n" +
                "  `sort` int(11) DEFAULT NULL COMMENT '顺序',\n" +
                "  `create_by` varchar(50) DEFAULT NULL COMMENT '创建者',\n" +
                "  `create_at` datetime(3) DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',\n" +
                "  `update_by` varchar(50) DEFAULT NULL COMMENT '更新者',\n" +
                "  `update_at` datetime(3) DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',\n" +
                "  `valid` char(1) DEFAULT '1' COMMENT '有效性',\n" +
                "  PRIMARY KEY (`id`) USING BTREE,\n" +
                "  KEY `production_id` (`section_id`) USING BTREE,\n" +
                "  KEY `name` (`name`) USING BTREE,\n" +
                "  KEY `group_id` (`group_id`) USING BTREE\n" +
                ") COMMENT='表单字段属性';");
    }
}
