-- 添加微信基本信息表结构
-- 沙若鹏


-- 导出  表 seed.mm_openid_map 结构
DROP TABLE IF EXISTS `mm_openid_map`
/
CREATE TABLE IF NOT EXISTS `mm_openid_map` (
  `open_id` varchar(50) NOT NULL COMMENT 'openId',
  `union_id` varchar(50) NOT NULL COMMENT '开放平台id',
  `phone` varchar(20) DEFAULT NULL COMMENT '手机号',
  `nick_name` varchar(50) DEFAULT NULL COMMENT '昵称',
  `avatar` varchar(200) DEFAULT NULL COMMENT '头像地址',
  PRIMARY KEY (`open_id`,`union_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='微信公众号账号对应关系'
/

-- 数据导出被取消选择。

-- 导出  表 seed.mm_role_tag_map 结构
DROP TABLE IF EXISTS `mm_role_tag_map`
/
CREATE TABLE IF NOT EXISTS `mm_role_tag_map` (
  `role_id` bigint unsigned NOT NULL COMMENT '角色id',
  `tag_id` int(11) NOT NULL COMMENT '标签id',
  `tag_name` varchar(50) DEFAULT NULL COMMENT '标签名',
  `menu_id` varchar(20) DEFAULT NULL COMMENT '个性菜单id',
  PRIMARY KEY (`role_id`,`tag_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户标签角色关系'
/
