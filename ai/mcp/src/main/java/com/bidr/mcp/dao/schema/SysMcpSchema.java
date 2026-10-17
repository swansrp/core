package com.bidr.mcp.dao.schema;

import com.bidr.kernel.mybatis.repository.BaseMybatisSchema;
import com.bidr.mcp.dao.entity.SysMcp;
import org.springframework.stereotype.Service;

/**
 * mcp配置表 Schema
 *
 * @author sharp
 */
@Service
public class SysMcpSchema extends BaseMybatisSchema<SysMcp> {
    static {
        setCreateDDL("CREATE TABLE IF NOT EXISTS `sys_mcp` (\n" +
                "  `end_point` varchar(200) NOT NULL COMMENT 'mcp服务',\n" +
                "  `end_point_name` varchar(200) NOT NULL COMMENT 'mcp服务名称',\n" +
                "  `name` varchar(200) NOT NULL COMMENT 'mcp方法名',\n" +
                "  `type` varchar(20) NOT NULL COMMENT 'mcp类型',\n" +
                "  `description` LONGTEXT NOT NULL COMMENT 'mcp方法描述',\n" +
                "  PRIMARY KEY (`end_point`,`name`,`type`) USING BTREE\n" +
                ") COMMENT='mcp配置表';");
    }
}
