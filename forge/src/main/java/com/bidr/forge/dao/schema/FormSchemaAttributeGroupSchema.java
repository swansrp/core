package com.bidr.forge.dao.schema;

import com.bidr.forge.dao.entity.FormSchemaAttributeGroup;
import com.bidr.kernel.mybatis.repository.BaseMybatisSchema;
import org.springframework.stereotype.Service;

/**
 * 表单字段分组 Schema Service
 *
 * @author sharp
 */
@Service
public class FormSchemaAttributeGroupSchema extends BaseMybatisSchema<FormSchemaAttributeGroup> {
    static {
        setCreateDDL("CREATE TABLE IF NOT EXISTS `form_schema_attribute_group` (\n" +
                "  `id` bigint(20) NOT NULL AUTO_INCREMENT COMMENT '主键 ID',\n" +
                "  `pid` bigint(20) DEFAULT NULL COMMENT '父 ID',\n" +
                "  `section_id` bigint(20) NOT NULL COMMENT '所属区块 ID',\n" +
                "  `title` varchar(200) NOT NULL COMMENT '分组标题',\n" +
                "  `description` varchar(500) DEFAULT NULL COMMENT '分组描述',\n" +
                "  `multi` char(1) DEFAULT '0' COMMENT '是否支持多行：0=单组，1=多组子表',\n" +
                "  `required` varchar(50) DEFAULT NULL COMMENT '是否必填',\n" +
                "  `sort` int(11) DEFAULT '1' COMMENT '排序号',\n" +
                "  `create_by` varchar(50) DEFAULT NULL COMMENT '创建者',\n" +
                "  `create_at` datetime(3) DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',\n" +
                "  `update_by` varchar(50) DEFAULT NULL COMMENT '更新者',\n" +
                "  `update_at` datetime(3) DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',\n" +
                "  `valid` char(1) DEFAULT '1' COMMENT '有效性',\n" +
                "  PRIMARY KEY (`id`) USING BTREE,\n" +
                "  KEY `idx_section_id` (`section_id`) USING BTREE,\n" +
                "  KEY `pid` (`pid`) USING BTREE\n" +
                ") COMMENT='表单字段分组';");
    }
}
