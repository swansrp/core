DROP TABLE IF EXISTS `sys_mcp`
/
CREATE TABLE IF NOT EXISTS `sys_mcp` (
  `end_point` varchar(200) NOT NULL COMMENT 'mcp服务',
  `end_point_name` varchar(200) NOT NULL COMMENT 'mcp服务名称',
  `name` varchar(200) NOT NULL COMMENT 'mcp方法名',
  `type` varchar(20) NOT NULL COMMENT 'mcp类型',
  `description` LONGTEXT NOT NULL COMMENT 'mcp方法描述',
  PRIMARY KEY (`end_point`,`name`,`type`) USING BTREE
) COMMENT='mcp配置表'
/


