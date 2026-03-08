package com.bidr.forge.dao.schema;

import com.bidr.forge.dao.entity.FormDataGroupInstance;
import com.bidr.kernel.mybatis.repository.BaseMybatisSchema;
import org.springframework.stereotype.Service;

/**
 * 属性分组实例表 Schema Service
 *
 * @author sharp
 */
@Service
public class FormDataGroupInstanceSchema extends BaseMybatisSchema<FormDataGroupInstance> {
    static {
        setCreateDDL("CREATE TABLE IF NOT EXISTS `form_data_group_instance` (\n" +
                "  `id` varchar(64) NOT NULL COMMENT '主键 ID',\n" +
                "  `history_id` varchar(50) NOT NULL COMMENT '上传历史 ID',\n" +
                "  `section_instance_id` varchar(50) NOT NULL COMMENT '区块实例 ID',\n" +
                "  `group_id` bigint(20) NOT NULL COMMENT '分组配置 ID',\n" +
                "  `row_index` int(11) DEFAULT '0' COMMENT '行索引（多组子表场景）',\n" +
                "  `create_by` varchar(50) DEFAULT NULL COMMENT '创建者',\n" +
                "  `create_at` datetime(3) DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',\n" +
                "  `update_by` varchar(50) DEFAULT NULL COMMENT '更新者',\n" +
                "  `update_at` datetime(3) DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',\n" +
                "  PRIMARY KEY (`id`) USING BTREE,\n" +
                "  KEY `idx_section_instance` (`section_instance_id`) USING BTREE,\n" +
                "  KEY `idx_group` (`group_id`) USING BTREE,\n" +
                "  KEY `idx_history` (`history_id`) USING BTREE\n" +
                ") COMMENT='属性分组实例表';");
    }
}
