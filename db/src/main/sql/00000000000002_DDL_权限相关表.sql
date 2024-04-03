-- 权限相关表
DROP TABLE IF EXISTS `ac_account`
/
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
  `status` int NOT NULL COMMENT '人员启用状态',
  `create_at` datetime(3) DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
  PRIMARY KEY (`id`),
  KEY `mobile` (`mobile`),
  KEY `user_name` (`user_name`),
  KEY `department` (`department`)
) COMMENT='用户表'
/

-- 数据导出被取消选择。

-- 导出  表 ac_dept 结构
DROP TABLE IF EXISTS `ac_dept`
/
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
  `status` int DEFAULT NULL COMMENT '部门状态',
  `show_order` int DEFAULT '0' COMMENT '显示顺序',
  `create_by` varchar(50) DEFAULT NULL COMMENT '创建者',
  `create_at` datetime(3) DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
  `update_by` varchar(50) DEFAULT NULL COMMENT '更新者',
  `update_at` datetime(3) DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
  `valid` char(1) DEFAULT '1' COMMENT '有效性',
  PRIMARY KEY (`dept_id`),
  KEY `pid` (`pid`),
  KEY `grand_id` (`grand_id`)
) COMMENT='部门表'
/

-- 数据导出被取消选择。

-- 导出  表 ac_group 结构
DROP TABLE IF EXISTS `ac_group`
/
CREATE TABLE IF NOT EXISTS `ac_group` (
  `id` bigint unsigned NOT NULL AUTO_INCREMENT COMMENT 'id',
  `pid` bigint DEFAULT NULL COMMENT '父id',
  `key` bigint NOT NULL DEFAULT '0' COMMENT 'key',
  `type` varchar(50) NOT NULL COMMENT '组类型',
  `name` varchar(50) NOT NULL COMMENT '组群名',
  `display_order` int NOT NULL DEFAULT '0' COMMENT '显示顺序',
  PRIMARY KEY (`id`),
  KEY `type` (`type`)
) COMMENT='用户逻辑组群'
/

-- 数据导出被取消选择。

-- 导出  表 ac_group_type 结构
DROP TABLE IF EXISTS `ac_group_type`
/
CREATE TABLE IF NOT EXISTS `ac_group_type` (
  `id` varchar(50) NOT NULL DEFAULT '' COMMENT '用户组类别id',
  `name` varchar(50) NOT NULL COMMENT '用户组类别名称',
  PRIMARY KEY (`id`)
) COMMENT='组类型'
/

-- 数据导出被取消选择。

-- 导出  表 ac_menu 结构
DROP TABLE IF EXISTS `ac_menu`
/
CREATE TABLE IF NOT EXISTS `ac_menu` (
  `menu_id` bigint NOT NULL AUTO_INCREMENT COMMENT '菜单ID',
  `pid` bigint DEFAULT NULL COMMENT '父菜单ID',
  `grand_id` bigint DEFAULT NULL COMMENT '祖父ID',
  `key` bigint DEFAULT NULL COMMENT 'key与菜单ID一致',
  `ancestors` varchar(50) NOT NULL DEFAULT '#' COMMENT '祖级列表',
  `client_type` varchar(2) NOT NULL DEFAULT '0' COMMENT '客户端类型',
  `title` varchar(50) NOT NULL COMMENT '菜单名称',
  `show_order` int DEFAULT '0' COMMENT '显示顺序',
  `path` varchar(200) DEFAULT '' COMMENT '路由地址',
  `component` varchar(255) DEFAULT NULL COMMENT '组件路径',
  `query` varchar(255) DEFAULT NULL COMMENT '路由参数',
  `is_frame` char(50) DEFAULT NULL COMMENT '是否为外链（0是 1否）',
  `is_cache` char(50) DEFAULT NULL COMMENT '是否缓存（0缓存 1不缓存）',
  `menu_type` int DEFAULT NULL COMMENT '菜单类型MENU_TYPE_DICT',
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
) COMMENT='菜单权限表'
/

-- 数据导出被取消选择。

-- 导出  表 ac_role 结构
DROP TABLE IF EXISTS `ac_role`
/
CREATE TABLE IF NOT EXISTS `ac_role` (
  `role_id` bigint NOT NULL AUTO_INCREMENT COMMENT '角色ID',
  `role_name` varchar(30) NOT NULL COMMENT '角色名称',
  `role_key` varchar(100) DEFAULT NULL COMMENT '角色权限字符串',
  `status` int NOT NULL DEFAULT '0' COMMENT '角色状态（1正常 0停用）',
  `display_order` int DEFAULT NULL COMMENT '显示顺序',
  `create_by` bigint DEFAULT NULL COMMENT '创建者',
  `create_at` datetime(3) DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
  `update_by` bigint DEFAULT NULL COMMENT '更新者',
  `update_at` datetime(3) DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
  `remark` varchar(500) DEFAULT NULL COMMENT '备注',
  `valid` char(1) DEFAULT '1' COMMENT '有效性',
  PRIMARY KEY (`role_id`)
) COMMENT='角色信息表'
/

-- 数据导出被取消选择。

-- 导出  表 ac_role_menu 结构
DROP TABLE IF EXISTS `ac_role_menu`
/
CREATE TABLE IF NOT EXISTS `ac_role_menu` (
  `role_id` bigint NOT NULL COMMENT '角色ID',
  `menu_id` bigint NOT NULL COMMENT '菜单ID',
  PRIMARY KEY (`role_id`,`menu_id`)
) COMMENT='角色和菜单关联表'
/

-- 数据导出被取消选择。

-- 导出  表 ac_user 结构
DROP TABLE IF EXISTS `ac_user`
/
CREATE TABLE IF NOT EXISTS `ac_user` (
  `user_id` bigint NOT NULL AUTO_INCREMENT COMMENT '用户ID',
  `customer_number` varchar(50) NOT NULL COMMENT '用户编码',
  `wechat_id` VARCHAR(50) NULL COMMENT '微信id',
  `id_number` VARCHAR(50) NULL COMMENT '身份证id',
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
  `password_error_time` int DEFAULT NULL COMMENT '密码输入错误次数',
  `password_last_time` datetime DEFAULT NULL COMMENT '上次密码修改时间',
  `status` int DEFAULT NULL COMMENT '帐号状态ACTIVE_STATUS_DICT',
  `login_ip` varchar(128) DEFAULT '' COMMENT '最后登录IP',
  `login_date` datetime DEFAULT NULL COMMENT '最后登录时间',
  `create_by` varchar(50) DEFAULT NULL COMMENT '创建者',
  `create_at` datetime(3) DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
  `update_by` varchar(50) DEFAULT NULL COMMENT '更新者',
  `update_at` datetime(3) DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
  `remark` varchar(500) DEFAULT NULL COMMENT '备注',
  `valid` char(1) DEFAULT '1' COMMENT '有效性',
  PRIMARY KEY (`user_id`) USING BTREE,
  KEY `email` (`email`),
  KEY `dept_id` (`dept_id`),
  KEY `customer_number` (`customer_number`),
  KEY `phonenumber` (`phone_number`) USING BTREE
) COMMENT='用户信息表'
/

-- 数据导出被取消选择。

-- 导出  表 ac_user_dept 结构
DROP TABLE IF EXISTS `ac_user_dept`
/
CREATE TABLE IF NOT EXISTS `ac_user_dept` (
  `user_id` bigint NOT NULL DEFAULT '0' COMMENT '用户id',
  `dept_id` varchar(50) NOT NULL COMMENT '组织id',
  `data_scope` int DEFAULT NULL COMMENT '数据权限范围',
  PRIMARY KEY (`user_id`,`dept_id`) USING BTREE,
  KEY `dept_id` (`dept_id`),
  KEY `user_id` (`user_id`) USING BTREE
) COMMENT='用户组织结构表'
/

-- 数据导出被取消选择。

-- 导出  表 ac_user_group 结构
DROP TABLE IF EXISTS `ac_user_group`
/
CREATE TABLE IF NOT EXISTS `ac_user_group` (
  `user_id` bigint NOT NULL DEFAULT '0' COMMENT '用户id',
  `group_id` bigint NOT NULL COMMENT '组id',
  `data_scope` int DEFAULT '0' COMMENT '数据权限范围',
  PRIMARY KEY (`user_id`,`group_id`) USING BTREE,
  KEY `group_id` (`group_id`),
  KEY `user_id` (`user_id`) USING BTREE
) COMMENT='用户组群关系'
/

-- 数据导出被取消选择。

-- 导出  表 ac_user_role 结构
DROP TABLE IF EXISTS `ac_user_role`
/
CREATE TABLE IF NOT EXISTS `ac_user_role` (
  `user_id` bigint NOT NULL COMMENT '用户ID',
  `role_id` bigint NOT NULL COMMENT '角色ID',
  PRIMARY KEY (`user_id`,`role_id`)
) COMMENT='用户和角色关联表'
/

