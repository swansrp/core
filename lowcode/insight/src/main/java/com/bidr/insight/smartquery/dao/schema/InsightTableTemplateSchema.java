package com.bidr.insight.smartquery.dao.schema;

import com.bidr.insight.smartquery.dao.entity.InsightTableTemplate;
import com.bidr.kernel.mybatis.repository.BaseMybatisSchema;
import org.springframework.stereotype.Service;

/**
 * Title: InsightTableTemplateSchema
 * Description: 表级资产模板建表语句（启动时经 MybatisPlusConfig 自动初始化）
 *
 * @author Sharp
 * @since 2026/8/24
 */
@Service
public class InsightTableTemplateSchema extends BaseMybatisSchema<InsightTableTemplate> {
    static {
        setCreateDDL("CREATE TABLE IF NOT EXISTS `insight_table_template` (\n" +
                "  `id` int(11) NOT NULL AUTO_INCREMENT COMMENT '主键',\n" +
                "  `ds_name` varchar(100) NOT NULL DEFAULT '' COMMENT '数据源名（模板身份的一半）',\n" +
                "  `table_name` varchar(200) NOT NULL DEFAULT '' COMMENT '表全名（db.tbl 形式，模板身份的另一半）',\n" +
                "  `entity_json` longtext COMMENT '已确认实体 JSON（单个 EntityDef，含字段级人工结论）',\n" +
                "  `source_agent` varchar(50) DEFAULT NULL COMMENT '最近沉淀来源 Agent 编码（追溯用）',\n" +
                "  `create_by` bigint(20) DEFAULT NULL COMMENT '创建者',\n" +
                "  `create_at` datetime DEFAULT NULL COMMENT '创建时间',\n" +
                "  `update_by` bigint(20) DEFAULT NULL COMMENT '更新者',\n" +
                "  `update_at` datetime DEFAULT NULL COMMENT '更新时间',\n" +
                "  PRIMARY KEY (`id`),\n" +
                "  UNIQUE KEY `uk_ds_table` (`ds_name`, `table_name`(100))\n" +
                ") COMMENT='表级资产模板（跨 Agent 复用）';");
    }
}
