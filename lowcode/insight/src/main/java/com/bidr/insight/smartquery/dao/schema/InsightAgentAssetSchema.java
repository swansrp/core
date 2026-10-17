package com.bidr.insight.smartquery.dao.schema;

import com.bidr.insight.smartquery.dao.entity.InsightAgentAsset;
import com.bidr.kernel.mybatis.repository.BaseMybatisSchema;
import org.springframework.stereotype.Service;

/**
 * Title: InsightAgentAssetSchema
 * Description: Agent 语义层资产存储表建表语句（启动时经 MybatisPlusConfig 自动初始化）
 *
 * @author Sharp
 * @since 2026/8/18
 */
@Service
public class InsightAgentAssetSchema extends BaseMybatisSchema<InsightAgentAsset> {
    static {
        setCreateDDL("CREATE TABLE IF NOT EXISTS `insight_agent_asset` (\n" +
                "  `id` int(11) NOT NULL AUTO_INCREMENT COMMENT '主键',\n" +
                "  `agent_code` varchar(50) NOT NULL DEFAULT '' COMMENT 'Agent 编码',\n" +
                "  `asset_type` varchar(30) NOT NULL DEFAULT '' COMMENT '资产类型（entities/metrics/dimensions/relations/value-domains/concepts）',\n" +
                "  `content` longtext COMMENT '资产 JSON 全文',\n" +
                "  `published_content` longtext COMMENT '发布快照（运行期缓存加载列；发布时由 content 拷入，改稿不触碰）',\n" +
                "  `status` char(1) DEFAULT '0' COMMENT '状态（0=草稿 1=已发布）',\n" +
                "  `create_by` bigint(20) DEFAULT NULL COMMENT '创建者',\n" +
                "  `create_at` datetime DEFAULT NULL COMMENT '创建时间',\n" +
                "  `update_by` bigint(20) DEFAULT NULL COMMENT '更新者',\n" +
                "  `update_at` datetime DEFAULT NULL COMMENT '更新时间',\n" +
                "  PRIMARY KEY (`id`),\n" +
                "  UNIQUE KEY `uk_agent_asset` (`agent_code`, `asset_type`)\n" +
                ") COMMENT='Agent 语义层资产存储表';");
    }
}
