package com.bidr.authorization.dao.schema;

import com.bidr.authorization.dao.entity.AcUser;
import com.bidr.kernel.mybatis.repository.BaseMybatisSchema;
import org.springframework.stereotype.Service;


/**
 * Title: AcUserSchema
 *
 * @author Sharp
 * @since 2025/11/28
 */
@Service
public class AcUserSchema extends BaseMybatisSchema<AcUser> {
    static {
        setCreateDDL("CREATE TABLE IF NOT EXISTS `ac_user` (\n" +
                "  `user_id` bigint(20) NOT NULL AUTO_INCREMENT COMMENT '用户ID',\n" +
                "  `customer_number` varchar(50) NOT NULL COMMENT '用户编码',\n" +
                "  `wechat_id` varchar(50) DEFAULT NULL COMMENT '微信id',\n" +
                "  `id_number` varchar(50) DEFAULT NULL COMMENT '身份证id',\n" +
                "  `name` varchar(50) DEFAULT NULL COMMENT '用户姓名',\n" +
                "  `dept_id` varchar(50) DEFAULT NULL COMMENT '部门ID',\n" +
                "  `user_name` varchar(30) NOT NULL COMMENT '用户账号',\n" +
                "  `nick_name` varchar(30) DEFAULT NULL COMMENT '用户昵称',\n" +
                "  `user_type` varchar(2) DEFAULT '00' COMMENT '用户类型（00系统用户）',\n" +
                "  `email` varchar(50) DEFAULT '' COMMENT '用户邮箱',\n" +
                "  `phone_number` varchar(11) DEFAULT '' COMMENT '手机号码',\n" +
                "  `sex` char(1) DEFAULT '1' COMMENT '用户性别（1男 2女）',\n" +
                "  `avatar` varchar(500) DEFAULT '' COMMENT '头像地址',\n" +
                "  `password` varchar(100) DEFAULT '' COMMENT '密码',\n" +
                "  `password_error_time` int(11) DEFAULT NULL COMMENT '密码输入错误次数',\n" +
                "  `password_last_time` datetime DEFAULT NULL COMMENT '上次密码修改时间',\n" +
                "  `status` int(11) DEFAULT NULL COMMENT '帐号状态ACTIVE_STATUS_DICT',\n" +
                "  `login_ip` varchar(128) DEFAULT '' COMMENT '最后登录IP',\n" +
                "  `login_date` datetime DEFAULT NULL COMMENT '最后登录时间',\n" +
                "  `create_by` varchar(50) DEFAULT NULL COMMENT '创建者',\n" +
                "  `create_at` datetime(3) DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',\n" +
                "  `update_by` varchar(50) DEFAULT NULL COMMENT '更新者',\n" +
                "  `update_at` datetime(3) DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',\n" +
                "  `remark` varchar(500) DEFAULT NULL COMMENT '备注',\n" +
                "  `valid` char(1) DEFAULT '1' COMMENT '有效性',\n" +
                "  PRIMARY KEY (`user_id`) USING BTREE,\n" +
                "  UNIQUE KEY `user_name` (`user_name`),\n" +
                "  KEY `email` (`email`),\n" +
                "  KEY `dept_id` (`dept_id`),\n" +
                "  KEY `customer_number` (`customer_number`),\n" +
                "  KEY `phonenumber` (`phone_number`) USING BTREE\n" +
                ") COMMENT='用户信息表';");

        setInitData("INSERT INTO `ac_user` (`user_id`, `customer_number`, `wechat_id`, `id_number`, `name`, `dept_id`, `user_name`, `nick_name`, `user_type`," +
                " `email`, `phone_number`, `sex`, `avatar`, `password`, `password_error_time`, `password_last_time`, `status`, `login_ip`, `login_date`, " +
                "`create_by`, `update_by`, `remark`, `valid`) VALUES\n\t(1, '000001', NULL, NULL, '系统管理员', NULL, 'BidrAdmin', NULL, '00', '', '', '1', '', " +
                "'a49a1ea1612f58a29968025b73ba2653274711c198c79465', 0, NOW(), 1, '127.0.0.1', '2025-06-04 11:44:53', NULL, NULL, NULL, '1');");
    }
}
