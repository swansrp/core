package com.bidr.insight.dao.schema;

import com.bidr.insight.dao.entity.ChatBiSensitiveColumn;
import com.bidr.kernel.mybatis.repository.BaseMybatisSchema;
import org.springframework.stereotype.Service;

/**
 * Title: ChatBiSensitiveColumnSchema
 * Description: 智能问数敏感列配置表 Schema Service——
 * 初始化建表（InitDDL）与后续结构变更（UpgradeDDL）完全由 Java 控制
 *
 * @author Sharp
 * @since 2026/8/16
 */
@Service
public class ChatBiSensitiveColumnSchema extends BaseMybatisSchema<ChatBiSensitiveColumn> {
    static {
        setCreateDDL("CREATE TABLE IF NOT EXISTS `insight_chatbi_sensitive_column` (\n" +
                "  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',\n" +
                "  `table_code` varchar(100) NOT NULL COMMENT '看板名（sys_portal.name，即语义目录 tableId）',\n" +
                "  `column_property` varchar(100) NOT NULL COMMENT '敏感列属性名（portal 模式=column.property，DATASET 模式=columnAlias）',\n" +
                "  `column_label` varchar(100) DEFAULT NULL COMMENT '显示名快照（审计用）',\n" +
                "  `replace_property` varchar(100) DEFAULT NULL COMMENT '配对替换列属性名（如 项目名称→项目编号）',\n" +
                "  `create_by` varchar(50) DEFAULT NULL COMMENT '创建者',\n" +
                "  `create_at` datetime DEFAULT NULL COMMENT '创建时间',\n" +
                "  `update_by` varchar(50) DEFAULT NULL COMMENT '更新者',\n" +
                "  `update_at` datetime DEFAULT NULL COMMENT '更新时间',\n" +
                "  PRIMARY KEY (`id`),\n" +
                "  UNIQUE KEY `uk_table_column` (`table_code`, `column_property`)\n" +
                ") COMMENT='智能问数敏感列配置（一列一行，整板覆盖保存）';");
    }
}
