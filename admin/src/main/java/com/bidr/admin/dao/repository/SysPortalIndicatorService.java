package com.bidr.admin.dao.repository;

import com.bidr.admin.dao.entity.SysPortalIndicator;
import com.bidr.admin.dao.mapper.SysPortalIndicatorMapper;
import com.bidr.kernel.mybatis.inf.MybatisPlusTableInitializerInf;
import com.bidr.kernel.mybatis.repository.BaseSqlRepo;
import org.springframework.stereotype.Service;

/**
 * @author Sharp
 */
@Service
public class SysPortalIndicatorService extends BaseSqlRepo<SysPortalIndicatorMapper, SysPortalIndicator> implements MybatisPlusTableInitializerInf {

    static {
        setCreateDDL("CREATE TABLE IF NOT EXISTS `sys_portal_indicator` (\n" +
                "  `id` bigint NOT NULL AUTO_INCREMENT COMMENT 'id',\n" +
                "  `group_id` bigint NOT NULL DEFAULT '0' COMMENT '指标分组id',\n" +
                "  `item_value` varchar(100) NOT NULL COMMENT '指标项值',\n" +
                "  `item_name` varchar(100) NOT NULL DEFAULT '' COMMENT '指标项名称',\n" +
                "  `condition` longtext COMMENT '条件json',\n" +
                "  `dynamic_column` LONGTEXT NULL DEFAULT NULL COMMENT '动态字段map',\n" +
                "  `display_order` int DEFAULT '0' COMMENT '指标项排序',\n" +
                "  `remark` varchar(500) DEFAULT NULL COMMENT '备注',\n" +
                "  `create_by` varchar(50) DEFAULT NULL COMMENT '创建者',\n" +
                "  `create_at` datetime(3) DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',\n" +
                "  `update_by` varchar(50) DEFAULT NULL COMMENT '更新者',\n" +
                "  `update_at` datetime(3) DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',\n" +
                "  `valid` char(1) NOT NULL DEFAULT '1' COMMENT '有效性',\n" +
                "  PRIMARY KEY (`id`) USING BTREE,\n" +
                "  KEY `group_id` (`group_id`)\n" +
                ") COMMENT='统计指标';");
    }
}
