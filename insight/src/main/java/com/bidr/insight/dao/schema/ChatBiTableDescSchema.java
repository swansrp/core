package com.bidr.insight.dao.schema;

import com.bidr.insight.dao.entity.ChatBiTableDesc;
import com.bidr.kernel.mybatis.repository.BaseMybatisSchema;
import org.springframework.stereotype.Service;

/**
 * Title: ChatBiTableDescSchema
 * Description: 智能问数看板业务描述表 Schema Service——
 * 初始化建表（InitDDL）与后续结构变更（UpgradeDDL）完全由 Java 控制
 *
 * @author Sharp
 * @since 2026/8/15
 */
@Service
public class ChatBiTableDescSchema extends BaseMybatisSchema<ChatBiTableDesc> {
    static {
        setCreateDDL("CREATE TABLE IF NOT EXISTS `insight_chatbi_table_desc` (\n" +
                "  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',\n" +
                "  `table_code` varchar(100) NOT NULL COMMENT '看板名（sys_portal.name，即路由候选的 tableId）',\n" +
                "  `description` varchar(1000) DEFAULT NULL COMMENT '业务描述（供大模型路由选择看板，如：生产项目全生命周期，含产值/成本/结余等指标）',\n" +
                "  `create_by` varchar(50) DEFAULT NULL COMMENT '创建者',\n" +
                "  `create_at` datetime DEFAULT NULL COMMENT '创建时间',\n" +
                "  `update_by` varchar(50) DEFAULT NULL COMMENT '更新者',\n" +
                "  `update_at` datetime DEFAULT NULL COMMENT '更新时间',\n" +
                "  PRIMARY KEY (`id`),\n" +
                "  UNIQUE KEY `uk_table_code` (`table_code`)\n" +
                ") COMMENT='智能问数看板描述';");
        // v2：关联键从 sys_portal_table.table_code 迁移为 sys_portal.name（portalName），
        // 存量描述经 sys_portal_table 换算回写；幂等（已迁移或无存量时匹配 0 行）
        setUpgradeDDL(2, "UPDATE `insight_chatbi_table_desc` d JOIN `sys_portal_table` t ON d.`table_code` = t.`table_code` " +
                "SET d.`table_code` = t.`portal_name` WHERE d.`table_code` <> t.`portal_name`");
    }
}
