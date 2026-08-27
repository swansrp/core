package com.bidr.insight.smartquery.dao.schema;

import com.bidr.insight.smartquery.dao.entity.InsightAgentEscapeLog;
import com.bidr.kernel.mybatis.repository.BaseMybatisSchema;
import org.springframework.stereotype.Service;

/**
 * Title: InsightAgentEscapeLogSchema
 * Description: Agent SQL 兜底通道命中台账建表语句（启动时经 MybatisPlusConfig 自动初始化）
 *
 * @author Sharp
 * @since 2026/8/23
 */
@Service
public class InsightAgentEscapeLogSchema extends BaseMybatisSchema<InsightAgentEscapeLog> {
    static {
        setCreateDDL("CREATE TABLE IF NOT EXISTS `insight_agent_escape_log` (\n" +
                "  `id` int(11) NOT NULL AUTO_INCREMENT COMMENT '主键',\n" +
                "  `agent_code` varchar(50) NOT NULL DEFAULT '' COMMENT 'Agent 编码',\n" +
                "  `question` text COMMENT '用户原问题（兜底触发当次的完整问题文本）',\n" +
                "  `sql_text` mediumtext COMMENT '兜底实际执行的 SQL（经只读守卫验证后的文本）',\n" +
                "  `note` varchar(1000) DEFAULT NULL COMMENT '口径说明（LLM note 字段，可空）',\n" +
                "  `create_by` bigint(20) DEFAULT NULL COMMENT '创建者（问数发起用户）',\n" +
                "  `create_at` datetime DEFAULT NULL COMMENT '创建时间',\n" +
                "  PRIMARY KEY (`id`),\n" +
                "  KEY `idx_agent_time` (`agent_code`, `create_at`)\n" +
                ") COMMENT='Agent SQL 兜底通道命中台账（结晶信号源）';");
    }
}
