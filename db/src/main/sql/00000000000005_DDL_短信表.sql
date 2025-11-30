
-- 导出  表 sa_sms_send 结构
DROP TABLE IF EXISTS `sa_sms_send`
/
CREATE TABLE IF NOT EXISTS `sa_sms_send` (
  `send_id` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL COMMENT '发送流水号',
  `platform` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin DEFAULT NULL COMMENT '对接平台id',
  `send_type` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin DEFAULT NULL COMMENT '发送类型',
  `biz_id` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL COMMENT '请求端id',
  `mobile` varchar(11) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL COMMENT '手机号码',
  `template_code` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL COMMENT '发送模板',
  `send_sign` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin DEFAULT NULL COMMENT '发送签名',
  `send_param` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin DEFAULT NULL COMMENT '发送参数表',
  `send_status` int NOT NULL COMMENT '发送状态',
  `send_result` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin DEFAULT NULL COMMENT '发送结果',
  `request_id` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin DEFAULT NULL COMMENT '服务商请求id',
  `send_at` datetime(3) DEFAULT NULL COMMENT '发送时间',
  `response_status` int DEFAULT NULL COMMENT '服务商返回状态码',
  `response_at` datetime(3) DEFAULT NULL COMMENT '结果回传时间',
  `response_msg` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin DEFAULT NULL COMMENT '服务商返回消息',
  `response_code` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin DEFAULT NULL COMMENT '服务商返回代码',
  PRIMARY KEY (`send_id`),
  KEY `template_code` (`template_code`)
) COMMENT='短信发送记录'
/

-- 数据导出被取消选择。

-- 导出  表 sa_sms_template 结构
DROP TABLE IF EXISTS `sa_sms_template`
/
CREATE TABLE IF NOT EXISTS `sa_sms_template` (
  `id` int unsigned NOT NULL AUTO_INCREMENT COMMENT 'id',
  `template_title` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL DEFAULT '0' COMMENT '短信模板名称',
  `template_type` int NOT NULL DEFAULT '0' COMMENT '短信模板类型0验证码1通知2推广',
  `sms_type` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin DEFAULT NULL COMMENT '发送短信类型',
  `template_code` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin DEFAULT NULL COMMENT '云平台短信模板',
  `parameter` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL DEFAULT '{}' COMMENT '短信模板参数个数',
  `body` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL DEFAULT '0' COMMENT '短信模板内容',
  `sign` varchar(30) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL DEFAULT '0' COMMENT '短信签名',
  `author` varchar(30) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL DEFAULT '0' COMMENT '作者',
  `platform` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin DEFAULT NULL COMMENT '平台id',
  `confirm_at` datetime(6) DEFAULT NULL COMMENT '审批时间',
  `confirm_status` int NOT NULL DEFAULT '0' COMMENT '0 未审批 1 同意 2 拒绝',
  `reason` varchar(200) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin DEFAULT NULL COMMENT '审核理由',
  `remark` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin DEFAULT NULL COMMENT '短信模板附言',
  PRIMARY KEY (`id`),
  UNIQUE KEY `send_sms_type` (`sms_type`) USING BTREE,
  UNIQUE KEY `template_id` (`template_code`) USING BTREE,
  KEY `platform` (`platform`)
) COMMENT='短信模板表'
/

-- 数据导出被取消选择。