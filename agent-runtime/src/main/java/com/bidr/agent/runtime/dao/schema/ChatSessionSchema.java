package com.bidr.agent.runtime.dao.schema;

import com.bidr.agent.runtime.dao.entity.ChatSession;
import com.bidr.kernel.mybatis.repository.BaseMybatisSchema;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

/**
 * AI对话会话映射（业务归属与业务绑定）表结构定义（DDL 正源，建表即冻结，变更只走 setUpgradeDDL 追加）
 * <p>
 * 2026-09-24 下沉框架时改名：项目表 `chat_session` → 框架平台表 `sys_agent_session`
 * （框架惯例：平台表带 sys_ 前缀、表名不带 schema 前缀，由数据源默认库决定）。
 * 因是**新表**，原 v1 升级语句（加 message_count）已并入 createDDL，不再保留版本号。
 *
 * @author sharp
 */
@Service
@ConditionalOnProperty(prefix = "my.agent.runtime", name = "enabled", havingValue = "true")
public class ChatSessionSchema extends BaseMybatisSchema<ChatSession> {

    static {
        setCreateDDL("CREATE TABLE IF NOT EXISTS `sys_agent_session` (\n" +
                "  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键',\n" +
                "  `session_id` varchar(64) NOT NULL COMMENT '平台会话ID（Agent Runtime 的 session_id）',\n" +
                "  `agent_code` varchar(64) NOT NULL COMMENT '平台 Agent 编码（会话绑定，平台侧创建即冻结）',\n" +
                "  `operator` varchar(64) NOT NULL COMMENT '归属人（登录账号）：会话归属强校验与列表过滤依据',\n" +
                "  `group_key` varchar(128) DEFAULT NULL COMMENT '业务分组键（随建会话提交平台，平台侧会话列表按它过滤）',\n" +
                "  `business_type` varchar(64) DEFAULT NULL COMMENT '业务类型（业务绑定维度，如 project/contract）',\n" +
                "  `business_id` varchar(64) DEFAULT NULL COMMENT '业务主键（业务绑定维度，如项目ID）',\n" +
                "  `title` varchar(255) DEFAULT NULL COMMENT '会话标题（本地维护：平台 name 创建即冻结）',\n" +
                "  `last_active_at` datetime DEFAULT NULL COMMENT '最后活跃时间（列表排序用）',\n" +
                "  `message_count` int DEFAULT 0 COMMENT '会话消息数（每轮 user+assistant 计 2，列表展示用）',\n" +
                "  `valid` int DEFAULT 1 COMMENT '是否有效：1有效 0无效（会话删除或新对话解绑）',\n" +
                "  `create_by` varchar(64) DEFAULT NULL COMMENT '创建人',\n" +
                "  `create_at` datetime DEFAULT NULL COMMENT '创建时间',\n" +
                "  `update_by` varchar(64) DEFAULT NULL COMMENT '更新人',\n" +
                "  `update_at` datetime DEFAULT NULL COMMENT '更新时间',\n" +
                "  PRIMARY KEY (`id`),\n" +
                "  UNIQUE KEY `uk_sys_agent_session_session_id` (`session_id`),\n" +
                "  KEY `idx_sys_agent_session_operator` (`operator`, `valid`)\n" +
                ") COMMENT='AI对话会话映射（业务归属与业务绑定）';");
    }
}
