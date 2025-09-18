package com.bidr.admin.dao.repository;

import com.bidr.admin.dao.entity.SysPortalDatasetColumn;
import com.bidr.admin.dao.mapper.SysPortalDatasetColumnMapper;
import com.bidr.kernel.mybatis.repository.BaseSqlRepo;
import org.springframework.stereotype.Service;

/**
 * @author Sharp
 */
@Service
public class SysPortalDatasetColumnService extends BaseSqlRepo<SysPortalDatasetColumnMapper, SysPortalDatasetColumn> {
    static {
        setCreateDDL("CREATE TABLE IF NOT EXISTS `sys_portal_dataset_column` (\n" +
                "  `id` bigint NOT NULL AUTO_INCREMENT,\n" +
                "  `table_id` varchar(100) NOT NULL COMMENT '关联的表格ID',\n" +
                "  `column_sql` longtext NOT NULL COMMENT '字段SQL表达',\n" +
                "  `column_alias` varchar(100) NOT NULL COMMENT '字段别名',\n" +
                "  `is_aggregate` varchar(1) NOT NULL DEFAULT '0' COMMENT '是否是聚合字段',\n" +
                "  `display_order` int NOT NULL DEFAULT '0' COMMENT '前端显示排序',\n" +
                "  `is_visible` varchar(1) NOT NULL DEFAULT '1' COMMENT '是否显示在结果集中',\n" +
                "  `remark` varchar(255) DEFAULT NULL,\n" +
                "  `create_by` varchar(50) DEFAULT NULL COMMENT '创建者',\n" +
                "  `create_at` datetime(3) DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',\n" +
                "  `update_by` varchar(50) DEFAULT NULL COMMENT '更新者',\n" +
                "  `update_at` datetime(3) DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',\n" +
                "  PRIMARY KEY (`id`)\n" +
                ") COMMENT='低代码表格列';");
    }
}
