package com.bidr.platform.dao.schema;

import com.bidr.platform.dao.entity.SysConfig;
import com.bidr.kernel.mybatis.repository.BaseMybatisSchema;
import org.springframework.stereotype.Service;

/**
 * 参数配置表 Schema
 */
@Service
public class SysConfigSchema extends BaseMybatisSchema<SysConfig> {
    static {
        setCreateDDL("CREATE TABLE IF NOT EXISTS `sys_config` (\n" +
                "  `config_id` int(11) NOT NULL AUTO_INCREMENT COMMENT '参数主键',\n" +
                "  `config_name` varchar(100) DEFAULT '' COMMENT '参数名称',\n" +
                "  `config_key` varchar(100) DEFAULT '' COMMENT '参数键名',\n" +
                "  `config_value` varchar(500) DEFAULT '' COMMENT '参数键值',\n" +
                "  `config_type` char(20) DEFAULT '0' COMMENT '系统内置',\n" +
                "  `create_by` bigint(20) DEFAULT NULL COMMENT '创建者',\n" +
                "  `create_time` datetime DEFAULT NULL COMMENT '创建时间',\n" +
                "  `update_by` bigint(20) DEFAULT NULL COMMENT '更新者',\n" +
                "  `update_time` datetime DEFAULT NULL COMMENT '更新时间',\n" +
                "  `remark` varchar(500) DEFAULT NULL COMMENT '备注',\n" +
                "  PRIMARY KEY (`config_id`),\n" +
                "  UNIQUE KEY `config_key` (`config_key`)\n" +
                ") COMMENT='参数配置表';");
    }
}
