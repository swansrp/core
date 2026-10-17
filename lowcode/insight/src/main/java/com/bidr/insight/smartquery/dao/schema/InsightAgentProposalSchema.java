package com.bidr.insight.smartquery.dao.schema;

import com.bidr.insight.smartquery.dao.entity.InsightAgentProposal;
import com.bidr.kernel.mybatis.repository.BaseMybatisSchema;
import org.springframework.stereotype.Service;

/**
 * Title: InsightAgentProposalSchema
 * Description: Agent 问数资产变更建议表建表语句（启动时经 MybatisPlusConfig 自动初始化）
 *
 * @author Sharp
 * @since 2026/8/19
 */
@Service
public class InsightAgentProposalSchema extends BaseMybatisSchema<InsightAgentProposal> {
    static {
        setCreateDDL("CREATE TABLE IF NOT EXISTS `insight_agent_proposal` (\n" +
                "  `id` int(11) NOT NULL AUTO_INCREMENT COMMENT '主键',\n" +
                "  `agent_code` varchar(50) NOT NULL DEFAULT '' COMMENT 'Agent 编码',\n" +
                "  `batch_no` varchar(40) NOT NULL DEFAULT '' COMMENT '批次号（一次提问维护产出一批）',\n" +
                "  `question_text` varchar(2000) DEFAULT NULL COMMENT '触发维护的自然语言问题',\n" +
                "  `semantic_query` longtext COMMENT '基于建议资产命中的 semantic_query 原文',\n" +
                "  `asset_type` varchar(30) NOT NULL DEFAULT '' COMMENT '资产类型（metrics/dimensions/relations/value-domains/concepts/sensitive-fields）',\n" +
                "  `item_key` varchar(100) NOT NULL DEFAULT '' COMMENT '资产项标识（指标/维度/关系名、码值域键、概念名等）',\n" +
                "  `op` varchar(10) NOT NULL DEFAULT 'add' COMMENT '变更动作（add=新增 update=修改）',\n" +
                "  `content` longtext COMMENT '单项资产 JSON 原文',\n" +
                "  `reason` varchar(1000) DEFAULT NULL COMMENT 'LLM 给出的建议理由',\n" +
                "  `status` char(1) DEFAULT '0' COMMENT '状态（0=待审 1=已合并 2=已驳回）',\n" +
                "  `create_by` bigint(20) DEFAULT NULL COMMENT '创建者',\n" +
                "  `create_at` datetime DEFAULT NULL COMMENT '创建时间',\n" +
                "  `update_by` bigint(20) DEFAULT NULL COMMENT '更新者',\n" +
                "  `update_at` datetime DEFAULT NULL COMMENT '更新时间',\n" +
                "  PRIMARY KEY (`id`),\n" +
                "  KEY `idx_agent_status` (`agent_code`, `status`)\n" +
                ") COMMENT='Agent 问数资产变更建议表';");
    }
}
