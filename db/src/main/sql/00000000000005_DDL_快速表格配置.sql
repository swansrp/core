
DROP TABLE IF EXISTS `sys_portal`
/
CREATE TABLE IF NOT EXISTS `sys_portal` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT COMMENT 'id',
  `name` varchar(50) NOT NULL COMMENT '英文名',
  `display_name` varchar(50) NOT NULL DEFAULT '' COMMENT '中文名',
  `url` varchar(50) NOT NULL DEFAULT '' COMMENT 'api地址',
  `bean` varchar(200) NOT NULL DEFAULT '' COMMENT '接口bean',
  `size` varchar(50) NOT NULL DEFAULT 'small' COMMENT '表格大小PORTAL_TABLE_SIZE_DICT',
  `read_only` varchar(1) NOT NULL DEFAULT '0' COMMENT '只读',
  `summary` varchar(1) NOT NULL DEFAULT '0' COMMENT '总结栏',
  `id_column` varchar(50) DEFAULT NULL COMMENT '行id字段名',
  `pid_column` varchar(50) DEFAULT NULL COMMENT '父id字段名',
  `tree_drag` varchar(1) NOT NULL DEFAULT '1' COMMENT '树形结构下是否支持拖拽修改',
  `name_column` varchar(50) DEFAULT NULL COMMENT '名称字段名',
  `order_column` varchar(50) DEFAULT NULL COMMENT '排序字段名',
  `table_drag` varchar(1) NOT NULL DEFAULT '0' COMMENT '表格拖拽改变顺序',
  `add_width` int(11) NOT NULL DEFAULT '100' COMMENT '新增弹框宽度',
  `edit_width` int(11) NOT NULL DEFAULT '60' COMMENT '编辑弹框宽度',
  `detail_width` int(11) NOT NULL DEFAULT '60' COMMENT '详情弹框宽度',
  `description_count` int(11) NOT NULL DEFAULT '2' COMMENT '弹框每行显示个数',
  `export_able` varchar(1) NOT NULL DEFAULT '1' COMMENT '支持导出',
  `import_able` varchar(1) NOT NULL DEFAULT '0' COMMENT '支持导入',
  PRIMARY KEY (`id`),
  UNIQUE KEY `name` (`name`),
  KEY `bean` (`bean`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='后台管理表'
/


DELETE FROM `sys_portal`
/
INSERT INTO `sys_portal` (`id`, `name`, `display_name`, `url`, `bean`, `size`, `read_only`, `summary`, `id_column`, `pid_column`, `tree_drag`, `name_column`, `order_column`, `table_drag`, `add_width`, `edit_width`, `detail_width`, `description_count`, `export_able`, `import_able`) VALUES
	(1, 'user', '用户管理', 'portal/user', 'com.bidr.admin.manage.user.controller.AdminUserController', 'small', '0', '0', 'userId', '', '1', 'name', NULL, '0', 100, 100, 100, 2, '1', '1'),
	(2, 'department', '部门管理', 'portal/department', 'com.bidr.authorization.controller.admin.AdminDeptController', 'small', '0', '0', 'deptId', 'pid', '1', 'name', 'showOrder', '0', 100, 100, 100, 2, '1', '0')
/	

-- 导出  表 tanya.sys_portal_column 结构
DROP TABLE IF EXISTS `sys_portal_column`;
CREATE TABLE IF NOT EXISTS `sys_portal_column` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT COMMENT 'id',
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
  `edit_able` varchar(1) NOT NULL DEFAULT '0' COMMENT '表格是否可以编辑',
  `required` varchar(1) NOT NULL DEFAULT '0' COMMENT '是否必填',
  `enable` varchar(1) NOT NULL DEFAULT '1' COMMENT '是否有效',
  `show` varchar(1) NOT NULL DEFAULT '1' COMMENT '是否显示',
  `detail_show` varchar(1) NOT NULL DEFAULT '1' COMMENT '详情时是否显示',
  `detail_size` int(11) NOT NULL DEFAULT '1' COMMENT '详情弹框布局大小',
  `add_show` varchar(1) NOT NULL DEFAULT '1' COMMENT '添加时是否显示',
  `add_size` int(11) NOT NULL DEFAULT '1' COMMENT '新增弹框布局大小',
  `add_padding` int(11) NOT NULL DEFAULT '0' COMMENT '新增弹框布局显示后占位填充',
  `edit_show` varchar(1) NOT NULL DEFAULT '1' COMMENT '编辑框是否显示',
  `edit_size` int(11) NOT NULL DEFAULT '1' COMMENT '编辑弹框布局大小',
  `edit_padding` int(11) NOT NULL DEFAULT '0' COMMENT '编辑弹框布局显示后占位填充',
  `filter_able` varchar(1) NOT NULL DEFAULT '1' COMMENT '是否可做筛选项',
  `sort_able` varchar(1) NOT NULL DEFAULT '1' COMMENT '是否可做排序项',
  `min` decimal(20,6) DEFAULT NULL COMMENT '最小值(长度)',
  `max` decimal(20,6) DEFAULT NULL COMMENT '最大值(长度)',
  `default_value` varchar(200) DEFAULT NULL COMMENT '默认内容',
  PRIMARY KEY (`id`),
  UNIQUE KEY `portal_id_property` (`portal_id`,`property`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='系统表表头'
/

DELETE FROM `sys_portal_column`
/


