INSERT INTO `ac_user` (`user_id`,`customer_number`, `user_name`, `password`, `name`, `status`, `valid`) VALUES
 (1, '000001', 'BidrAdmin', '332e99b4633437a86e12331647468b350d9057728f48fd81', '系统管理员', '1', '1')
/
INSERT INTO `ac_menu` (`menu_id`, `pid`, `grand_id`, `key`, `ancestors`, `client_type`, `title`, `show_order`, `path`, `component`, `query`, `is_frame`, `is_cache`, `menu_type`, `visible`, `status`, `perms`, `icon`, `create_by`, `create_at`, `update_by`, `update_at`, `remark`) VALUES
	(1, NULL, NULL, 1, '#', '0', '系统管理', 0, 'SystemManage', 'SystemManage', '', '0', '0', 0, '1', '1', NULL, 'RightSquareOutlined', '1', '2023-12-14 21:36:01.207', '000001', '2024-01-15 09:35:23.418', '系统管理目录'),
	(2, NULL, 1, 2, '#', '0', '权限管理', 5, 'permit', '', '', '0', '0', 1, '1', '1', NULL, 'UsergroupAddOutlined', '1', '2023-12-14 21:36:01.207', '000001', '2024-03-06 14:17:46.508', ''),
	(3, 2, 1, 3, '#', '0', '菜单管理', 0, 'MenuMaintenance', '/framework/views/MainContent/SystemManage/PermissionMaintenance/MenuMaintenance', '', '0', '1', 1, '1', '1', NULL, 'ClusterOutlined', '1', '2023-12-14 21:36:01.207', '000001', '2024-03-06 15:34:33.739', ''),
	(4, 2, 1, 4, '#', '0', '人员-角色管理', 2, 'UserRoleMaintenance', '/framework/views/MainContent/SystemManage/PermissionMaintenance/UserRoleMaintenance', '', '0', '0', 1, '1', '1', NULL, 'ApiOutlined', '1', '2023-12-14 21:36:01.207', '000001', '2024-03-06 15:34:33.740', ''),
	(5, 2, 1, 5, '#', '0', '角色-权限管理', 1, 'RolePermissionMaintenance', '/framework/views/MainContent/SystemManage/PermissionMaintenance/RolePermissionMaintenance', '', '0', '0', 1, '1', '1', NULL, 'BlockOutlined', '1', '2023-12-14 21:36:01.207', '000001', '2024-03-06 15:34:33.740', ''),
	(6, NULL, 1, 6, '#', '0', '字典维护', 1, 'DictionaryMaintenance', '/framework/views/MainContent/Portal/dict', '', '0', '0', 1, '1', '1', NULL, 'ProfileOutlined', '1', '2023-12-14 21:36:01.207', '000001', '2024-03-06 14:17:39.532', ''),
	(7, NULL, 1, 7, '#', '0', '用户组管理', 4, 'UserGroupMaintenance', '/framework/views/MainContent/SystemManage/UserGroupMaintenance', '', '0', '0', 1, '1', '1', NULL, 'TeamOutlined', '1', '2023-12-14 21:36:01.207', '000001', '2024-03-06 14:17:46.508', ''),
	(8, NULL, 1, 8, '#', '0', '参数管理', 0, 'ParamsManage', '/framework/views/MainContent/Portal/parameter', '', '0', '0', 1, '1', '1', NULL, 'SettingOutlined', '1', '2023-12-14 21:36:01.207', '000001', '2024-02-26 13:39:51.456', ''),
	(9, NULL, 1, 9, '#', '0', '表格配置', 6, 'portalConfig', '/framework/views/MainContent/PortalConfig', '', '0', '0', 1, '1', '1', NULL, 'InsertRowAboveOutlined', '000001', '2023-12-28 20:45:14.901', '000001', '2024-03-06 14:17:44.652', ''),
	(10, 2, 1, 10, '#', '0', '权限切换', 3, 'PermissionSwitch', '/framework/views/MainContent/SystemManage/PermissionMaintenance/PermissionSwitch', '', '0', '0', 1, '1', '1', NULL, 'UserSwitchOutlined', '000001', '2023-12-28 20:47:53.465', '000001', '2024-03-06 15:34:33.740', ''),
	(12, NULL, 1, 12, '#', '0', '部门管理', 3, 'department', '/framework/views/MainContent/Portal/department', '', '0', '0', 1, '1', '1', NULL, 'BankOutlined', '000001', '2023-12-29 09:58:35.956', '000001', '2024-03-06 14:17:39.532', ''),
	(13, NULL, 1, 13, '#', '0', '账号管理', 2, 'account', '/framework/views/MainContent/Portal/user', '', '0', '0', 1, '1', '1', NULL, 'UserOutlined', '000001', '2023-12-29 09:58:59.868', '000001', '2024-03-06 14:17:39.532', ''),
	(26, NULL, 1, 26, '#', '0', '对象存储管理', 7, 'SaObjectStorage', '/framework/views/MainContent/Portal', '', '0', '0', 1, '1', '1', NULL, 'SettingOutlined', '000001', '2024-02-26 13:39:47.329', '000001', '2024-03-06 14:17:37.987', '')
/
INSERT INTO `ac_role` (`role_id`, `role_name`, `role_key`, `status`, `display_order`, `create_by`, `update_by`, `remark`, `valid`) VALUES
 (1, '超级管理员', 'admin', 1, 1, '1', '1', '超级管理员', '1'),
 (2, '普通角色', 'common', 1, 2, '1', '1', '普通角色', '1')
/
INSERT INTO `ac_user_role` (`user_id`, `role_id`) VALUES (1, 1)
/
INSERT INTO `ac_role_menu` (`role_id`, `menu_id`) VALUES 
 (1, 1),
 (1, 2),
 (1, 3),
 (1, 4),
 (1, 5),
 (1, 6),
 (1, 7),
 (1, 8),
 (1, 9),
 (1, 10)
/
