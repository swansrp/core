-- 添加默认菜单
-- 沙若鹏

INSERT INTO `ac_user` (`user_id`,`customer_number`, `user_name`, `password`, `name`, `status`, `valid`) VALUES
 (1, '000001', 'admin', '332e99b4633437a86e12331647468b350d9057728f48fd81', '系统管理员', '1', '1')
/
INSERT INTO `ac_menu` (`menu_id`, `pid`, `grand_id`, `key`, `ancestors`, `client_type`, `title`, `show_order`, `path`, `component`, `query`, `is_frame`, `is_cache`, `menu_type`, `visible`, `status`, `perms`, `icon`, `create_by`, `update_by`, `remark`) VALUES
	(1, NULL, NULL, 1, '#', '0', '系统管理', 2, 'SystemManage', 'SystemManage', '', '0', '0', 0, '1', '1', NULL, 'RightSquareOutlined', '1', '1', '系统管理目录'),
	(2, NULL, 1, 2, '#', '0', '权限管理', 1, '', '', '', '0', '0', 1, '1', '1', NULL, 'UsergroupAddOutlined', '1', '1', ''),
	(3, 2, 1, 3, '#', '0', '菜单管理', 0, 'MenuMaintenance', 'SystemManage/PermissionMaintenance/MenuMaintenance', '', '0', '1', 1, '1', '1', NULL, 'ClusterOutlined', '1', '1', ''),
	(4, 2, 1, 4, '#', '0', '人员-角色管理', 2, 'UserRoleMaintenance', 'SystemManage/PermissionMaintenance/UserRoleMaintenance', '', '0', '0', 1, '1', '1', NULL, 'ApiOutlined', '1', '1', ''),
	(5, 2, 1, 5, '#', '0', '角色-权限管理', 1, 'RolePermissionMaintenance', 'SystemManage/PermissionMaintenance/RolePermissionMaintenance', '', '0', '0', 1, '1', '1', NULL, 'BlockOutlined', '1', '1', ''),
	(6, NULL, 1, 6, '#', '0', '字典维护', 0, 'DictionaryMaintenance', 'SystemManage/DictionaryMaintenance', '', '0', '0', 1, '1', '1', NULL, 'ProfileOutlined', '1', '1', ''),
    (7, NULL, 1, 7, '#', '0', '用户组管理', 0, 'UserGroupMaintenance', 'SystemManage/UserGroupMaintenance', '', '0', '0', 1, '1', '1', NULL, 'TeamOutlined', '1', '1', '')

/
INSERT INTO `sequence` (`seq_name`, `value`, `prefix`, `suffix`, `min_value`, `max_value`, `step`) VALUES
	('AC_USER_CUSTOMER_NUMBER_SEQ', 0, '', '', 100000000, 999999999, 1)
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
	(1, 7)
/	


