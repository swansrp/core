package com.bidr.forge.dao.schema;

import com.bidr.forge.dao.entity.FormWideTableConfig;
import com.bidr.kernel.mybatis.repository.BaseMybatisSchema;
import org.springframework.stereotype.Service;

/**
 * 宽表收集配置 Schema Service
 *
 * @author sharp
 */
@Service
public class FormWideTableConfigSchema extends BaseMybatisSchema<FormWideTableConfig> {
    static {
        setCreateDDL("CREATE TABLE IF NOT EXISTS `form_wide_table_config` (\n" +
                "  `id` bigint(20) NOT NULL AUTO_INCREMENT COMMENT '主键 ID',\n" +
                "  `form_id` varchar(50) NOT NULL COMMENT '关联表单 ID',\n" +
                "  `table_name` varchar(100) NOT NULL COMMENT '物理宽表名',\n" +
                "  `title` varchar(200) DEFAULT NULL COMMENT '配置名称',\n" +
                "  `description` text COMMENT '描述',\n" +
                "  `status` varchar(20) DEFAULT 'draft' COMMENT '状态: draft/active/inactive',\n" +
                "  `portal_id` bigint(20) DEFAULT NULL COMMENT '关联 Portal 表 ID',\n" +
                "  `create_by` varchar(50) DEFAULT NULL COMMENT '创建者',\n" +
                "  `create_at` datetime(3) DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',\n" +
                "  `update_by` varchar(50) DEFAULT NULL COMMENT '更新者',\n" +
                "  `update_at` datetime(3) DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',\n" +
                "  `valid` char(1) DEFAULT '1' COMMENT '有效性',\n" +
                "  PRIMARY KEY (`id`) USING BTREE,\n" +
                "  KEY `idx_form_id` (`form_id`) USING BTREE,\n" +
                "  KEY `idx_status` (`status`) USING BTREE\n" +
                ") COMMENT='宽表收集配置';");
    }
}
