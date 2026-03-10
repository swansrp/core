package com.bidr.forge.dao.schema;

import com.bidr.forge.dao.entity.FormSchema;
import com.bidr.kernel.mybatis.repository.BaseMybatisSchema;
import org.springframework.stereotype.Service;

/**
 * 表单 Schema Service
 *
 * @author sharp
 */
@Service
public class FormSchemaSchema extends BaseMybatisSchema<FormSchema> {
    static {
        setCreateDDL("CREATE TABLE IF NOT EXISTS `form_schema` (\n" +
                "  `id` varchar(50) NOT NULL COMMENT 'id',\n" +
                "  `pid` varchar(50) DEFAULT NULL COMMENT '父 id',\n" +
                "  `code` varchar(50) NOT NULL COMMENT '编码',\n" +
                "  `title` varchar(50) NOT NULL COMMENT '名称',\n" +
                "  `description` varchar(50) DEFAULT NULL COMMENT '描述',\n" +
                "  `status` varchar(20) DEFAULT 'published' COMMENT '状态: draft/published/archived',\n" +
                "  `sort` int(11) NOT NULL DEFAULT '0' COMMENT '顺序',\n" +
                "  `create_by` varchar(50) DEFAULT NULL COMMENT '创建者',\n" +
                "  `create_at` datetime(3) DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',\n" +
                "  `update_by` varchar(50) DEFAULT NULL COMMENT '更新者',\n" +
                "  `update_at` datetime(3) DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',\n" +
                "  `valid` char(1) DEFAULT '1' COMMENT '有效性',\n" +
                "  PRIMARY KEY (`id`) USING BTREE,\n" +
                "  KEY `pid` (`pid`) USING BTREE\n" +
                "  KEY `code` (`code`) USING BTREE\n" +
                ") COMMENT='表单';");
    }
}
