package com.bidr.forge.dao.schema;

import com.bidr.forge.dao.entity.FormData;
import com.bidr.kernel.mybatis.repository.BaseMybatisSchema;
import org.springframework.stereotype.Service;

/**
 * 表单填写数据表 Schema Service
 *
 * @author sharp
 */
@Service
public class FormDataSchema extends BaseMybatisSchema<FormData> {
    static {
        setCreateDDL("CREATE TABLE IF NOT EXISTS `form_data` (\n" +
                "  `id` varchar(50) NOT NULL COMMENT '主键 ID',\n" +
                "  `history_id` varchar(50) NOT NULL COMMENT '上传历史 ID',\n" +
                "  `section_instance_id` varchar(64) NOT NULL COMMENT '区块实例 ID',\n" +
                "  `group_instance_id` varchar(64) NOT NULL COMMENT '组实例 ID',\n" +
                "  `attribute_id` bigint(20) NOT NULL COMMENT '字段 ID',\n" +
                "  `value` longtext COMMENT '企业填写的值',\n" +
                "  `version` int(11) DEFAULT '1' COMMENT '表单版本号',\n" +
                "  `status` tinyint(4) DEFAULT '1' COMMENT '状态：1=有效，0=无效',\n" +
                "  `create_by` varchar(50) DEFAULT NULL COMMENT '创建者',\n" +
                "  `create_at` datetime(3) DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',\n" +
                "  `update_by` varchar(50) DEFAULT NULL COMMENT '更新者',\n" +
                "  `update_at` datetime(3) DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',\n" +
                "  `valid` char(1) DEFAULT '1' COMMENT '有效性',\n" +
                "  PRIMARY KEY (`id`) USING BTREE,\n" +
                "  KEY `idx_attribute` (`attribute_id`) USING BTREE,\n" +
                "  KEY `history_id` (`history_id`) USING BTREE,\n" +
                "  KEY `group_instance_id` (`group_instance_id`) USING BTREE,\n" +
                "  KEY `idx_instance` (`section_instance_id`) USING BTREE\n" +
                ") COMMENT='表单填写数据表';");
    }
}
