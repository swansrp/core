package com.bidr.insight.smartquery.dao.schema;

import com.bidr.insight.smartquery.dao.entity.InsightAgent;
import com.bidr.kernel.mybatis.repository.BaseMybatisSchema;
import org.springframework.stereotype.Service;

/**
 * Title: InsightAgentSchema
 * Description: 智能问数 Agent 配置表建表语句（启动时经 MybatisPlusConfig 自动初始化）
 *
 * @author Sharp
 * @since 2026/8/18
 */
@Service
public class InsightAgentSchema extends BaseMybatisSchema<InsightAgent> {
    static {
        setCreateDDL("CREATE TABLE IF NOT EXISTS `insight_agent` (\n" +
                "  `agent_id` int(11) NOT NULL AUTO_INCREMENT COMMENT 'Agent 主键',\n" +
                "  `agent_code` varchar(50) NOT NULL DEFAULT '' COMMENT 'Agent 编码（semantic_query.agent 取值）',\n" +
                "  `agent_name` varchar(100) NOT NULL DEFAULT '' COMMENT 'Agent 名称',\n" +
                "  `ds_name` varchar(100) NOT NULL DEFAULT '' COMMENT '绑定的数据源名称',\n" +
                "  `status` char(1) DEFAULT '1' COMMENT '状态（1=启用 0=停用）',\n" +
                "  `thinking_budget` int(11) DEFAULT NULL COMMENT '思考强度（仅问数链）：思考 token 上限（空/非正=最强不限制）',\n" +
                "  `create_by` bigint(20) DEFAULT NULL COMMENT '创建者',\n" +
                "  `create_at` datetime DEFAULT NULL COMMENT '创建时间',\n" +
                "  `update_by` bigint(20) DEFAULT NULL COMMENT '更新者',\n" +
                "  `update_at` datetime DEFAULT NULL COMMENT '更新时间',\n" +
                "  `remark` varchar(500) DEFAULT NULL COMMENT '备注',\n" +
                "  PRIMARY KEY (`agent_id`),\n" +
                "  UNIQUE KEY `agent_code` (`agent_code`)\n" +
                ") COMMENT='智能问数 Agent 配置表';");
        setUpgradeDDL(1, "ALTER TABLE `insight_agent`\n" +
                "\tADD COLUMN `thinking_budget` INT(11) NULL DEFAULT NULL COMMENT '思考强度（仅问数链）：思考 token 上限（空/非正=最强不限制）' AFTER `status`;\n");
    }
}
