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
        setUpgradeDDL(1, "ALTER TABLE `ac_user_menu`\n" +
                "\tCHANGE COLUMN `user_id` `customer_number` VARCHAR(50) NOT NULL DEFAULT '' COMMENT '用户编码' FIRST,\n" +
                "\tDROP PRIMARY KEY,\n" +
                "\tADD PRIMARY KEY (`customer_number`, `menu_id`) USING BTREE,\n" +
                "\tDROP INDEX `user_id`,\n" +
                "\tADD INDEX `customer_number` (`customer_number`) USING BTREE;\n");
    }
}
