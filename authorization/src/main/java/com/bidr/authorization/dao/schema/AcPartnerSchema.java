package com.bidr.authorization.dao.schema;

import com.bidr.authorization.dao.entity.AcPartner;
import com.bidr.kernel.mybatis.repository.BaseMybatisSchema;
import org.springframework.stereotype.Service;

/**
 * 三方应用对接 Schema
 *
 * @author sharp
 */
@Service
public class AcPartnerSchema extends BaseMybatisSchema<AcPartner> {
    static {
        setCreateDDL("CREATE TABLE IF NOT EXISTS `ac_partner` (\n" +
                "  `id` bigint NOT NULL AUTO_INCREMENT COMMENT 'id',\n" +
                "  `app_key` varchar(50) NOT NULL COMMENT '应用的唯一标识key',\n" +
                "  `app_secret` varchar(50) NOT NULL COMMENT '应用的密钥',\n" +
                "  `platform` varchar(50) DEFAULT NULL COMMENT '所属平台',\n" +
                "  `remark` varchar(50) DEFAULT NULL COMMENT '备注',\n" +
                "  `status` int NOT NULL DEFAULT '0' COMMENT '有效性',\n" +
                "  `create_by` varchar(50) DEFAULT NULL COMMENT '创建者',\n" +
                "  `create_at` datetime(3) DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',\n" +
                "  `update_by` varchar(50) DEFAULT NULL COMMENT '更新者',\n" +
                "  `update_at` datetime(3) DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',\n" +
                "  PRIMARY KEY (`id`),\n" +
                "  KEY `app_key` (`app_key`)\n" +
                ") COMMENT='三方应用对接';");
    }
}
