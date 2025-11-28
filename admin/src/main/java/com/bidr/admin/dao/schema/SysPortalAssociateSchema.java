package com.bidr.admin.dao.schema;

import com.bidr.admin.dao.entity.SysPortalAssociate;
import com.bidr.kernel.mybatis.repository.BaseMybatisSchema;
import org.springframework.stereotype.Service;

/**
 * Title: SysPortalAssociateSchema
 * @author Sharp
 * @since 2025/11/28
 */
@Service
public class SysPortalAssociateSchema extends BaseMybatisSchema<SysPortalAssociate> {
    static {
        setCreateDDL("CREATE TABLE IF NOT EXISTS `sys_portal_associate` (\n" +
                "  `id` bigint(20) NOT NULL AUTO_INCREMENT COMMENT 'id',\n" +
                "  `role_id` bigint(20) NOT NULL COMMENT '角色id',\n" +
                "  `portal_id` bigint(20) NOT NULL COMMENT '实体id',\n" +
                "  `title` varchar(50) NOT NULL COMMENT '显示名称',\n" +
                "  `bind_type` varchar(1) NOT NULL DEFAULT '0' COMMENT '实体关系',\n" +
                "  `bind_portal_id` bigint(20) NOT NULL COMMENT '目标实体id',\n" +
                "  `bind_property` varchar(50) NOT NULL COMMENT '关联字段名',\n" +
                "  `bind_sort_property` varchar(50) NOT NULL DEFAULT '' COMMENT '默认排序字段',\n" +
                "  `bind_sort_type` varchar(1) NOT NULL DEFAULT '1' COMMENT '默认排序方式',\n" +
                "  `tree_mode` varchar(1) NOT NULL DEFAULT '0' COMMENT '树形展示',\n" +
                "  `tree_check_strict` varchar(1) NOT NULL DEFAULT '0' COMMENT '树形结构显示是否严格节点显示',\n" +
                "  `attach_condition` json DEFAULT NULL COMMENT '查询条件',\n" +
                "  `display_order` int(11) NOT NULL DEFAULT '0' COMMENT '显示顺序',\n" +
                "  PRIMARY KEY (`id`)\n" +
                ") COMMENT='关联表格配置';");
    }
}
