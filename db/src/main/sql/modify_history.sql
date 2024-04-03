ALTER TABLE `ac_user`
	ADD COLUMN `wechat_id` VARCHAR(50) NULL COMMENT '微信id' AFTER `customer_number`,
	ADD COLUMN `id_number` VARCHAR(50) NULL COMMENT '身份证id' AFTER `wechat_id`;
ALTER TABLE `ac_user`
	CHANGE COLUMN `avatar` `avatar` VARCHAR(500) NULL DEFAULT '' COMMENT '头像地址' COLLATE 'utf8mb4_general_ci' AFTER `sex`;
ALTER TABLE `ac_user`
	CHANGE COLUMN `sex` `sex` CHAR(1) NULL DEFAULT '1' COMMENT '用户性别（1男 2女）' COLLATE 'utf8mb4_general_ci' AFTER `phone_number`;
ALTER TABLE `sys_config`
	CHANGE COLUMN `config_type` `config_type` CHAR(20) NULL DEFAULT '0' COMMENT '系统内置' COLLATE 'utf8mb4_general_ci' AFTER `config_value`;