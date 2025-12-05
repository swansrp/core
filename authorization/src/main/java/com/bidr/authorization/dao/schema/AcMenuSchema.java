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

        setInitData("INSERT INTO `ac_menu` (`menu_id`, `pid`, `grand_id`, `key`, `ancestors`, `client_type`, `title`, `show_order`, `path`, `component`, `query`, `is_frame`, `is_cache`, `menu_type`, `visible`, `status`, `perms`, `icon`, `create_by`, `update_by`, `remark`) VALUES\n\t(1, NULL, NULL, 1, '#', '0', '系统管理', 0, 'SystemManage', 'SystemManage', '', '0', '0', 0, '1', '1', NULL, 'RightSquareOutlined', '000001', '000001', '系统管理目录');");
        setInitData("INSERT INTO `ac_menu` (`menu_id`, `pid`, `grand_id`, `key`, `ancestors`, `client_type`, `title`, `show_order`, `path`, `component`, `query`, `is_frame`, `is_cache`, `menu_type`, `visible`, `status`, `perms`, `icon`, `create_by`, `update_by`, `remark`) VALUES\n\t(2, NULL, 1, 2, '#', '0', '权限管理', 5, 'permit', '', '', '0', '0', 1, '1', '1', NULL, 'UsergroupAddOutlined', '000001', '000001', '');");
        setInitData("INSERT INTO `ac_menu` (`menu_id`, `pid`, `grand_id`, `key`, `ancestors`, `client_type`, `title`, `show_order`, `path`, `component`, `query`, `is_frame`, `is_cache`, `menu_type`, `visible`, `status`, `perms`, `icon`, `create_by`, `update_by`, `remark`) VALUES\n\t(3, 2, 1, 3, '#', '0', '菜单管理', 0, 'MenuMaintenance', '/framework/views/MainContent/SystemManage/PermissionMaintenance/MenuMaintenance', '', '0', '0', 1, '1', '1', NULL, 'ClusterOutlined', '000001', '000001', '');");
        setInitData("INSERT INTO `ac_menu` (`menu_id`, `pid`, `grand_id`, `key`, `ancestors`, `client_type`, `title`, `show_order`, `path`, `component`, `query`, `is_frame`, `is_cache`, `menu_type`, `visible`, `status`, `perms`, `icon`, `create_by`, `update_by`, `remark`) VALUES\n\t(4, 2, 1, 4, '#', '0', '人员-角色管理', 2, 'UserRoleMaintenance', '/framework/views/MainContent/SystemManage/PermissionMaintenance/UserRoleMaintenance', '', '0', '0', 1, '1', '1', NULL, 'ApiOutlined', '000001', '000001', '');");
        setInitData("INSERT INTO `ac_menu` (`menu_id`, `pid`, `grand_id`, `key`, `ancestors`, `client_type`, `title`, `show_order`, `path`, `component`, `query`, `is_frame`, `is_cache`, `menu_type`, `visible`, `status`, `perms`, `icon`, `create_by`, `update_by`, `remark`) VALUES\n\t(5, 2, 1, 5, '#', '0', '角色-权限管理', 1, 'RolePermissionMaintenance', '/framework/views/MainContent/SystemManage/PermissionMaintenance/RolePermissionMaintenance', '', '0', '0', 1, '1', '1', NULL, 'BlockOutlined', '000001', '000001', '');");
        setInitData("INSERT INTO `ac_menu` (`menu_id`, `pid`, `grand_id`, `key`, `ancestors`, `client_type`, `title`, `show_order`, `path`, `component`, `query`, `is_frame`, `is_cache`, `menu_type`, `visible`, `status`, `perms`, `icon`, `create_by`, `update_by`, `remark`) VALUES\n\t(6, NULL, 1, 6, '#', '0', '字典维护', 1, 'DictionaryMaintenance', '/framework/views/MainContent/Portal/dict', '', '0', '0', 1, '1', '1', NULL, 'ProfileOutlined', '000001', '000001', '');");
        setInitData("INSERT INTO `ac_menu` (`menu_id`, `pid`, `grand_id`, `key`, `ancestors`, `client_type`, `title`, `show_order`, `path`, `component`, `query`, `is_frame`, `is_cache`, `menu_type`, `visible`, `status`, `perms`, `icon`, `create_by`, `update_by`, `remark`) VALUES\n\t(7, NULL, 1, 7, '#', '0', '用户组管理', 4, 'UserGroupMaintenance', '/framework/views/MainContent/SystemManage/UserGroupMaintenance', '', '0', '0', 1, '1', '1', NULL, 'TeamOutlined', '000001', '000001', '');");
        setInitData("INSERT INTO `ac_menu` (`menu_id`, `pid`, `grand_id`, `key`, `ancestors`, `client_type`, `title`, `show_order`, `path`, `component`, `query`, `is_frame`, `is_cache`, `menu_type`, `visible`, `status`, `perms`, `icon`, `create_by`, `update_by`, `remark`) VALUES\n\t(8, NULL, 1, 8, '#', '0', '参数管理', 0, 'ParamsManage', '/framework/views/MainContent/Portal/parameter', '', '0', '0', 1, '1', '1', NULL, 'SettingOutlined', '000001', '000001', '');");
        setInitData("INSERT INTO `ac_menu` (`menu_id`, `pid`, `grand_id`, `key`, `ancestors`, `client_type`, `title`, `show_order`, `path`, `component`, `query`, `is_frame`, `is_cache`, `menu_type`, `visible`, `status`, `perms`, `icon`, `create_by`, `update_by`, `remark`) VALUES\n\t(9, NULL, 1, 9, '#', '0', '表格配置', 6, 'portalConfig', '/framework/views/MainContent/PortalConfig', '', '0', '0', 1, '1', '1', NULL, 'InsertRowAboveOutlined', '000001', '000001', '');");
        setInitData("INSERT INTO `ac_menu` (`menu_id`, `pid`, `grand_id`, `key`, `ancestors`, `client_type`, `title`, `show_order`, `path`, `component`, `query`, `is_frame`, `is_cache`, `menu_type`, `visible`, `status`, `perms`, `icon`, `create_by`, `update_by`, `remark`) VALUES\n\t(10, 2, 1, 10, '#', '0', '权限切换', 3, 'PermissionSwitch', '/framework/views/MainContent/SystemManage/PermissionMaintenance/PermissionSwitch', '', '0', '0', 1, '1', '1', NULL, 'UserSwitchOutlined', '000001', '000001', '');");
        setInitData("INSERT INTO `ac_menu` (`menu_id`, `pid`, `grand_id`, `key`, `ancestors`, `client_type`, `title`, `show_order`, `path`, `component`, `query`, `is_frame`, `is_cache`, `menu_type`, `visible`, `status`, `perms`, `icon`, `create_by`, `update_by`, `remark`) VALUES\n\t(12, NULL, 1, 12, '#', '0', '部门管理', 3, 'department', '/framework/views/MainContent/Portal/department', '', '0', '0', 1, '1', '1', NULL, 'BankOutlined', '000001', '000001', '');");
        setInitData("INSERT INTO `ac_menu` (`menu_id`, `pid`, `grand_id`, `key`, `ancestors`, `client_type`, `title`, `show_order`, `path`, `component`, `query`, `is_frame`, `is_cache`, `menu_type`, `visible`, `status`, `perms`, `icon`, `create_by`, `update_by`, `remark`) VALUES\n\t(13, NULL, 1, 13, '#', '0', '账号管理', 2, 'account', '/framework/views/MainContent/Portal/user', '', '0', '0', 1, '1', '1', NULL, 'UserOutlined', '000001', '000001', '');");
        setInitData("INSERT INTO `ac_menu` (`menu_id`, `pid`, `grand_id`, `key`, `ancestors`, `client_type`, `title`, `show_order`, `path`, `component`, `query`, `is_frame`, `is_cache`, `menu_type`, `visible`, `status`, `perms`, `icon`, `create_by`, `update_by`, `remark`) VALUES\n\t(26, NULL, 1, 26, '#', '0', '对象存储管理', 7, 'SaObjectStorage', '/framework/views/MainContent/Portal', '', '0', '0', 1, '1', '1', NULL, 'SettingOutlined', '000001', '000001', '');");
        setInitData("INSERT INTO `ac_menu` (`menu_id`, `pid`, `grand_id`, `key`, `ancestors`, `client_type`, `title`, `show_order`, `path`, `component`, `query`, `is_frame`, `is_cache`, `menu_type`, `visible`, `status`, `perms`, `icon`, `create_by`, `update_by`, `remark`) VALUES\n\t(27, NULL, 1, 27, '#', '0', '日志', 10, 'log', '/framework/views/MainContent/Log/index.vue', '', '0', '0', 1, '1', '1', NULL, 'SettingOutlined', '000001', '000001', '');");
    }
}
