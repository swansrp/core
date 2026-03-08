package com.bidr.forge.dao.schema;

import com.bidr.forge.dao.entity.FormDataHistory;
import com.bidr.kernel.mybatis.repository.BaseMybatisSchema;
import org.springframework.stereotype.Service;

/**
 * 表单填写历史 Schema Service
 *
 * @author sharp
 */
@Service
public class FormDataHistorySchema extends BaseMybatisSchema<FormDataHistory> {
    static {
        setCreateDDL("CREATE TABLE IF NOT EXISTS `form_data_history` (\n" +
                "  `id` varchar(50) NOT NULL COMMENT '上报历史 ID',\n" +
                "  `form_id` varchar(50) NOT NULL COMMENT '表单 ID',\n" +
                "  `version_no` varchar(50) DEFAULT NULL COMMENT '版本号',\n" +
                "  `status` varchar(2) DEFAULT NULL COMMENT '总体状态：0 草稿 1 提交 2 审核中 3 通过 4 退回',\n" +
                "  `remark` varchar(255) DEFAULT NULL COMMENT '备注',\n" +
                "  `submitted_by` varchar(50) DEFAULT NULL COMMENT '提交人',\n" +
                "  `submitted_at` datetime(3) DEFAULT CURRENT_TIMESTAMP(3) COMMENT '提交时间',\n" +
                "  `confirm_by` varchar(50) DEFAULT NULL COMMENT '审批人',\n" +
                "  `confirm_at` datetime(3) DEFAULT CURRENT_TIMESTAMP(3) COMMENT '审批时间',\n" +
                "  `confirm_reason` varchar(255) DEFAULT NULL COMMENT '审批理由',\n" +
                "  `create_by` varchar(50) DEFAULT NULL COMMENT '创建者',\n" +
                "  `create_at` datetime(3) DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',\n" +
                "  `update_by` varchar(50) DEFAULT NULL COMMENT '更新者',\n" +
                "  `update_at` datetime(3) DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',\n" +
                "  `valid` char(1) DEFAULT '1' COMMENT '有效性',\n" +
                "  PRIMARY KEY (`id`) USING BTREE,\n" +
                "  KEY `form_id` (`form_id`)\n" +
                ") COMMENT='表单填写历史';");
    }
}
