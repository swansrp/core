DROP TABLE IF EXISTS `ac_partner_history`
/
CREATE TABLE IF NOT EXISTS `ac_partner` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT 'id',
  `app_key` varchar(50) NOT NULL COMMENT '应用的唯一标识key',
  `app_secret` varchar(50) NOT NULL COMMENT '应用的密钥',
  `platform` varchar(50) DEFAULT NULL COMMENT '所属平台',
  `remark` varchar(50) DEFAULT NULL COMMENT '备注',
  `status` int NOT NULL DEFAULT '0' COMMENT '有效性',
  `create_by` varchar(50) DEFAULT NULL COMMENT '创建者',
  `create_at` datetime(3) DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
  `update_by` varchar(50) DEFAULT NULL COMMENT '更新者',
  `update_at` datetime(3) DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
  PRIMARY KEY (`id`),
  KEY `app_key` (`app_key`)
) COMMENT='三方应用对接'
/
DROP TABLE IF EXISTS `ac_partner_history`
/
CREATE TABLE IF NOT EXISTS `ac_partner_history` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT 'id',
  `platform` varchar(50) NOT NULL COMMENT '平台',
  `app_key` varchar(50) COLLATE utf8mb4_bin NOT NULL COMMENT 'appKey',
  `remote_ip` varchar(50) COLLATE utf8mb4_bin NOT NULL COMMENT '访问ip',
  `url` varchar(50) NOT NULL COMMENT 'api路径',
  `status` int NOT NULL DEFAULT '0' COMMENT '返回值',
  `message` varchar(50) DEFAULT NULL COMMENT '返回消息',
  `request_at` datetime NOT NULL COMMENT '访问时间',
  `response_at` datetime DEFAULT NULL COMMENT '返回时间',
  PRIMARY KEY (`id`)
) COMMENT='对接接口访问记录'
/



