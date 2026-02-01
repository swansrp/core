package com.bidr.authorization.dao.schema;

import com.bidr.authorization.dao.entity.AcGroupMenu;
import com.bidr.kernel.mybatis.repository.BaseMybatisSchema;
import org.springframework.stereotype.Service;

/**
 * 用户组和菜单关联表Schema
 *
 * @author sharp
 */
@Service
public class AcGroupMenuSchema extends BaseMybatisSchema<AcGroupMenu> {
    static {
        setCreateDDL("CREATE TABLE IF NOT EXISTS `ac_group_menu` (\n" +
                "  `group_id` bigint(20) NOT NULL COMMENT '用户组ID',\n" +
                "  `menu_id` bigint(20) NOT NULL COMMENT '菜单ID',\n" +
                "  PRIMARY KEY (`group_id`,`menu_id`),\n" +
                "  KEY `group_id` (`group_id`),\n" +
                "  KEY `menu_id` (`menu_id`)\n" +
                ") COMMENT='用户组和菜单关联表';");
    }
}
