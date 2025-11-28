package com.bidr.authorization.dao.schema;

import com.bidr.authorization.dao.entity.AcRole;
import com.bidr.kernel.mybatis.repository.BaseMybatisSchema;
import org.springframework.stereotype.Service;


/**
 * Title: AcRoleSchema
 *
 * @author Sharp
 * @since 2025/11/28
 */
@Service
public class AcRoleSchema extends BaseMybatisSchema<AcRole> {
    static {
        setCreateDDL("CREATE TABLE IF NOT EXISTS `ac_role` (\n" +
                "  `role_id` bigint(20) NOT NULL AUTO_INCREMENT COMMENT '角色ID',\n" +
                "  `role_name` varchar(30) NOT NULL COMMENT '角色名称',\n" +
                "  `role_key` varchar(100) DEFAULT NULL COMMENT '角色权限字符串',\n" +
                "  `status` int(11) NOT NULL DEFAULT '0' COMMENT '角色状态（1正常 0停用）',\n" +
                "  `display_order` int(11) DEFAULT NULL COMMENT '显示顺序',\n" +
                "  `create_by` bigint(20) DEFAULT NULL COMMENT '创建者',\n" +
                "  `create_at` datetime(3) DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',\n" +
                "  `update_by` bigint(20) DEFAULT NULL COMMENT '更新者',\n" +
                "  `update_at` datetime(3) DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',\n" +
                "  `remark` varchar(500) DEFAULT NULL COMMENT '备注',\n" +
                "  `valid` char(1) DEFAULT '1' COMMENT '有效性',\n" +
                "  PRIMARY KEY (`role_id`)\n" +
                ") COMMENT='角色信息表';");

        setInitData("INSERT INTO `ac_role` (`role_id`, `role_name`, `role_key`, `status`, `display_order`, `create_by`, `update_by`, `remark`, `valid`) VALUES\n\t(1, '超级管理员', 'admin', 1, 1, 1, 1, '超级管理员', '1');");
        setInitData("INSERT INTO `ac_role` (`role_id`, `role_name`, `role_key`, `status`, `display_order`, `create_by`, `update_by`, `remark`, `valid`) VALUES\n\t(2, '普通角色', 'common', 1, 2, 1, 1, '普通角色', '1');");
    }
}
