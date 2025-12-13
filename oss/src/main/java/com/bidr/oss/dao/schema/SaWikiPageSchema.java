package com.bidr.oss.dao.schema;

import com.bidr.kernel.mybatis.repository.BaseMybatisSchema;
import com.bidr.oss.dao.entity.SaWikiPage;
import org.springframework.stereotype.Service;

/**
 * Wiki页面Schema
 *
 * @author sharp
 * @since 2025-12-12
 */
@Service
public class SaWikiPageSchema extends BaseMybatisSchema<SaWikiPage> {
    static {
        setCreateDDL("CREATE TABLE IF NOT EXISTS `sa_wiki_page` (\n" +
                "  `id` bigint(20) NOT NULL AUTO_INCREMENT COMMENT '页面ID',\n" +
                "  `title` varchar(200) NOT NULL COMMENT '页面标题',\n" +
                "  `content` longtext COMMENT '页面内容(JSON格式)',\n" +
                "  `content_html` longtext COMMENT '页面内容(HTML格式)',\n" +
                "  `parent_id` bigint(20) DEFAULT NULL COMMENT '父级页面ID',\n" +
                "  `sort_order` int(11) NOT NULL DEFAULT '0' COMMENT '排序号',\n" +
                "  `author_id` varchar(50) NOT NULL COMMENT '作者用户ID',\n" +
                "  `status` char(1) NOT NULL DEFAULT '1' COMMENT '状态: 1-草稿, 2-已发布',\n" +
                "  `is_public` char(1) NOT NULL DEFAULT '1' COMMENT '是否公开: 0-私有, 1-公开',\n" +
                "  `view_count` bigint(20) NOT NULL DEFAULT '0' COMMENT '浏览次数',\n" +
                "  `version` int(11) NOT NULL DEFAULT '1' COMMENT '版本号',\n" +
                "  `create_by` varchar(50) DEFAULT NULL COMMENT '创建者',\n" +
                "  `create_at` datetime(3) DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',\n" +
                "  `update_by` varchar(50) DEFAULT NULL COMMENT '更新者',\n" +
                "  `update_at` datetime(3) DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',\n" +
                "  `valid` char(1) DEFAULT '1' COMMENT '有效性',\n" +
                "  PRIMARY KEY (`id`),\n" +
                "  KEY `idx_parent_id` (`parent_id`),\n" +
                "  KEY `idx_author_id` (`author_id`),\n" +
                "  KEY `idx_status` (`status`)\n" +
                ") COMMENT='Wiki页面';");
        setUpgradeDDL(1, "ALTER TABLE `sa_wiki_page`\n" +
                " CHANGE COLUMN `update_at` `update_at` DATETIME(3) NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '更新时间' AFTER `update_by`;");
        setUpgradeDDL(2, "ALTER TABLE `sa_wiki_page`\n" +
                " ADD COLUMN `modify_at` DATETIME(3) NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '内容更新时间' AFTER `author_id`;");
    }
}
