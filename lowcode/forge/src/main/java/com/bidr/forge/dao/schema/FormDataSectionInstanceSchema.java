package com.bidr.forge.dao.schema;

import com.bidr.forge.dao.entity.FormDataSectionInstance;
import com.bidr.kernel.mybatis.repository.BaseMybatisSchema;
import org.springframework.stereotype.Service;

/**
 * 表单区块实例 Schema Service
 *
 * @author sharp
 */
@Service
public class FormDataSectionInstanceSchema extends BaseMybatisSchema<FormDataSectionInstance> {
    static {
        setCreateDDL("CREATE TABLE IF NOT EXISTS `form_data_section_instance` (\n" +
                "  `id` varchar(50) NOT NULL COMMENT '表单实例 ID',\n" +
                "  `history_id` varchar(50) NOT NULL DEFAULT '' COMMENT '上报历史 id',\n" +
                "  `section_id` bigint(20) NOT NULL COMMENT '区块 id',\n" +
                "  `version_no` int(11) DEFAULT '1' COMMENT '版本号',\n" +
                "  `remark` varchar(255) DEFAULT NULL COMMENT '备注/说明',\n" +
                "  `form_content` json DEFAULT NULL COMMENT '上报内容 JSON',\n" +
                "  `submitted_by` varchar(50) DEFAULT NULL COMMENT '提交人',\n" +
                "  `submitted_at` datetime(3) DEFAULT CURRENT_TIMESTAMP(3) COMMENT '提交时间',\n" +
                "  `confirm_by` varchar(50) DEFAULT NULL COMMENT '审核人',\n" +
                "  `confirm_at` datetime DEFAULT NULL COMMENT '审核时间',\n" +
                "  `confirm_status` varchar(1) NOT NULL DEFAULT '0' COMMENT '状态：1=已提交，0=草稿，2=审核中，3=退回',\n" +
                "  `confirm_comment` varchar(500) DEFAULT NULL COMMENT '审核意见',\n" +
                "  `create_by` varchar(50) DEFAULT NULL COMMENT '创建者',\n" +
                "  `create_at` datetime(3) DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',\n" +
                "  `update_by` varchar(50) DEFAULT NULL COMMENT '更新者',\n" +
                "  `update_at` datetime(3) DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',\n" +
                "  PRIMARY KEY (`id`) USING BTREE,\n" +
                "  KEY `history_id` (`history_id`) USING BTREE,\n" +
                "  KEY `section_id` (`section_id`) USING BTREE\n" +
                ") COMMENT='表单区块实例';");
    }
}
