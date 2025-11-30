package com.bidr.sms.dao.schema;

import com.bidr.kernel.mybatis.repository.BaseMybatisSchema;
import com.bidr.sms.dao.entity.SaSmsTemplate;
import org.springframework.stereotype.Service;

/**
 * 短信模板表 Schema
 *
 * @author sharp
 */
@Service
public class SaSmsTemplateSchema extends BaseMybatisSchema<SaSmsTemplate> {
    static {
        setCreateDDL("CREATE TABLE IF NOT EXISTS `sa_sms_template` (\n" +
                "  `id` int unsigned NOT NULL AUTO_INCREMENT COMMENT 'id',\n" +
                "  `template_title` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL DEFAULT '0' COMMENT '短信模板名称',\n" +
                "  `template_type` int NOT NULL DEFAULT '0' COMMENT '短信模板类型0验证码1通知2推广',\n" +
                "  `sms_type` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin DEFAULT NULL COMMENT '发送短信类型',\n" +
                "  `template_code` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin DEFAULT NULL COMMENT '云平台短信模板',\n" +
                "  `parameter` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL DEFAULT '{}' COMMENT '短信模板参数个数',\n" +
                "  `body` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL DEFAULT '0' COMMENT '短信模板内容',\n" +
                "  `sign` varchar(30) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL DEFAULT '0' COMMENT '短信签名',\n" +
                "  `author` varchar(30) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL DEFAULT '0' COMMENT '作者',\n" +
                "  `platform` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin DEFAULT NULL COMMENT '平台id',\n" +
                "  `confirm_at` datetime(6) DEFAULT NULL COMMENT '审批时间',\n" +
                "  `confirm_status` int NOT NULL DEFAULT '0' COMMENT '0 未审批 1 同意 2 拒绝',\n" +
                "  `reason` varchar(200) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin DEFAULT NULL COMMENT '审核理由',\n" +
                "  `remark` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin DEFAULT NULL COMMENT '短信模板附言',\n" +
                "  PRIMARY KEY (`id`),\n" +
                "  UNIQUE KEY `send_sms_type` (`sms_type`) USING BTREE,\n" +
                "  UNIQUE KEY `template_id` (`template_code`) USING BTREE,\n" +
                "  KEY `platform` (`platform`)\n" +
                ") COMMENT='短信模板表';");
    }
}
