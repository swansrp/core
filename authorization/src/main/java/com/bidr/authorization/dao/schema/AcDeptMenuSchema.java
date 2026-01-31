package com.bidr.authorization.dao.schema;

import com.bidr.authorization.dao.entity.AcDeptMenu;
import com.bidr.kernel.mybatis.repository.BaseMybatisSchema;
import org.springframework.stereotype.Service;

/**
 * 部门和菜单关联表Schema
 *
 * @author sharp
 */
@Service
public class AcDeptMenuSchema extends BaseMybatisSchema<AcDeptMenu> {
    static {
        setCreateDDL("CREATE TABLE IF NOT EXISTS `ac_dept_menu` (\n" +
                "  `dept_id` bigint(20) NOT NULL COMMENT '部门ID',\n" +
                "  `menu_id` bigint(20) NOT NULL COMMENT '菜单ID',\n" +
                "  PRIMARY KEY (`dept_id`,`menu_id`),\n" +
                "  KEY `dept_id` (`dept_id`),\n" +
                "  KEY `menu_id` (`menu_id`)\n" +
                ") COMMENT='部门和菜单关联表';");
    }
}
