package com.bidr.oss.dao.schema;

import com.bidr.kernel.mybatis.repository.BaseMybatisSchema;
import com.bidr.oss.dao.entity.SaWikiCollaborator;
import org.springframework.stereotype.Service;

/**
 * Wiki协作者Schema
 *
 * @author sharp
 * @since 2025-12-12
 */
@Service
public class SaWikiCollaboratorSchema extends BaseMybatisSchema<SaWikiCollaborator> {
    static {
        setCreateDDL("CREATE TABLE IF NOT EXISTS `sa_wiki_collaborator` (\n" +
                "  `page_id` bigint(20) NOT NULL COMMENT '页面ID',\n" +
                "  `user_id` varchar(50) NOT NULL COMMENT '用户ID',\n" +
                "  `permission` char(1) NOT NULL DEFAULT '1' COMMENT '权限类型: 1-只读, 2-编辑',\n" +
                "  `status` char(1) NOT NULL DEFAULT '0' COMMENT '状态: 0-待审批, 1-已通过, 2-已拒绝',\n" +
                "  `request_msg` varchar(500) DEFAULT NULL COMMENT '申请说明',\n" +
                "  `create_by` varchar(50) DEFAULT NULL COMMENT '创建者',\n" +
                "  `create_at` datetime(3) DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',\n" +
                "  `update_by` varchar(50) DEFAULT NULL COMMENT '更新者',\n" +
                "  `update_at` datetime(3) DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',\n" +
                "  PRIMARY KEY (`page_id`, `user_id`),\n" +
                "  KEY `idx_page_id` (`page_id`),\n" +
                "  KEY `idx_user_id` (`user_id`)\n" +
                ") COMMENT='Wiki协作者';");
    }
}
