package com.bidr.agent.console.dao.schema;

import com.bidr.agent.console.dao.entity.AgentManifest;
import com.bidr.kernel.mybatis.repository.BaseMybatisSchema;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

/**
 * Title: AgentManifestSchema
 * Description: agent 会话装配单表结构定义（DDL 正源，建表即冻结，变更只走 setUpgradeDDL 追加）。
 *
 * <p>框架平台表按惯例带 {@code sys_} 前缀、表名不带 schema 前缀（同 {@code sys_agent_session}）。
 * 三栏 JSON 文本不做 DB 侧校验：本表是复盘证据不是运行期输入，写坏一栏最多是那一栏读不出来。</p>
 *
 * <p>开关 {@code agent-console.manifest.enabled}（默认开）：关掉即停写快照，
 * 历史行原样留在表里可读——回退不需要动数据。</p>
 *
 * @author Sharp
 * @since 2026-10-12
 */
@Service
@ConditionalOnProperty(prefix = "agent-console.manifest", name = "enabled", havingValue = "true", matchIfMissing = true)
public class AgentManifestSchema extends BaseMybatisSchema<AgentManifest> {

    static {
        setCreateDDL("CREATE TABLE IF NOT EXISTS `sys_agent_manifest`\n" +
                "(\n" +
                "    `id`              bigint(20)   NOT NULL AUTO_INCREMENT COMMENT 'id',\n" +
                "    `session_id`      varchar(64)  NOT NULL COMMENT '框架会话标识（一行一会话）',\n" +
                "    `agent_key`       varchar(64)  NOT NULL COMMENT 'agent 运行时注册键',\n" +
                "    `display_name`    varchar(128) DEFAULT NULL COMMENT 'agent 展示名（定形时刻的）',\n" +
                "    `asked_by`        varchar(50)  DEFAULT NULL COMMENT '发起人（框架 OperatorResolver 口径；列名避开 operator 关键字）',\n" +
                "    `tool_names`      text COMMENT '工具名清单（JSON 数组＝发给模型的那份，含引擎按上下文预算追加的回捞工具）',\n" +
                "    `tool_count`      int(11)      DEFAULT NULL COMMENT '工具只数（冗余列：列表页不解析 JSON 也能对账）',\n" +
                "    `model_tier`      varchar(128) DEFAULT NULL COMMENT '模型档（本次答话用的模型 id，业务贡献者补；NULL＝没交，不编造）',\n" +
                "    `skill_refs`      text COMMENT '注入的技能包与版本（JSON 数组，版本必填原因见 AgentManifestSkillRef）',\n" +
                "    `prompt_sections` text COMMENT '提示词段清单（JSON 数组，按拼装顺序，段名由业务定义）',\n" +
                "    `extras`          text COMMENT '业务标注（JSON 对象：featureId/spaceId 这类框架不解读的维度）',\n" +
                "    `create_by`       varchar(50)  DEFAULT NULL COMMENT '创建人（run 线程无登录上下文时为空，属正常）',\n" +
                "    `create_at`       datetime(3)  DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间（＝工具面定形时刻）',\n" +
                "    `update_at`       datetime(3)  DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间（同会话重定形才动）',\n" +
                "    `valid`           char(1)      DEFAULT '1' COMMENT '有效性（保留列随规范；装配单只插与覆盖、不删）',\n" +
                "    PRIMARY KEY (`id`),\n" +
                "    UNIQUE KEY `uk_sys_agent_manifest_session` (`session_id`),\n" +
                "    KEY `idx_sys_agent_manifest_agent` (`agent_key`, `id`)\n" +
                ") COMMENT='agent 会话装配单（工具面定形那一刻落一行，永久留存；记\"这场会话手里有什么\"：工具名清单/模型档/技能与版本/提示词段。只读诊断证据，不是运行期输入——本表不参与会话决策，也不进空间行级隔离清单'");
    }
}
