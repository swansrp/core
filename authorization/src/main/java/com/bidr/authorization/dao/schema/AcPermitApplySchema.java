package com.bidr.authorization.dao.schema;

import com.bidr.authorization.dao.entity.AcPermitApply;
import com.bidr.kernel.mybatis.repository.BaseMybatisSchema;
import org.springframework.stereotype.Service;

/**
 * 权限申请表Schema
 *
 * @author sharp
 */
@Service
public class AcPermitApplySchema extends BaseMybatisSchema<AcPermitApply> {
    static {
        setCreateDDL("CREATE TABLE IF NOT EXISTS `ac_permit_apply` (\n" +
                "  `id` bigint(20) NOT NULL AUTO_INCREMENT COMMENT 'ID',\n" +
                "  `user_id` bigint(20) NOT NULL COMMENT '用户ID',\n" +
                "  `menu_id` bigint(20) NOT NULL COMMENT '菜单ID（权限ID）',\n" +
                "  `status` varchar(20) DEFAULT '0' COMMENT '审批状态（0-未提交，1-待审核，2-未通过，3-已通过）',\n" +
                "  `reason` varchar(500) DEFAULT NULL COMMENT '申请理由',\n" +
                "  `audit_remark` varchar(500) DEFAULT NULL COMMENT '审批意见',\n" +
                "  `audit_by` varchar(50) DEFAULT NULL COMMENT '审批人',\n" +
                "  `audit_at` datetime(3) DEFAULT NULL COMMENT '审批时间',\n" +
                "  `create_by` varchar(50) DEFAULT NULL COMMENT '创建者',\n" +
                "  `create_at` datetime(3) DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',\n" +
                "  `update_by` varchar(50) DEFAULT NULL COMMENT '更新者',\n" +
                "  `update_at` datetime(3) DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',\n" +
                "  `valid` char(1) DEFAULT '1' COMMENT '有效性',\n" +
                "  PRIMARY KEY (`id`),\n" +
                "  KEY `user_id` (`user_id`),\n" +
                "  KEY `menu_id` (`menu_id`)\n" +
                ") COMMENT='权限申请表';");
    }
}
