package com.bidr.authorization.dao.schema;

import com.bidr.authorization.dao.entity.AcMenu;
import com.bidr.kernel.mybatis.repository.BaseMybatisSchema;
import org.springframework.stereotype.Service;


/**
 * Title: AcMenuSchema
 *
 * @author Sharp
 * @since 2025/11/28
 */
@Service
public class AcMenuSchema extends BaseMybatisSchema<AcMenu> {
    static {
        setCreateDDL("CREATE TABLE IF NOT EXISTS `ac_menu` (\n" +
                "  `menu_id` bigint(20) NOT NULL AUTO_INCREMENT COMMENT '菜单ID',\n" +
                "  `pid` bigint(20) DEFAULT NULL COMMENT '父菜单ID',\n" +
                "  `grand_id` bigint(20) DEFAULT NULL COMMENT '祖父ID',\n" +
                "  `key` bigint(20) DEFAULT NULL COMMENT 'key与菜单ID一致',\n" +
                "  `ancestors` varchar(50) NOT NULL DEFAULT '#' COMMENT '祖级列表',\n" +
                "  `client_type` varchar(2) NOT NULL DEFAULT '0' COMMENT '客户端类型',\n" +
                "  `title` varchar(50) NOT NULL COMMENT '菜单名称',\n" +
                "  `show_order` int(11) DEFAULT '0' COMMENT '显示顺序',\n" +
                "  `path` varchar(200) DEFAULT '' COMMENT '路由地址',\n" +
                "  `component` varchar(255) DEFAULT NULL COMMENT '组件路径',\n" +
                "  `query` varchar(255) DEFAULT NULL COMMENT '路由参数',\n" +
                "  `is_frame` char(50) DEFAULT NULL COMMENT '是否为外链（0是 1否）',\n" +
                "  `is_cache` char(50) DEFAULT NULL COMMENT '是否缓存（0缓存 1不缓存）',\n" +
                "  `menu_type` int(11) DEFAULT NULL COMMENT '菜单类型MENU_TYPE_DICT',\n" +
                "  `visible` char(1) DEFAULT '0' COMMENT '菜单状态（0显示 1隐藏）',\n" +
                "  `status` char(1) DEFAULT '0' COMMENT '菜单状态（0正常 1停用）',\n" +
                "  `perms` varchar(100) DEFAULT NULL COMMENT '权限标识',\n" +
                "  `icon` varchar(100) DEFAULT '#' COMMENT '菜单图标',\n" +
                "  `create_by` varchar(50) DEFAULT NULL COMMENT '创建者',\n" +
                "  `create_at` datetime(3) DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',\n" +
                "  `update_by` varchar(50) DEFAULT NULL COMMENT '更新者',\n" +
                "  `update_at` datetime(3) DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',\n" +
                "  `remark` varchar(500) DEFAULT '' COMMENT '备注',\n" +
                "  PRIMARY KEY (`menu_id`) USING BTREE,\n" +
                "  KEY `pid` (`pid`),\n" +
                "  KEY `grand_id` (`grand_id`),\n" +
                "  KEY `key` (`key`)\n" +
                ") COMMENT='菜单权限表';");

        // 默认菜单行已迁至本模块 resources/menu-seed.yml（MenuSeeder 启动期幂等播种）；
        // initData 只在建表当次执行，已上线库不受影响——新装机出默认菜单只走 yml 一条正源。
    }
}
