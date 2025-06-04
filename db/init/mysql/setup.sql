-- 导出  表 aicxtek.ac_account 结构
DROP TABLE IF EXISTS `ac_account`;
CREATE TABLE IF NOT EXISTS `ac_account` (
  `id` varchar(50) NOT NULL COMMENT 'id',
  `name` varchar(50) NOT NULL COMMENT '姓名',
  `gender` varchar(50) DEFAULT NULL COMMENT '人员性别',
  `nationality` varchar(50) DEFAULT NULL COMMENT '民族',
  `native_place` varchar(50) DEFAULT NULL COMMENT '籍贯',
  `political_outlook` varchar(50) DEFAULT NULL COMMENT '政治面貌',
  `work_date` datetime DEFAULT NULL COMMENT '参加工作日期',
  `id_number` varchar(50) DEFAULT NULL COMMENT '身份证号',
  `profession` varchar(50) DEFAULT NULL COMMENT '专业技术职务',
  `talent` varchar(50) DEFAULT NULL COMMENT '公司人才工程名称',
  `email` varchar(50) DEFAULT NULL COMMENT '人员电子邮件',
  `mobile` varchar(50) DEFAULT NULL COMMENT '人员手机',
  `category` varchar(50) DEFAULT NULL COMMENT '人员类别',
  `department` varchar(50) DEFAULT NULL COMMENT '人员所属部门',
  `org` varchar(50) DEFAULT NULL COMMENT '人员所属组织',
  `user_name` varchar(50) NOT NULL COMMENT '用户名',
  `picture_link` varchar(500) DEFAULT NULL COMMENT '人员照片链接',
  `signature_link` varchar(500) DEFAULT NULL COMMENT '人员电子签名链接',
  `employ_status` varchar(50) NOT NULL COMMENT '在职状态',
  `status` int(11) NOT NULL COMMENT '人员启用状态',
  `create_at` datetime(3) DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
  PRIMARY KEY (`id`),
  KEY `mobile` (`mobile`),
  KEY `user_name` (`user_name`),
  KEY `department` (`department`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户表';

-- 正在导出表  aicxtek.ac_account 的数据：~0 rows (大约)
DELETE FROM `ac_account`;

-- 导出  表 aicxtek.ac_dept 结构
DROP TABLE IF EXISTS `ac_dept`;
CREATE TABLE IF NOT EXISTS `ac_dept` (
  `dept_id` varchar(20) NOT NULL COMMENT '部门id',
  `pid` varchar(20) DEFAULT NULL COMMENT '父部门id',
  `grand_id` varchar(20) DEFAULT NULL COMMENT '祖父id',
  `ancestors` varchar(50) DEFAULT '' COMMENT '祖级列表',
  `name` varchar(30) DEFAULT '' COMMENT '部门名称',
  `abbreviate` varchar(50) DEFAULT NULL COMMENT '简称',
  `founded_time` datetime DEFAULT NULL COMMENT '建立时间',
  `category` varchar(20) DEFAULT NULL COMMENT '类别',
  `type` varchar(20) DEFAULT NULL COMMENT '类型',
  `function` varchar(20) DEFAULT NULL COMMENT '职能',
  `leader` varchar(20) DEFAULT NULL COMMENT '负责人',
  `contact` varchar(11) DEFAULT NULL COMMENT '联系电话',
  `address` varchar(50) DEFAULT NULL COMMENT '地址',
  `status` int(11) DEFAULT NULL COMMENT '部门状态',
  `show_order` int(11) DEFAULT '0' COMMENT '显示顺序',
  `create_by` varchar(50) DEFAULT NULL COMMENT '创建者',
  `create_at` datetime(3) DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
  `update_by` varchar(50) DEFAULT NULL COMMENT '更新者',
  `update_at` datetime(3) DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
  `valid` char(1) DEFAULT '1' COMMENT '有效性',
  PRIMARY KEY (`dept_id`),
  KEY `pid` (`pid`),
  KEY `grand_id` (`grand_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='部门表';

-- 正在导出表  aicxtek.ac_dept 的数据：~0 rows (大约)
DELETE FROM `ac_dept`;

-- 导出  表 aicxtek.ac_group 结构
DROP TABLE IF EXISTS `ac_group`;
CREATE TABLE IF NOT EXISTS `ac_group` (
  `id` bigint(20) unsigned NOT NULL AUTO_INCREMENT COMMENT 'id',
  `pid` bigint(20) DEFAULT NULL COMMENT '父id',
  `key` bigint(20) NOT NULL DEFAULT '0' COMMENT 'key',
  `type` varchar(50) NOT NULL COMMENT '组类型',
  `name` varchar(50) NOT NULL COMMENT '组群名',
  `display_order` int(11) NOT NULL DEFAULT '0' COMMENT '显示顺序',
  PRIMARY KEY (`id`),
  KEY `type` (`type`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户逻辑组群';

-- 正在导出表  aicxtek.ac_group 的数据：~0 rows (大约)
DELETE FROM `ac_group`;

-- 导出  表 aicxtek.ac_group_type 结构
DROP TABLE IF EXISTS `ac_group_type`;
CREATE TABLE IF NOT EXISTS `ac_group_type` (
  `id` varchar(50) NOT NULL DEFAULT '' COMMENT '用户组类别id',
  `name` varchar(50) NOT NULL COMMENT '用户组类别名称',
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='组类型';

-- 正在导出表  aicxtek.ac_group_type 的数据：~0 rows (大约)
DELETE FROM `ac_group_type`;

-- 导出  表 aicxtek.ac_menu 结构
DROP TABLE IF EXISTS `ac_menu`;
CREATE TABLE IF NOT EXISTS `ac_menu` (
  `menu_id` bigint(20) NOT NULL AUTO_INCREMENT COMMENT '菜单ID',
  `pid` bigint(20) DEFAULT NULL COMMENT '父菜单ID',
  `grand_id` bigint(20) DEFAULT NULL COMMENT '祖父ID',
  `key` bigint(20) DEFAULT NULL COMMENT 'key与菜单ID一致',
  `ancestors` varchar(50) NOT NULL DEFAULT '#' COMMENT '祖级列表',
  `client_type` varchar(2) NOT NULL DEFAULT '0' COMMENT '客户端类型',
  `title` varchar(50) NOT NULL COMMENT '菜单名称',
  `show_order` int(11) DEFAULT '0' COMMENT '显示顺序',
  `path` varchar(200) DEFAULT '' COMMENT '路由地址',
  `component` varchar(255) DEFAULT NULL COMMENT '组件路径',
  `query` varchar(255) DEFAULT NULL COMMENT '路由参数',
  `is_frame` char(50) DEFAULT NULL COMMENT '是否为外链（0是 1否）',
  `is_cache` char(50) DEFAULT NULL COMMENT '是否缓存（0缓存 1不缓存）',
  `menu_type` int(11) DEFAULT NULL COMMENT '菜单类型MENU_TYPE_DICT',
  `visible` char(1) DEFAULT '0' COMMENT '菜单状态（0显示 1隐藏）',
  `status` char(1) DEFAULT '0' COMMENT '菜单状态（0正常 1停用）',
  `perms` varchar(100) DEFAULT NULL COMMENT '权限标识',
  `icon` varchar(100) DEFAULT '#' COMMENT '菜单图标',
  `create_by` varchar(50) DEFAULT NULL COMMENT '创建者',
  `create_at` datetime(3) DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
  `update_by` varchar(50) DEFAULT NULL COMMENT '更新者',
  `update_at` datetime(3) DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
  `remark` varchar(500) DEFAULT '' COMMENT '备注',
  PRIMARY KEY (`menu_id`) USING BTREE,
  KEY `pid` (`pid`),
  KEY `grand_id` (`grand_id`),
  KEY `key` (`key`)
) ENGINE=InnoDB AUTO_INCREMENT=38 DEFAULT CHARSET=utf8mb4 COMMENT='菜单权限表';

-- 正在导出表  aicxtek.ac_menu 的数据：~14 rows (大约)
DELETE FROM `ac_menu`;
INSERT INTO `ac_menu` (`menu_id`, `pid`, `grand_id`, `key`, `ancestors`, `client_type`, `title`, `show_order`, `path`, `component`, `query`, `is_frame`, `is_cache`, `menu_type`, `visible`, `status`, `perms`, `icon`, `create_by`, `create_at`, `update_by`, `update_at`, `remark`) VALUES
	(1, NULL, NULL, 1, '#', '0', '系统管理', 0, 'SystemManage', 'SystemManage', '', '0', '0', 0, '1', '1', NULL, 'RightSquareOutlined', '1', '2023-12-14 21:36:01.207', '000001', '2024-01-15 09:35:23.418', '系统管理目录');
INSERT INTO `ac_menu` (`menu_id`, `pid`, `grand_id`, `key`, `ancestors`, `client_type`, `title`, `show_order`, `path`, `component`, `query`, `is_frame`, `is_cache`, `menu_type`, `visible`, `status`, `perms`, `icon`, `create_by`, `create_at`, `update_by`, `update_at`, `remark`) VALUES
	(2, NULL, 1, 2, '#', '0', '权限管理', 5, 'permit', '', '', '0', '0', 1, '1', '1', NULL, 'UsergroupAddOutlined', '1', '2023-12-14 21:36:01.207', '000001', '2024-03-06 14:17:46.508', '');
INSERT INTO `ac_menu` (`menu_id`, `pid`, `grand_id`, `key`, `ancestors`, `client_type`, `title`, `show_order`, `path`, `component`, `query`, `is_frame`, `is_cache`, `menu_type`, `visible`, `status`, `perms`, `icon`, `create_by`, `create_at`, `update_by`, `update_at`, `remark`) VALUES
	(3, 2, 1, 3, '#', '0', '菜单管理', 0, 'MenuMaintenance', '/framework/views/MainContent/SystemManage/PermissionMaintenance/MenuMaintenance', '', '0', '1', 1, '1', '1', NULL, 'ClusterOutlined', '1', '2023-12-14 21:36:01.207', '000001', '2024-03-06 15:34:33.739', '');
INSERT INTO `ac_menu` (`menu_id`, `pid`, `grand_id`, `key`, `ancestors`, `client_type`, `title`, `show_order`, `path`, `component`, `query`, `is_frame`, `is_cache`, `menu_type`, `visible`, `status`, `perms`, `icon`, `create_by`, `create_at`, `update_by`, `update_at`, `remark`) VALUES
	(4, 2, 1, 4, '#', '0', '人员-角色管理', 2, 'UserRoleMaintenance', '/framework/views/MainContent/SystemManage/PermissionMaintenance/UserRoleMaintenance', '', '0', '0', 1, '1', '1', NULL, 'ApiOutlined', '1', '2023-12-14 21:36:01.207', '000001', '2024-03-06 15:34:33.740', '');
INSERT INTO `ac_menu` (`menu_id`, `pid`, `grand_id`, `key`, `ancestors`, `client_type`, `title`, `show_order`, `path`, `component`, `query`, `is_frame`, `is_cache`, `menu_type`, `visible`, `status`, `perms`, `icon`, `create_by`, `create_at`, `update_by`, `update_at`, `remark`) VALUES
	(5, 2, 1, 5, '#', '0', '角色-权限管理', 1, 'RolePermissionMaintenance', '/framework/views/MainContent/SystemManage/PermissionMaintenance/RolePermissionMaintenance', '', '0', '0', 1, '1', '1', NULL, 'BlockOutlined', '1', '2023-12-14 21:36:01.207', '000001', '2024-03-06 15:34:33.740', '');
INSERT INTO `ac_menu` (`menu_id`, `pid`, `grand_id`, `key`, `ancestors`, `client_type`, `title`, `show_order`, `path`, `component`, `query`, `is_frame`, `is_cache`, `menu_type`, `visible`, `status`, `perms`, `icon`, `create_by`, `create_at`, `update_by`, `update_at`, `remark`) VALUES
	(6, NULL, 1, 6, '#', '0', '字典维护', 1, 'DictionaryMaintenance', '/framework/views/MainContent/Portal/dict', '', '0', '0', 1, '1', '1', NULL, 'ProfileOutlined', '1', '2023-12-14 21:36:01.207', '000001', '2024-03-06 14:17:39.532', '');
INSERT INTO `ac_menu` (`menu_id`, `pid`, `grand_id`, `key`, `ancestors`, `client_type`, `title`, `show_order`, `path`, `component`, `query`, `is_frame`, `is_cache`, `menu_type`, `visible`, `status`, `perms`, `icon`, `create_by`, `create_at`, `update_by`, `update_at`, `remark`) VALUES
	(7, NULL, 1, 7, '#', '0', '用户组管理', 4, 'UserGroupMaintenance', '/framework/views/MainContent/SystemManage/UserGroupMaintenance', '', '0', '0', 1, '1', '1', NULL, 'TeamOutlined', '1', '2023-12-14 21:36:01.207', '000001', '2024-03-06 14:17:46.508', '');
INSERT INTO `ac_menu` (`menu_id`, `pid`, `grand_id`, `key`, `ancestors`, `client_type`, `title`, `show_order`, `path`, `component`, `query`, `is_frame`, `is_cache`, `menu_type`, `visible`, `status`, `perms`, `icon`, `create_by`, `create_at`, `update_by`, `update_at`, `remark`) VALUES
	(8, NULL, 1, 8, '#', '0', '参数管理', 0, 'ParamsManage', '/framework/views/MainContent/Portal/parameter', '', '0', '0', 1, '1', '1', NULL, 'SettingOutlined', '1', '2023-12-14 21:36:01.207', '000001', '2024-02-26 13:39:51.456', '');
INSERT INTO `ac_menu` (`menu_id`, `pid`, `grand_id`, `key`, `ancestors`, `client_type`, `title`, `show_order`, `path`, `component`, `query`, `is_frame`, `is_cache`, `menu_type`, `visible`, `status`, `perms`, `icon`, `create_by`, `create_at`, `update_by`, `update_at`, `remark`) VALUES
	(9, NULL, 1, 9, '#', '0', '表格配置', 6, 'portalConfig', '/framework/views/MainContent/PortalConfig', '', '0', '0', 1, '1', '1', NULL, 'InsertRowAboveOutlined', '000001', '2023-12-28 20:45:14.901', '000001', '2024-03-06 14:17:44.652', '');
INSERT INTO `ac_menu` (`menu_id`, `pid`, `grand_id`, `key`, `ancestors`, `client_type`, `title`, `show_order`, `path`, `component`, `query`, `is_frame`, `is_cache`, `menu_type`, `visible`, `status`, `perms`, `icon`, `create_by`, `create_at`, `update_by`, `update_at`, `remark`) VALUES
	(10, 2, 1, 10, '#', '0', '权限切换', 3, 'PermissionSwitch', '/framework/views/MainContent/SystemManage/PermissionMaintenance/PermissionSwitch', '', '0', '0', 1, '1', '1', NULL, 'UserSwitchOutlined', '000001', '2023-12-28 20:47:53.465', '000001', '2024-03-06 15:34:33.740', '');
INSERT INTO `ac_menu` (`menu_id`, `pid`, `grand_id`, `key`, `ancestors`, `client_type`, `title`, `show_order`, `path`, `component`, `query`, `is_frame`, `is_cache`, `menu_type`, `visible`, `status`, `perms`, `icon`, `create_by`, `create_at`, `update_by`, `update_at`, `remark`) VALUES
	(12, NULL, 1, 12, '#', '0', '部门管理', 3, 'department', '/framework/views/MainContent/Portal/department', '', '0', '0', 1, '1', '1', NULL, 'BankOutlined', '000001', '2023-12-29 09:58:35.956', '000001', '2024-03-06 14:17:39.532', '');
INSERT INTO `ac_menu` (`menu_id`, `pid`, `grand_id`, `key`, `ancestors`, `client_type`, `title`, `show_order`, `path`, `component`, `query`, `is_frame`, `is_cache`, `menu_type`, `visible`, `status`, `perms`, `icon`, `create_by`, `create_at`, `update_by`, `update_at`, `remark`) VALUES
	(13, NULL, 1, 13, '#', '0', '账号管理', 2, 'account', '/framework/views/MainContent/Portal/user', '', '0', '0', 1, '1', '1', NULL, 'UserOutlined', '000001', '2023-12-29 09:58:59.868', '000001', '2024-03-06 14:17:39.532', '');
INSERT INTO `ac_menu` (`menu_id`, `pid`, `grand_id`, `key`, `ancestors`, `client_type`, `title`, `show_order`, `path`, `component`, `query`, `is_frame`, `is_cache`, `menu_type`, `visible`, `status`, `perms`, `icon`, `create_by`, `create_at`, `update_by`, `update_at`, `remark`) VALUES
	(26, NULL, 1, 26, '#', '0', '对象存储管理', 7, 'SaObjectStorage', '/framework/views/MainContent/Portal', '', '0', '0', 1, '1', '1', NULL, 'SettingOutlined', '000001', '2024-02-26 13:39:47.329', '000001', '2024-03-06 14:17:37.987', '');
INSERT INTO `ac_menu` (`menu_id`, `pid`, `grand_id`, `key`, `ancestors`, `client_type`, `title`, `show_order`, `path`, `component`, `query`, `is_frame`, `is_cache`, `menu_type`, `visible`, `status`, `perms`, `icon`, `create_by`, `create_at`, `update_by`, `update_at`, `remark`) VALUES
	(27, NULL, 1, 27, '#', '0', '日志', 10, 'log', '/framework/views/MainContent/Log/index.vue', '', '0', '0', 1, '1', '1', NULL, 'SettingOutlined', '000001', '2025-06-04 11:46:47.270', '000001', '2025-06-04 11:47:17.972', '');

-- 导出  表 aicxtek.ac_role 结构
DROP TABLE IF EXISTS `ac_role`;
CREATE TABLE IF NOT EXISTS `ac_role` (
  `role_id` bigint(20) NOT NULL AUTO_INCREMENT COMMENT '角色ID',
  `role_name` varchar(30) NOT NULL COMMENT '角色名称',
  `role_key` varchar(100) DEFAULT NULL COMMENT '角色权限字符串',
  `status` int(11) NOT NULL DEFAULT '0' COMMENT '角色状态（1正常 0停用）',
  `display_order` int(11) DEFAULT NULL COMMENT '显示顺序',
  `create_by` bigint(20) DEFAULT NULL COMMENT '创建者',
  `create_at` datetime(3) DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
  `update_by` bigint(20) DEFAULT NULL COMMENT '更新者',
  `update_at` datetime(3) DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
  `remark` varchar(500) DEFAULT NULL COMMENT '备注',
  `valid` char(1) DEFAULT '1' COMMENT '有效性',
  PRIMARY KEY (`role_id`)
) ENGINE=InnoDB AUTO_INCREMENT=7 DEFAULT CHARSET=utf8mb4 COMMENT='角色信息表';

-- 正在导出表  aicxtek.ac_role 的数据：~2 rows (大约)
DELETE FROM `ac_role`;
INSERT INTO `ac_role` (`role_id`, `role_name`, `role_key`, `status`, `display_order`, `create_by`, `create_at`, `update_by`, `update_at`, `remark`, `valid`) VALUES
	(1, '超级管理员', 'admin', 1, 1, 1, '2025-06-04 09:48:51.433', 1, '2025-06-04 09:48:51.433', '超级管理员', '1');
INSERT INTO `ac_role` (`role_id`, `role_name`, `role_key`, `status`, `display_order`, `create_by`, `create_at`, `update_by`, `update_at`, `remark`, `valid`) VALUES
	(2, '普通角色', 'common', 1, 2, 1, '2025-06-04 09:48:51.433', 1, '2025-06-04 09:48:51.433', '普通角色', '1');

-- 导出  表 aicxtek.ac_role_menu 结构
DROP TABLE IF EXISTS `ac_role_menu`;
CREATE TABLE IF NOT EXISTS `ac_role_menu` (
  `role_id` bigint(20) NOT NULL COMMENT '角色ID',
  `menu_id` bigint(20) NOT NULL COMMENT '菜单ID',
  PRIMARY KEY (`role_id`,`menu_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='角色和菜单关联表';

-- 正在导出表  aicxtek.ac_role_menu 的数据：~10 rows (大约)
DELETE FROM `ac_role_menu`;
INSERT INTO `ac_role_menu` (`role_id`, `menu_id`) VALUES
	(1, 1);
INSERT INTO `ac_role_menu` (`role_id`, `menu_id`) VALUES
	(1, 2);
INSERT INTO `ac_role_menu` (`role_id`, `menu_id`) VALUES
	(1, 3);
INSERT INTO `ac_role_menu` (`role_id`, `menu_id`) VALUES
	(1, 4);
INSERT INTO `ac_role_menu` (`role_id`, `menu_id`) VALUES
	(1, 5);
INSERT INTO `ac_role_menu` (`role_id`, `menu_id`) VALUES
	(1, 6);
INSERT INTO `ac_role_menu` (`role_id`, `menu_id`) VALUES
	(1, 7);
INSERT INTO `ac_role_menu` (`role_id`, `menu_id`) VALUES
	(1, 8);
INSERT INTO `ac_role_menu` (`role_id`, `menu_id`) VALUES
	(1, 9);
INSERT INTO `ac_role_menu` (`role_id`, `menu_id`) VALUES
	(1, 10);
	INSERT INTO `ac_role_menu` (`role_id`, `menu_id`) VALUES
	(1, 26);
INSERT INTO `ac_role_menu` (`role_id`, `menu_id`) VALUES
	(1, 27);

-- 导出  表 aicxtek.ac_user 结构
DROP TABLE IF EXISTS `ac_user`;
CREATE TABLE IF NOT EXISTS `ac_user` (
  `user_id` bigint(20) NOT NULL AUTO_INCREMENT COMMENT '用户ID',
  `customer_number` varchar(50) NOT NULL COMMENT '用户编码',
  `wechat_id` varchar(50) DEFAULT NULL COMMENT '微信id',
  `id_number` varchar(50) DEFAULT NULL COMMENT '身份证id',
  `name` varchar(50) DEFAULT NULL COMMENT '用户姓名',
  `dept_id` varchar(50) DEFAULT NULL COMMENT '部门ID',
  `user_name` varchar(30) NOT NULL COMMENT '用户账号',
  `nick_name` varchar(30) DEFAULT NULL COMMENT '用户昵称',
  `user_type` varchar(2) DEFAULT '00' COMMENT '用户类型（00系统用户）',
  `email` varchar(50) DEFAULT '' COMMENT '用户邮箱',
  `phone_number` varchar(11) DEFAULT '' COMMENT '手机号码',
  `sex` char(1) DEFAULT '1' COMMENT '用户性别（1男 2女）',
  `avatar` varchar(500) DEFAULT '' COMMENT '头像地址',
  `password` varchar(100) DEFAULT '' COMMENT '密码',
  `password_error_time` int(11) DEFAULT NULL COMMENT '密码输入错误次数',
  `password_last_time` datetime DEFAULT NULL COMMENT '上次密码修改时间',
  `status` int(11) DEFAULT NULL COMMENT '帐号状态ACTIVE_STATUS_DICT',
  `login_ip` varchar(128) DEFAULT '' COMMENT '最后登录IP',
  `login_date` datetime DEFAULT NULL COMMENT '最后登录时间',
  `create_by` varchar(50) DEFAULT NULL COMMENT '创建者',
  `create_at` datetime(3) DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
  `update_by` varchar(50) DEFAULT NULL COMMENT '更新者',
  `update_at` datetime(3) DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
  `remark` varchar(500) DEFAULT NULL COMMENT '备注',
  `valid` char(1) DEFAULT '1' COMMENT '有效性',
  PRIMARY KEY (`user_id`) USING BTREE,
  UNIQUE KEY `user_name` (`user_name`),
  KEY `email` (`email`),
  KEY `dept_id` (`dept_id`),
  KEY `customer_number` (`customer_number`),
  KEY `phonenumber` (`phone_number`) USING BTREE
) ENGINE=InnoDB AUTO_INCREMENT=61 DEFAULT CHARSET=utf8mb4 COMMENT='用户信息表';

-- 正在导出表  aicxtek.ac_user 的数据：~1 rows (大约)
DELETE FROM `ac_user`;
INSERT INTO `ac_user` (`user_id`, `customer_number`, `wechat_id`, `id_number`, `name`, `dept_id`, `user_name`, `nick_name`, `user_type`, `email`, `phone_number`, `sex`, `avatar`, `password`, `password_error_time`, `password_last_time`, `status`, `login_ip`, `login_date`, `create_by`, `create_at`, `update_by`, `update_at`, `remark`, `valid`) VALUES
	(1, '000001', NULL, NULL, '系统管理员', NULL, 'BidrAdmin', NULL, '00', '', '', '1', '', '332e99b4633437a86e12331647468b350d9057728f48fd81', 0, NULL, 1, '127.0.0.1', '2025-06-04 11:44:53', NULL, '2025-06-04 09:48:51.332', NULL, '2025-06-04 11:44:52.616', NULL, '1');

-- 导出  表 aicxtek.ac_user_dept 结构
DROP TABLE IF EXISTS `ac_user_dept`;
CREATE TABLE IF NOT EXISTS `ac_user_dept` (
  `user_id` bigint(20) NOT NULL DEFAULT '0' COMMENT '用户id',
  `dept_id` varchar(50) NOT NULL COMMENT '组织id',
  `data_scope` int(11) DEFAULT NULL COMMENT '数据权限范围',
  PRIMARY KEY (`user_id`,`dept_id`) USING BTREE,
  KEY `dept_id` (`dept_id`),
  KEY `user_id` (`user_id`) USING BTREE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户组织结构表';

-- 正在导出表  aicxtek.ac_user_dept 的数据：~0 rows (大约)
DELETE FROM `ac_user_dept`;

-- 导出  表 aicxtek.ac_user_group 结构
DROP TABLE IF EXISTS `ac_user_group`;
CREATE TABLE IF NOT EXISTS `ac_user_group` (
  `user_id` bigint(20) NOT NULL DEFAULT '0' COMMENT '用户id',
  `group_id` bigint(20) NOT NULL COMMENT '组id',
  `data_scope` int(11) DEFAULT '0' COMMENT '数据权限范围',
  PRIMARY KEY (`user_id`,`group_id`) USING BTREE,
  KEY `group_id` (`group_id`),
  KEY `user_id` (`user_id`) USING BTREE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户组群关系';

-- 正在导出表  aicxtek.ac_user_group 的数据：~0 rows (大约)
DELETE FROM `ac_user_group`;

-- 导出  表 aicxtek.ac_user_role 结构
DROP TABLE IF EXISTS `ac_user_role`;
CREATE TABLE IF NOT EXISTS `ac_user_role` (
  `user_id` bigint(20) NOT NULL COMMENT '用户ID',
  `role_id` bigint(20) NOT NULL COMMENT '角色ID',
  PRIMARY KEY (`user_id`,`role_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户和角色关联表';

-- 正在导出表  aicxtek.ac_user_role 的数据：~1 rows (大约)
DELETE FROM `ac_user_role`;
INSERT INTO `ac_user_role` (`user_id`, `role_id`) VALUES
	(1, 1);

-- 导出  表 aicxtek.changelog 结构
DROP TABLE IF EXISTS `changelog`;
CREATE TABLE IF NOT EXISTS `changelog` (
  `change_number` varchar(22) NOT NULL COMMENT '修改编号',
  `complete_dt` datetime(6) NOT NULL COMMENT '修改时间',
  `applied_by` varchar(100) NOT NULL COMMENT '修改用户',
  `description` varchar(500) NOT NULL COMMENT '修改文件',
  PRIMARY KEY (`change_number`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='数据库变更记录表';

-- 正在导出表  aicxtek.changelog 的数据：~0 rows (大约)
DELETE FROM `changelog`;

-- 导出  函数 aicxtek.f_nextval 结构
DROP FUNCTION IF EXISTS `f_nextval`;
DELIMITER //
CREATE FUNCTION `f_nextval`(`SEQ_NAME` VARCHAR(128)) RETURNS varchar(50) CHARSET utf8mb4
    SQL SECURITY INVOKER
    COMMENT '获取流水号'
BEGIN
  declare exsited int default 0;
  declare cur int default 0;
  declare next int default 0;
  declare _min int default 0;
  declare _max int default 0;
  declare _step int default 0;
  declare _prefix varchar(20) default '';
  declare _suffix varchar(20) default '';
  declare result varchar(50) default '';
  select count(1) into exsited from sa_sequence seq where seq.seq_name = SEQ_NAME;
  CASE
    when exsited = 0
      then set result = '';
    ELSE
      select value, prefix, suffix, min_value, max_value, step into cur,_prefix,_suffix,_min,_max,_step
      from sa_sequence seq
      where seq.seq_name = SEQ_NAME FOR UPDATE;
      if cur < _min then
        set cur = _min;
      end if;
      if cur > _max then
        set cur = _min;
      end if;
      set next = cur + _step;
      if next > _max then
        set next = _min;
      end if;
      if _prefix = 'now' then
        set _prefix = date_format(now(), '%Y%m%d%H%i%s');
      end if;
      UPDATE sa_sequence seq SET seq.value=next WHERE seq.seq_name = SEQ_NAME;
      SELECT CONCAT(_prefix, cur, _suffix) into result;
    END CASE;
  return (result);
END//
DELIMITER ;

-- 导出  表 aicxtek.sa_object_storage 结构
DROP TABLE IF EXISTS `sa_object_storage`;
CREATE TABLE IF NOT EXISTS `sa_object_storage` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT COMMENT 'id',
  `key` varchar(500) NOT NULL,
  `name` varchar(100) NOT NULL COMMENT '文件名',
  `uri` varchar(500) NOT NULL COMMENT '地址',
  `size` bigint(20) NOT NULL DEFAULT '0' COMMENT '文件大小',
  `type` varchar(10) NOT NULL COMMENT '文件存储类型',
  `create_at` timestamp(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
  `update_at` timestamp(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
  `valid` varchar(1) NOT NULL DEFAULT '0' COMMENT '有效性',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uri` (`uri`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='对象存储记录';

-- 正在导出表  aicxtek.sa_object_storage 的数据：~0 rows (大约)
DELETE FROM `sa_object_storage`;

-- 导出  表 aicxtek.sa_sequence 结构
DROP TABLE IF EXISTS `sa_sequence`;
CREATE TABLE IF NOT EXISTS `sa_sequence` (
  `seq_name` varchar(128) NOT NULL COMMENT '序列名称',
  `platform` varchar(50) NOT NULL COMMENT '所属平台',
  `value` int(11) NOT NULL COMMENT '目前序列值',
  `prefix` varchar(10) NOT NULL DEFAULT '' COMMENT '序列前缀',
  `suffix` varchar(10) NOT NULL DEFAULT '' COMMENT '序列后缀',
  `min_value` int(11) NOT NULL COMMENT '最小值',
  `max_value` int(11) NOT NULL COMMENT '最大值',
  `step` int(11) NOT NULL DEFAULT '1' COMMENT '每次取值的数量',
  `create_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
  `create_by` varchar(50) DEFAULT NULL COMMENT '创建人',
  `update_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '修改时间',
  `update_by` varchar(50) DEFAULT NULL COMMENT '修改人',
  PRIMARY KEY (`seq_name`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='队列表';

-- 正在导出表  aicxtek.sa_sequence 的数据：~2 rows (大约)
DELETE FROM `sa_sequence`;
INSERT INTO `sa_sequence` (`seq_name`, `platform`, `value`, `prefix`, `suffix`, `min_value`, `max_value`, `step`, `create_at`, `create_by`, `update_at`, `update_by`) VALUES
	('AC_DEPT_ID_SEQ', 'sys', 10000000, '', '', 10000000, 99999999, 1, '2024-03-07 16:04:26.403', NULL, '2024-03-07 16:04:26.403', NULL);
INSERT INTO `sa_sequence` (`seq_name`, `platform`, `value`, `prefix`, `suffix`, `min_value`, `max_value`, `step`, `create_at`, `create_by`, `update_at`, `update_by`) VALUES
	('AC_USER_CUSTOMER_NUMBER_SEQ', 'sys', 10000000, '', '', 10000000, 99999999, 1, '2024-03-07 16:04:26.403', NULL, '2025-06-04 09:47:20.192', NULL);

-- 导出  表 aicxtek.sa_sms_send 结构
DROP TABLE IF EXISTS `sa_sms_send`;
CREATE TABLE IF NOT EXISTS `sa_sms_send` (
  `send_id` varchar(20) COLLATE utf8mb4_bin NOT NULL COMMENT '发送流水号',
  `platform` varchar(50) COLLATE utf8mb4_bin DEFAULT NULL COMMENT '对接平台id',
  `send_type` varchar(50) COLLATE utf8mb4_bin DEFAULT NULL COMMENT '发送类型',
  `biz_id` varchar(50) COLLATE utf8mb4_bin NOT NULL COMMENT '请求端id',
  `mobile` varchar(11) COLLATE utf8mb4_bin NOT NULL COMMENT '手机号码',
  `template_code` varchar(20) COLLATE utf8mb4_bin NOT NULL COMMENT '发送模板',
  `send_sign` varchar(20) COLLATE utf8mb4_bin DEFAULT NULL COMMENT '发送签名',
  `send_param` varchar(500) COLLATE utf8mb4_bin DEFAULT NULL COMMENT '发送参数表',
  `send_status` int(11) NOT NULL COMMENT '发送状态',
  `send_result` varchar(100) COLLATE utf8mb4_bin DEFAULT NULL COMMENT '发送结果',
  `request_id` varchar(50) COLLATE utf8mb4_bin DEFAULT NULL COMMENT '服务商请求id',
  `send_at` datetime(3) DEFAULT NULL COMMENT '发送时间',
  `response_status` int(11) DEFAULT NULL COMMENT '服务商返回状态码',
  `response_at` datetime(3) DEFAULT NULL COMMENT '结果回传时间',
  `response_msg` varchar(50) COLLATE utf8mb4_bin DEFAULT NULL COMMENT '服务商返回消息',
  `response_code` varchar(50) COLLATE utf8mb4_bin DEFAULT NULL COMMENT '服务商返回代码',
  PRIMARY KEY (`send_id`),
  KEY `template_code` (`template_code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin COMMENT='短信发送记录';

-- 正在导出表  aicxtek.sa_sms_send 的数据：~0 rows (大约)
DELETE FROM `sa_sms_send`;

-- 导出  表 aicxtek.sa_sms_template 结构
DROP TABLE IF EXISTS `sa_sms_template`;
CREATE TABLE IF NOT EXISTS `sa_sms_template` (
  `id` int(10) unsigned NOT NULL AUTO_INCREMENT COMMENT 'id',
  `template_title` varchar(50) COLLATE utf8mb4_bin NOT NULL DEFAULT '0' COMMENT '短信模板名称',
  `template_type` int(11) NOT NULL DEFAULT '0' COMMENT '短信模板类型0验证码1通知2推广',
  `sms_type` varchar(50) COLLATE utf8mb4_bin DEFAULT NULL COMMENT '发送短信类型',
  `template_code` varchar(50) COLLATE utf8mb4_bin DEFAULT NULL COMMENT '云平台短信模板',
  `parameter` varchar(500) COLLATE utf8mb4_bin NOT NULL DEFAULT '{}' COMMENT '短信模板参数个数',
  `body` varchar(500) COLLATE utf8mb4_bin NOT NULL DEFAULT '0' COMMENT '短信模板内容',
  `sign` varchar(30) COLLATE utf8mb4_bin NOT NULL DEFAULT '0' COMMENT '短信签名',
  `author` varchar(30) COLLATE utf8mb4_bin NOT NULL DEFAULT '0' COMMENT '作者',
  `platform` varchar(50) COLLATE utf8mb4_bin DEFAULT NULL COMMENT '平台id',
  `confirm_at` datetime(6) DEFAULT NULL COMMENT '审批时间',
  `confirm_status` int(11) NOT NULL DEFAULT '0' COMMENT '0 未审批 1 同意 2 拒绝',
  `reason` varchar(200) COLLATE utf8mb4_bin DEFAULT NULL COMMENT '审核理由',
  `remark` varchar(50) COLLATE utf8mb4_bin DEFAULT NULL COMMENT '短信模板附言',
  PRIMARY KEY (`id`),
  UNIQUE KEY `send_sms_type` (`sms_type`) USING BTREE,
  UNIQUE KEY `template_id` (`template_code`) USING BTREE,
  KEY `platform` (`platform`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin COMMENT='短信模板表';

-- 正在导出表  aicxtek.sa_sms_template 的数据：~0 rows (大约)
DELETE FROM `sa_sms_template`;

-- 导出  表 aicxtek.sys_config 结构
DROP TABLE IF EXISTS `sys_config`;
CREATE TABLE IF NOT EXISTS `sys_config` (
  `config_id` int(11) NOT NULL AUTO_INCREMENT COMMENT '参数主键',
  `config_name` varchar(100) DEFAULT '' COMMENT '参数名称',
  `config_key` varchar(100) DEFAULT '' COMMENT '参数键名',
  `config_value` varchar(500) DEFAULT '' COMMENT '参数键值',
  `config_type` char(20) DEFAULT '0' COMMENT '系统内置',
  `create_by` bigint(20) DEFAULT NULL COMMENT '创建者',
  `create_time` datetime DEFAULT NULL COMMENT '创建时间',
  `update_by` bigint(20) DEFAULT NULL COMMENT '更新者',
  `update_time` datetime DEFAULT NULL COMMENT '更新时间',
  `remark` varchar(500) DEFAULT NULL COMMENT '备注',
  PRIMARY KEY (`config_id`),
  UNIQUE KEY `config_key` (`config_key`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='参数配置表';

-- 导出  表 aicxtek.sys_dict 结构
DROP TABLE IF EXISTS `sys_dict`;
CREATE TABLE IF NOT EXISTS `sys_dict` (
  `dict_id` varchar(100) NOT NULL COMMENT '字典编码',
  `dict_pid` varchar(100) DEFAULT NULL COMMENT '字典父节点',
  `dict_sort` int(11) DEFAULT '0' COMMENT '字典排序',
  `dict_name` varchar(100) NOT NULL DEFAULT '' COMMENT '字典类型',
  `dict_title` varchar(100) NOT NULL DEFAULT '' COMMENT '字典显示名称',
  `dict_item` varchar(100) DEFAULT '' COMMENT '字典项名称',
  `dict_value` varchar(100) NOT NULL COMMENT '字典键值',
  `dict_label` varchar(100) NOT NULL DEFAULT '' COMMENT '字典标签',
  `is_default` char(1) DEFAULT '0' COMMENT '是否默认（1是 0否）',
  `status` char(1) NOT NULL DEFAULT '0' COMMENT '状态（0正常 1停用）',
  `show` char(1) NOT NULL DEFAULT '0' COMMENT '是否显示',
  `read_only` char(1) NOT NULL DEFAULT '0' COMMENT '只读',
  `create_by` bigint(20) DEFAULT NULL COMMENT '创建者',
  `create_time` datetime(3) DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
  `update_by` bigint(20) DEFAULT NULL COMMENT '更新者',
  `update_time` datetime(3) DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
  `remark` varchar(500) DEFAULT NULL COMMENT '备注',
  PRIMARY KEY (`dict_id`) USING BTREE,
  UNIQUE KEY `dict_value_dict_name` (`dict_value`,`dict_name`),
  KEY `dict_pid` (`dict_pid`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='字典数据表';

-- 导出  表 aicxtek.sys_dict_type 结构
DROP TABLE IF EXISTS `sys_dict_type`;
CREATE TABLE IF NOT EXISTS `sys_dict_type` (
  `dict_name` varchar(50) NOT NULL COMMENT '字典类型',
  `dict_title` varchar(50) NOT NULL COMMENT '字典显示名称',
  `read_only` varchar(1) NOT NULL DEFAULT '0' COMMENT '只读',
  `expired` int(11) NOT NULL DEFAULT '1440' COMMENT '更新时间',
  PRIMARY KEY (`dict_name`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='字典类型表';

-- 导出  表 aicxtek.sys_log 结构
DROP TABLE IF EXISTS `sys_log`;
CREATE TABLE IF NOT EXISTS `sys_log` (
  `log_id` bigint(20) NOT NULL AUTO_INCREMENT COMMENT '日志id',
  `project_id` varchar(100) DEFAULT NULL COMMENT '项目标识id',
  `module_id` varchar(100) DEFAULT NULL COMMENT '模块id',
  `env_type` varchar(100) DEFAULT NULL COMMENT '环境类型',
  `create_time` datetime(3) DEFAULT NULL COMMENT '日志创建时间',
  `log_seq` bigint(20) DEFAULT NULL COMMENT '日志序列号',
  `log_level` varchar(20) DEFAULT NULL COMMENT '日志级别',
  `request_id` varchar(100) DEFAULT NULL COMMENT '请求id',
  `trace_id` varchar(100) DEFAULT NULL COMMENT 'trace id',
  `request_ip` varchar(100) DEFAULT NULL COMMENT '请求ip',
  `user_ip` varchar(100) DEFAULT NULL COMMENT '用户ip',
  `server_ip` varchar(100) DEFAULT NULL COMMENT '服务器ip',
  `thread_name` varchar(100) DEFAULT NULL COMMENT '线程名',
  `class_name` varchar(200) DEFAULT NULL COMMENT '类名',
  `method_name` varchar(200) DEFAULT NULL COMMENT '方法名',
  `content` longtext COMMENT '内容',
  PRIMARY KEY (`log_id`),
  KEY `module_id` (`module_id`),
  KEY `project_id` (`project_id`),
  KEY `env_type` (`env_type`),
  KEY `request_id` (`request_id`),
  KEY `trace_id` (`trace_id`),
  KEY `request_ip` (`request_ip`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 正在导出表  aicxtek.sys_log 的数据：~0 rows (大约)
DELETE FROM `sys_log`;

-- 导出  表 aicxtek.sys_portal 结构
DROP TABLE IF EXISTS `sys_portal`;
CREATE TABLE IF NOT EXISTS `sys_portal` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT COMMENT 'id',
  `role_id` bigint(20) NOT NULL COMMENT '对应角色id',
  `name` varchar(50) NOT NULL COMMENT '英文名',
  `display_name` varchar(50) NOT NULL DEFAULT '' COMMENT '中文名',
  `url` varchar(50) NOT NULL DEFAULT '' COMMENT 'api地址',
  `bean` varchar(200) NOT NULL DEFAULT '' COMMENT '接口bean',
  `size` varchar(50) NOT NULL DEFAULT 'small' COMMENT '表格大小PORTAL_TABLE_SIZE_DICT',
  `read_only` varchar(1) NOT NULL DEFAULT '0' COMMENT '只读',
  `summary` varchar(1) NOT NULL DEFAULT '0' COMMENT '总结栏',
  `advanced` varchar(1) NOT NULL DEFAULT '1' COMMENT '是否支持高级查询',
  `id_column` varchar(50) DEFAULT NULL COMMENT '行id字段名',
  `pid_column` varchar(50) DEFAULT NULL COMMENT '父id字段名',
  `tree_drag` varchar(1) NOT NULL DEFAULT '1' COMMENT '树形结构下是否支持拖拽修改',
  `name_column` varchar(50) DEFAULT NULL COMMENT '名称字段名',
  `order_column` varchar(50) DEFAULT NULL COMMENT '排序字段名',
  `table_drag` varchar(1) NOT NULL DEFAULT '0' COMMENT '表格拖拽改变顺序',
  `add_width` int(11) NOT NULL DEFAULT '60' COMMENT '新增弹框宽度',
  `edit_width` int(11) NOT NULL DEFAULT '60' COMMENT '编辑弹框宽度',
  `detail_width` int(11) NOT NULL DEFAULT '60' COMMENT '详情弹框宽度',
  `description_count` int(11) NOT NULL DEFAULT '2' COMMENT '弹框每行显示个数',
  `export_able` varchar(1) NOT NULL DEFAULT '1' COMMENT '支持导出',
  `import_able` varchar(1) NOT NULL DEFAULT '0' COMMENT '支持导入',
  `default_condition` json DEFAULT NULL COMMENT '默认搜索条件',
  `default_sort` json DEFAULT NULL COMMENT '默认排序字段',
  PRIMARY KEY (`id`),
  UNIQUE KEY `role_id_name` (`role_id`,`name`),
  KEY `bean` (`bean`),
  KEY `role_id` (`role_id`),
  KEY `name` (`name`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='后台管理表';

-- 导出  表 aicxtek.sys_portal_associate 结构
DROP TABLE IF EXISTS `sys_portal_associate`;
CREATE TABLE IF NOT EXISTS `sys_portal_associate` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT COMMENT 'id',
  `role_id` bigint(20) NOT NULL COMMENT '角色id',
  `portal_id` bigint(20) NOT NULL COMMENT '实体id',
  `title` varchar(50) NOT NULL COMMENT '显示名称',
  `bind_type` varchar(1) NOT NULL DEFAULT '0' COMMENT '实体关系',
  `bind_portal_id` bigint(20) NOT NULL COMMENT '目标实体id',
  `bind_property` varchar(50) NOT NULL COMMENT '关联字段名',
  `bind_sort_property` varchar(50) NOT NULL DEFAULT '' COMMENT '默认排序字段',
  `bind_sort_type` varchar(1) NOT NULL DEFAULT '1' COMMENT '默认排序方式',
  `tree_mode` varchar(1) NOT NULL DEFAULT '0' COMMENT '树形展示',
  `tree_check_strict` varchar(1) NOT NULL DEFAULT '0' COMMENT '树形结构显示是否严格节点显示',
  `attach_condition` json DEFAULT NULL COMMENT '查询条件',
  `display_order` int(11) NOT NULL DEFAULT '0' COMMENT '显示顺序',
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='关联表格配置';

-- 导出  表 aicxtek.sys_portal_column 结构
DROP TABLE IF EXISTS `sys_portal_column`;
CREATE TABLE IF NOT EXISTS `sys_portal_column` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT COMMENT 'id',
  `role_id` bigint(20) NOT NULL COMMENT '对应角色id',
  `portal_id` bigint(20) NOT NULL COMMENT '表id',
  `property` varchar(50) NOT NULL DEFAULT '' COMMENT '属性名',
  `db_field` varchar(50) NOT NULL DEFAULT '' COMMENT '数据字段名',
  `display_name` varchar(50) NOT NULL DEFAULT '' COMMENT '显示名称',
  `field_type` varchar(2) NOT NULL DEFAULT '0' COMMENT '属性类型PORTAL_FIELD_DICT',
  `reference` varchar(50) DEFAULT NULL COMMENT '字典或者跳转地址',
  `entity_field` varchar(50) DEFAULT NULL COMMENT '相关实体字段',
  `entity_condition` json DEFAULT NULL COMMENT '查询实体关系',
  `display_order` int(11) NOT NULL DEFAULT '0' COMMENT '显示顺序',
  `align` varchar(20) DEFAULT 'center' COMMENT '对齐方式',
  `width` int(11) NOT NULL DEFAULT '140' COMMENT '宽度',
  `fixed` varchar(1) NOT NULL DEFAULT '0' COMMENT '是否固定',
  `tooltip` varchar(1) NOT NULL DEFAULT '0' COMMENT '是否显示tooltip',
  `enable` varchar(1) NOT NULL DEFAULT '1' COMMENT '是否有效',
  `show` varchar(1) NOT NULL DEFAULT '1' COMMENT '是否显示',
  `filter_able` varchar(1) NOT NULL DEFAULT '1' COMMENT '是否可做筛选项',
  `sort_able` varchar(1) NOT NULL DEFAULT '1' COMMENT '是否可做排序项',
  `summary_able` varchar(1) NOT NULL DEFAULT '0' COMMENT '是否汇总',
  `edit_able` varchar(1) NOT NULL DEFAULT '0' COMMENT '表格是否可以编辑',
  `display_group_name` varchar(50) DEFAULT NULL COMMENT '字段分组名称',
  `detail_show` varchar(1) NOT NULL DEFAULT '1' COMMENT '详情时是否显示',
  `detail_size` int(11) NOT NULL DEFAULT '1' COMMENT '详情弹框布局大小',
  `detail_padding` int(11) NOT NULL DEFAULT '0' COMMENT '详情布局显示后占位填充',
  `add_show` varchar(1) NOT NULL DEFAULT '1' COMMENT '添加时是否显示',
  `add_size` int(11) NOT NULL DEFAULT '1' COMMENT '新增弹框布局大小',
  `add_padding` int(11) NOT NULL DEFAULT '0' COMMENT '新增弹框布局显示后占位填充',
  `add_disabled` varchar(50) NOT NULL DEFAULT '0' COMMENT '新增弹框disable显示',
  `edit_show` varchar(1) NOT NULL DEFAULT '1' COMMENT '编辑框是否显示',
  `edit_size` int(11) NOT NULL DEFAULT '1' COMMENT '编辑弹框布局大小',
  `edit_padding` int(11) NOT NULL DEFAULT '0' COMMENT '编辑弹框布局显示后占位填充',
  `edit_disabled` varchar(50) NOT NULL DEFAULT '0' COMMENT '编辑弹框布局disable显示',
  `required` varchar(1) NOT NULL DEFAULT '0' COMMENT '是否必填',
  `min` decimal(20,6) DEFAULT NULL COMMENT '最小值(长度)',
  `max` decimal(20,6) DEFAULT NULL COMMENT '最大值(长度)',
  `default_value` varchar(200) DEFAULT NULL COMMENT '默认内容',
  `mobile_display_type` varchar(2) NOT NULL DEFAULT '0' COMMENT '移动端显示类型',
  PRIMARY KEY (`id`),
  UNIQUE KEY `role_id_portal_id_property` (`role_id`,`portal_id`,`property`),
  KEY `role_id` (`role_id`),
  KEY `portal_id` (`portal_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='系统表表头';
