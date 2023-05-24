
DROP DATABASE IF EXISTS `test`;
CREATE DATABASE `test` /*!40100 COLLATE 'utf8mb4_general_ci' */;
USE `test`;

DROP TABLE IF EXISTS `changelog`;
CREATE TABLE IF NOT EXISTS `changelog` (
  `change_number` varchar(22) NOT NULL COMMENT '修改编号',
  `complete_dt` datetime(6) NOT NULL COMMENT '修改时间',
  `applied_by` varchar(100) NOT NULL COMMENT '修改用户',
  `description` varchar(500) NOT NULL COMMENT '修改文件',
  PRIMARY KEY (`change_number`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='数据库变更记录表';


