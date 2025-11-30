package com.bidr.authorization.dao.schema;

import com.bidr.authorization.dao.entity.AcPartnerHistory;
import com.bidr.kernel.mybatis.repository.BaseMybatisSchema;
import org.springframework.stereotype.Service;

/**
 * 对接接口访问记录 Schema
 *
 * @author sharp
 */
@Service
public class AcPartnerHistorySchema extends BaseMybatisSchema<AcPartnerHistory> {
    static {
        setCreateDDL("CREATE TABLE IF NOT EXISTS `ac_partner_history` (\n" +
                "  `id` bigint NOT NULL AUTO_INCREMENT COMMENT 'id',\n" +
                "  `platform` varchar(50) NOT NULL COMMENT '平台',\n" +
                "  `app_key` varchar(50) COLLATE utf8mb4_bin NOT NULL COMMENT 'appKey',\n" +
                "  `remote_ip` varchar(50) COLLATE utf8mb4_bin NOT NULL COMMENT '访问ip',\n" +
                "  `url` varchar(50) NOT NULL COMMENT 'api路径',\n" +
                "  `status` int NOT NULL DEFAULT '0' COMMENT '返回值',\n" +
                "  `message` varchar(50) DEFAULT NULL COMMENT '返回消息',\n" +
                "  `request_at` datetime NOT NULL COMMENT '访问时间',\n" +
                "  `response_at` datetime DEFAULT NULL COMMENT '返回时间',\n" +
                "  PRIMARY KEY (`id`)\n" +
                ") COMMENT='对接接口访问记录';");
    }
}
