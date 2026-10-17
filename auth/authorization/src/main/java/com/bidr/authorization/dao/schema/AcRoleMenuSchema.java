package com.bidr.authorization.dao.schema;

import com.bidr.authorization.dao.entity.AcRoleMenu;
import com.bidr.kernel.mybatis.repository.BaseMybatisSchema;
import org.springframework.stereotype.Service;

/**
 * Title: AcRoleMenuSchema
 *
 * 角色绑定不在此播种：menu_id 是 ac_menu 的自增主键，硬编码跨表引用会在全新装机时
 * 与 MenuSeeder 新插菜单撞 (role_id,menu_id) 唯一键（启动失败退出码 0，无限重启）。
 * 绑定由 MenuSeeder 对自己新插的菜单行自动完成。
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
    }
}
