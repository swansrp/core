

-- 导出  表 tanya.sa_object_storage 结构
DROP TABLE IF EXISTS `sa_object_storage`
/
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
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='对象存储记录'
/
INSERT INTO `sys_config` (`config_id`, `config_name`, `config_key`, `config_value`, `config_type`, `create_by`, `create_time`, `update_by`, `update_time`, `remark`) VALUES
 (20, '对象服务器类型', 'OSS_SERVER_TYPE', '1', '0', NULL, '2024-02-26 09:35:11', NULL, '2024-02-26 09:35:11', 'Local Minio Ali'),
 (21, '对象存储桶名称', 'OSS_BUCKET', 'tanya', '0', NULL, '2024-02-26 09:35:11', NULL, '2024-02-26 09:35:11', '上传路径 桶名称'),
 (22, '对象存储接入地址', 'OSS_ACCESS_ENDPOINT', 'http://49.232.132.116:10022', '0', NULL, '2024-02-26 09:35:11', NULL, '2024-02-26 09:35:11', '访问域名'),
 (23, '对象存储接入key', 'OSS_ACCESS_KEY', 'UX5E9Jm7ma2CXDv1gFnQ', '0', NULL, '2024-02-26 09:35:11', NULL, '2024-02-26 09:35:11', 'OSS APP KEY'),
 (24, '对象存储接入秘钥', 'OSS_ACCESS_SECRET', 'glj4I7vsdggEElt2mA4FNLSMyaBcTdq5V9geZRGN', '0', NULL, '2024-02-26 09:35:11', NULL, '2024-02-26 09:35:11', 'OSS APP SECRET')
/

