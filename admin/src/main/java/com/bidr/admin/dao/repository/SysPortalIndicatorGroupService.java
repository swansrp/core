package com.bidr.admin.dao.repository;

import com.bidr.admin.dao.entity.SysPortalIndicatorGroup;
import com.bidr.admin.dao.mapper.SysPortalIndicatorGroupMapper;
import com.bidr.kernel.mybatis.inf.MybatisPlusTableInitializerInf;
import com.bidr.kernel.mybatis.repository.BaseSqlRepo;
import org.springframework.stereotype.Service;

/**
 * @author Sharp
 */
@Service
public class SysPortalIndicatorGroupService extends BaseSqlRepo<SysPortalIndicatorGroupMapper, SysPortalIndicatorGroup> implements MybatisPlusTableInitializerInf {

    @Override
    public String getSql() {
        return "CREATE TABLE IF NOT EXISTS `sys_portal_indicator_group` (\n" +
                "  `id` bigint NOT NULL AUTO_INCREMENT COMMENT 'id',\n" +
                "  `pid` bigint DEFAULT NULL COMMENT '父级指标id',\n" +
                "  `portal_name` varchar(50) NOT NULL COMMENT '实体名称',\n" +
                "  `name` varchar(50) NOT NULL COMMENT '指标名称',\n" +
                "  `display_order` int NOT NULL COMMENT '排序',\n" +
                "  PRIMARY KEY (`id`) USING BTREE,\n" +
                "  KEY `pid` (`pid`),\n" +
                "  KEY `portal_id` (`portal_name`) USING BTREE\n" +
                ") COMMENT='统计指标组';";
    }
}
