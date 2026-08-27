package com.bidr.insight.smartquery.dao.schema;

import com.bidr.insight.smartquery.dao.entity.InsightAgentTable;
import com.bidr.kernel.mybatis.repository.BaseMybatisSchema;
import org.springframework.stereotype.Service;

/**
 * Title: InsightAgentTableSchema
 * Description: Agent 选表关联表建表语句（启动时经 MybatisPlusConfig 自动初始化）
 *
 * @author Sharp
 * @since 2026/8/18
 */
@Service
public class InsightAgentTableSchema extends BaseMybatisSchema<InsightAgentTable> {
    static {
        setCreateDDL("CREATE TABLE IF NOT EXISTS `insight_agent_table` (\n" +
                "  `id` int(11) NOT NULL AUTO_INCREMENT COMMENT '主键',\n" +
                "  `agent_code` varchar(50) NOT NULL DEFAULT '' COMMENT 'Agent 编码',\n" +
                "  `table_name` varchar(200) NOT NULL DEFAULT '' COMMENT '表全名（db.tbl 形式）',\n" +
                "  `table_comment` varchar(500) DEFAULT NULL COMMENT '表注释（选表时快照）',\n" +
                "  `create_by` bigint(20) DEFAULT NULL COMMENT '创建者',\n" +
                "  `create_at` datetime DEFAULT NULL COMMENT '创建时间',\n" +
                "  `update_by` bigint(20) DEFAULT NULL COMMENT '更新者',\n" +
                "  `update_at` datetime DEFAULT NULL COMMENT '更新时间',\n" +
                "  PRIMARY KEY (`id`),\n" +
                "  UNIQUE KEY `uk_agent_table` (`agent_code`, `table_name`(100))\n" +
                ") COMMENT='Agent 选表关联表';");
    }
}
