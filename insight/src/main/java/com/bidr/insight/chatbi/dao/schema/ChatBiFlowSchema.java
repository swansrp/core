package com.bidr.insight.chatbi.dao.schema;

import com.bidr.insight.chatbi.dao.entity.ChatBiFlow;
import com.bidr.kernel.mybatis.repository.BaseMybatisSchema;
import org.springframework.stereotype.Service;

/**
 * Title: ChatBiFlowSchema
 * Description: 智能问数流程编排表 Schema Service——
 * 初始化建表（InitDDL）与后续结构变更（UpgradeDDL）完全由 Java 控制；
 * 库中无记录时引擎回落代码内置默认链，不插默认数据
 *
 * @author Sharp
 * @since 2026/8/15
 */
@Service
public class ChatBiFlowSchema extends BaseMybatisSchema<ChatBiFlow> {
    static {
        setCreateDDL("CREATE TABLE IF NOT EXISTS `insight_chatbi_flow` (\n" +
                "  `flow_key` varchar(50) NOT NULL COMMENT '流程标识（route-看板路由 ask-看板问答）',\n" +
                "  `name` varchar(100) NOT NULL COMMENT '流程名称',\n" +
                "  `graph` longtext NOT NULL COMMENT 'DAG 定义（nodes+edges JSON，含提示词模板与结点坐标）',\n" +
                "  `create_by` varchar(50) DEFAULT NULL COMMENT '创建者',\n" +
                "  `create_at` datetime DEFAULT NULL COMMENT '创建时间',\n" +
                "  `update_by` varchar(50) DEFAULT NULL COMMENT '更新者',\n" +
                "  `update_at` datetime DEFAULT NULL COMMENT '更新时间',\n" +
                "  PRIMARY KEY (`flow_key`)\n" +
                ") COMMENT='智能问数流程编排';");
    }
}
