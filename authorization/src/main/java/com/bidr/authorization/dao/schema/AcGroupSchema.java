package com.bidr.authorization.dao.schema;

import com.bidr.authorization.dao.entity.AcGroup;
import com.bidr.kernel.mybatis.repository.BaseMybatisSchema;
import org.springframework.stereotype.Service;

/**
 * Title: AcGroupSchema
 *
 * @author Sharp
 * @since 2025/11/28
 */

@Service
public class AcGroupSchema extends BaseMybatisSchema<AcGroup> {
    static {
        setCreateDDL("CREATE TABLE IF NOT EXISTS `ac_group` (\n" +
                "  `id` bigint(20) unsigned NOT NULL AUTO_INCREMENT COMMENT 'id',\n" +
                "  `pid` bigint(20) DEFAULT NULL COMMENT '父id',\n" +
                "  `key` bigint(20) NOT NULL DEFAULT '0' COMMENT 'key',\n" +
                "  `type` varchar(50) NOT NULL COMMENT '组类型',\n" +
                "  `name` varchar(50) NOT NULL COMMENT '组群名',\n" +
                "  `display_order` int(11) NOT NULL DEFAULT '0' COMMENT '显示顺序',\n" +
                "  PRIMARY KEY (`id`),\n" +
                "  KEY `type` (`type`)\n" +
                ") COMMENT='用户逻辑组群';");
    }
}
