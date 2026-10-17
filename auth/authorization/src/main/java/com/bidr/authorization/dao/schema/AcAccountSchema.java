package com.bidr.authorization.dao.schema;

import com.bidr.authorization.dao.entity.AcAccount;
import com.bidr.kernel.mybatis.repository.BaseMybatisSchema;
import org.springframework.stereotype.Service;


/**
 * Title: AcAccountSchema
 *
 * @author Sharp
 * @since 2025/11/28
 */
@Service
public class AcAccountSchema extends BaseMybatisSchema<AcAccount> {
    static {
        setCreateDDL("CREATE TABLE IF NOT EXISTS `ac_account` (\n" +
                "  `id` varchar(50) NOT NULL COMMENT 'id',\n" +
                "  `name` varchar(50) NOT NULL COMMENT '姓名',\n" +
                "  `gender` varchar(50) DEFAULT NULL COMMENT '人员性别',\n" +
                "  `nationality` varchar(50) DEFAULT NULL COMMENT '民族',\n" +
                "  `native_place` varchar(50) DEFAULT NULL COMMENT '籍贯',\n" +
                "  `political_outlook` varchar(50) DEFAULT NULL COMMENT '政治面貌',\n" +
                "  `work_date` datetime DEFAULT NULL COMMENT '参加工作日期',\n" +
                "  `id_number` varchar(50) DEFAULT NULL COMMENT '身份证号',\n" +
                "  `profession` varchar(50) DEFAULT NULL COMMENT '专业技术职务',\n" +
                "  `talent` varchar(50) DEFAULT NULL COMMENT '公司人才工程名称',\n" +
                "  `email` varchar(50) DEFAULT NULL COMMENT '人员电子邮件',\n" +
                "  `mobile` varchar(50) DEFAULT NULL COMMENT '人员手机',\n" +
                "  `category` varchar(50) DEFAULT NULL COMMENT '人员类别',\n" +
                "  `department` varchar(50) DEFAULT NULL COMMENT '人员所属部门',\n" +
                "  `org` varchar(50) DEFAULT NULL COMMENT '人员所属组织',\n" +
                "  `user_name` varchar(50) NOT NULL COMMENT '用户名',\n" +
                "  `picture_link` varchar(500) DEFAULT NULL COMMENT '人员照片链接',\n" +
                "  `signature_link` varchar(500) DEFAULT NULL COMMENT '人员电子签名链接',\n" +
                "  `employ_status` varchar(50) NOT NULL COMMENT '在职状态',\n" +
                "  `status` int(11) NOT NULL COMMENT '人员启用状态',\n" +
                "  `create_at` datetime(3) DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',\n" +
                "  PRIMARY KEY (`id`),\n" +
                "  KEY `mobile` (`mobile`),\n" +
                "  KEY `user_name` (`user_name`),\n" +
                "  KEY `department` (`department`)\n" +
                ") COMMENT='用户表';");
    }
}
