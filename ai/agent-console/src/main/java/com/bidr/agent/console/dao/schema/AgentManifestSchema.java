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
 * <p>建表即冻结：棒 2 增量加的两列（提示词全文、对话定位）走 {@code setUpgradeDDL} 追加，
 * createDDL 一字未动。这两列是行内最重的载荷，读口默认不下发（见 {@code AgentManifestController}）。</p>
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
                ") COMMENT='agent 会话装配单（工具面定形那一刻落一行，永久留存；记\"这场会话手里有什么\"：工具名清单/模型档/技能与版本/提示词段。只读诊断证据，不是运行期输入——本表不参与空间行级隔离，也不进会话决策'");

        // M14 棒2 增量：装配单从"手里有什么"扩到"当时被怎么说、跟谁聊过"。
        // 两列都不进默认响应（读口 ?include= 显式展开，见 AgentManifestController 的权限闸）
        setUpgradeDDL(1, "ALTER TABLE `sys_agent_manifest`\n" +
                "\tADD COLUMN `prompt_text` MEDIUMTEXT NULL COMMENT '提示词全文：模型首轮收到的 system 槽原文，即 prompt_sections 那几段拼出来的真身（超长截尾留标记；业务按惯例把内容放 user 槽时这里只有框架纪律）' AFTER `prompt_sections`,\n" +
                "\tADD COLUMN `conversation_id` VARCHAR(64) NULL COMMENT '历史对话定位：对话正文按保留期滚窗过期，本列永久——有值即\"当时确有一条对话\"，读不回正文＝已过保留期，NULL＝该会话没接对话层（纯后台型）' AFTER `extras`;\n");
    }
}
