-- 系统参数表
DROP TABLE IF EXISTS `sys_config`;
/
CREATE TABLE IF NOT EXISTS `sys_config` (
  `config_id` int NOT NULL AUTO_INCREMENT COMMENT '参数主键',
  `config_name` varchar(100)  DEFAULT '' COMMENT '参数名称',
  `config_key` varchar(100)  DEFAULT '' COMMENT '参数键名',
  `config_value` varchar(500)  DEFAULT '' COMMENT '参数键值',
  `config_type` varchar(20)  DEFAULT '0' COMMENT '系统内置',
  `create_by` bigint DEFAULT NULL COMMENT '创建者',
  `create_time` datetime DEFAULT NULL COMMENT '创建时间',
  `update_by` bigint DEFAULT NULL COMMENT '更新者',
  `update_time` datetime DEFAULT NULL COMMENT '更新时间',
  `remark` varchar(500)  DEFAULT NULL COMMENT '备注',
  PRIMARY KEY (`config_id`),
  UNIQUE KEY `config_key` (`config_key`)
)  COMMENT='参数配置表'
/

DROP TABLE IF EXISTS `sys_dict`
/
CREATE TABLE IF NOT EXISTS `sys_dict` (
  `dict_id` varchar(100) NOT NULL COMMENT '字典编码',
  `dict_pid` varchar(100) DEFAULT NULL COMMENT '字典父节点',
  `dict_sort` int DEFAULT '0' COMMENT '字典排序',
  `dict_name` varchar(100)  NOT NULL DEFAULT '' COMMENT '字典类型',
  `dict_title` varchar(100)  NOT NULL DEFAULT '' COMMENT '字典显示名称',
  `dict_item` varchar(100)  DEFAULT '' COMMENT '字典项名称',
  `dict_value` varchar(100)  NOT NULL COMMENT '字典键值',
  `dict_label` varchar(100)  NOT NULL DEFAULT '' COMMENT '字典标签',
  `is_default` char(1)  DEFAULT '0' COMMENT '是否默认（1是 0否）',
  `status` char(1)  NOT NULL DEFAULT '0' COMMENT '状态（0正常 1停用）',
  `show` char(1)  NOT NULL DEFAULT '0' COMMENT '是否显示',
  `read_only` char(1)  NOT NULL DEFAULT '0' COMMENT '只读',
  `create_by` bigint DEFAULT NULL COMMENT '创建者',
  `create_time` datetime(3) DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
  `update_by` bigint DEFAULT NULL COMMENT '更新者',
  `update_time` datetime(3) DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
  `remark` varchar(500)  DEFAULT NULL COMMENT '备注',
  PRIMARY KEY (`dict_id`) USING BTREE,
  UNIQUE KEY `dict_value_dict_name` (`dict_value`,`dict_name`),
  KEY `dict_pid` (`dict_pid`)
)  COMMENT='字典数据表'
/

DROP TABLE IF EXISTS `sys_dict_type`
/
CREATE TABLE IF NOT EXISTS `sys_dict_type` (
  `dict_name` varchar(50)  NOT NULL COMMENT '字典类型',
  `dict_title` varchar(50)  NOT NULL COMMENT '字典显示名称',
  `read_only` varchar(1)  NOT NULL DEFAULT '0' COMMENT '只读',
  `expired` int NOT NULL DEFAULT '1440' COMMENT '更新时间',
  PRIMARY KEY (`dict_name`)
)  COMMENT='字典类型表'
/
DROP TABLE IF EXISTS `sys_log`
/
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
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
/
