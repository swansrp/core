package com.bidr.authorization.dao.schema;

import com.bidr.authorization.dao.entity.AcRoleMenu;
import com.bidr.kernel.mybatis.repository.BaseMybatisSchema;
import org.springframework.stereotype.Service;

/**
 * Title: AcRoleMenuSchema
 *
 * @author Sharp
 * @since 2025/11/28
 */

@Service
public class AcRoleMenuSchema extends BaseMybatisSchema<AcRoleMenu> {
    static {
        setCreateDDL("CREATE TABLE IF NOT EXISTS `ac_role_menu` (\n" +
                "  `role_id` bigint(20) NOT NULL COMMENT '角色ID',\n" +
                "  `menu_id` bigint(20) NOT NULL COMMENT '菜单ID',\n" +
                "  PRIMARY KEY (`role_id`,`menu_id`)\n" +
                ") COMMENT='角色和菜单关联表';");

        setInitData("INSERT INTO `ac_role_menu` (`role_id`, `menu_id`) VALUES\n\t(1, 1);");
        setInitData("INSERT INTO `ac_role_menu` (`role_id`, `menu_id`) VALUES\n\t(1, 2);");
        setInitData("INSERT INTO `ac_role_menu` (`role_id`, `menu_id`) VALUES\n\t(1, 3);");
        setInitData("INSERT INTO `ac_role_menu` (`role_id`, `menu_id`) VALUES\n\t(1, 4);");
        setInitData("INSERT INTO `ac_role_menu` (`role_id`, `menu_id`) VALUES\n\t(1, 5);");
        setInitData("INSERT INTO `ac_role_menu` (`role_id`, `menu_id`) VALUES\n\t(1, 6);");
        setInitData("INSERT INTO `ac_role_menu` (`role_id`, `menu_id`) VALUES\n\t(1, 7);");
        setInitData("INSERT INTO `ac_role_menu` (`role_id`, `menu_id`) VALUES\n\t(1, 8);");
        setInitData("INSERT INTO `ac_role_menu` (`role_id`, `menu_id`) VALUES\n\t(1, 9);");
        setInitData("INSERT INTO `ac_role_menu` (`role_id`, `menu_id`) VALUES\n\t(1, 10);");
        setInitData("INSERT INTO `ac_role_menu` (`role_id`, `menu_id`) VALUES\n\t(1, 26);");
        setInitData("INSERT INTO `ac_role_menu` (`role_id`, `menu_id`) VALUES\n\t(1, 27);");
    }
}
