package com.bidr.forge.dao.schema;

import com.bidr.forge.dao.entity.FormSchemaModule;
import com.bidr.kernel.mybatis.repository.BaseMybatisSchema;
import org.springframework.stereotype.Service;

/**
 * 表单模块 Schema Service
 *
 * @author sharp
 */
@Service
public class FormSchemaModuleSchema extends BaseMybatisSchema<FormSchemaModule> {
    static {
        setCreateDDL("CREATE TABLE IF NOT EXISTS `form_schema_module` (\n" +
                "  `id` bigint(20) NOT NULL AUTO_INCREMENT COMMENT 'id',\n" +
                "  `production_id` bigint(20) NOT NULL COMMENT '产品 id',\n" +
                "  `title` varchar(20) NOT NULL COMMENT '名称',\n" +
                "  `description` varchar(50) NOT NULL COMMENT '描述',\n" +
                "  `multi` varchar(20) NOT NULL COMMENT '是否多组数据',\n" +
                "  `sort` int(11) NOT NULL DEFAULT '0' COMMENT '顺序',\n" +
                "  `create_by` varchar(50) DEFAULT NULL COMMENT '创建者',\n" +
                "  `create_at` datetime(3) DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',\n" +
                "  `update_by` varchar(50) DEFAULT NULL COMMENT '更新者',\n" +
                "  `update_at` datetime(3) DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',\n" +
                "  `valid` char(1) DEFAULT '1' COMMENT '有效性',\n" +
                "  PRIMARY KEY (`id`) USING BTREE,\n" +
                "  KEY `production_id` (`production_id`) USING BTREE\n" +
                ") COMMENT='表单模块';");
    }
}
