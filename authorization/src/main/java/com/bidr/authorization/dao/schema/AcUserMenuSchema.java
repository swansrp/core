package com.bidr.authorization.dao.schema;

import com.bidr.authorization.dao.entity.AcUserMenu;
import com.bidr.kernel.mybatis.repository.BaseMybatisSchema;
import org.springframework.stereotype.Service;

/**
 * 用户和菜单关联表Schema
 *
 * @author sharp
 */
@Service
public class AcUserMenuSchema extends BaseMybatisSchema<AcUserMenu> {
    static {
        setCreateDDL("CREATE TABLE IF NOT EXISTS `ac_user_menu` (\n" +
                "  `user_id` bigint(20) NOT NULL COMMENT '用户ID',\n" +
                "  `menu_id` bigint(20) NOT NULL COMMENT '菜单ID',\n" +
                "  PRIMARY KEY (`user_id`,`menu_id`),\n" +
                "  KEY `user_id` (`user_id`),\n" +
                "  KEY `menu_id` (`menu_id`)\n" +
                ") COMMENT='用户和菜单关联表';");
    }
}
