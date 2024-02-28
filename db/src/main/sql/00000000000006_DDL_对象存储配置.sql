

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
