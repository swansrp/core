package com.bidr.sms.dao.schema;

import com.bidr.kernel.mybatis.repository.BaseMybatisSchema;
import com.bidr.sms.dao.entity.SaSmsSend;
import org.springframework.stereotype.Service;

/**
 * 短信发送记录 Schema
 *
 * @author sharp
 */
@Service
public class SaSmsSendSchema extends BaseMybatisSchema<SaSmsSend> {
    static {
        setCreateDDL("CREATE TABLE IF NOT EXISTS `sa_sms_send` (\n" +
                "  `send_id` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL COMMENT '发送流水号',\n" +
                "  `platform` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin DEFAULT NULL COMMENT '对接平台id',\n" +
                "  `send_type` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin DEFAULT NULL COMMENT '发送类型',\n" +
                "  `biz_id` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL COMMENT '请求端id',\n" +
                "  `mobile` varchar(11) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL COMMENT '手机号码',\n" +
                "  `template_code` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL COMMENT '发送模板',\n" +
                "  `send_sign` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin DEFAULT NULL COMMENT '发送签名',\n" +
                "  `send_param` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin DEFAULT NULL COMMENT '发送参数表',\n" +
                "  `send_status` int NOT NULL COMMENT '发送状态',\n" +
                "  `send_result` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin DEFAULT NULL COMMENT '发送结果',\n" +
                "  `request_id` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin DEFAULT NULL COMMENT '服务商请求id',\n" +
                "  `send_at` datetime(3) DEFAULT NULL COMMENT '发送时间',\n" +
                "  `response_status` int DEFAULT NULL COMMENT '服务商返回状态码',\n" +
                "  `response_at` datetime(3) DEFAULT NULL COMMENT '结果回传时间',\n" +
                "  `response_msg` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin DEFAULT NULL COMMENT '服务商返回消息',\n" +
                "  `response_code` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin DEFAULT NULL COMMENT '服务商返回代码',\n" +
                "  PRIMARY KEY (`send_id`),\n" +
                "  KEY `template_code` (`template_code`)\n" +
                ") COMMENT='短信发送记录';");
    }
}
