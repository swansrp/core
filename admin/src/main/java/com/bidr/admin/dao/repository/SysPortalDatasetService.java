package com.bidr.admin.dao.repository;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.bidr.admin.dao.entity.SysPortalDataset;
import com.bidr.admin.dao.mapper.SysPortalDatasetMapper;
import com.bidr.kernel.mybatis.repository.BaseSqlRepo;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * @author Sharp
 */
@Service
public class SysPortalDatasetService extends BaseSqlRepo<SysPortalDatasetMapper, SysPortalDataset> {
    static {
        setCreateDDL(
                "CREATE TABLE IF NOT EXISTS `sys_portal_dataset` (\n" + "  `id` bigint NOT NULL AUTO_INCREMENT,\n" +
                        "  `table_id` varchar(100) NOT NULL COMMENT '关联的表格ID',\n" +
                        "  `data_source` varchar(100) NULL COMMENT '多源数据库配置名称',\n" +
                        "  `dataset_order` int NOT NULL DEFAULT '0' COMMENT '表顺序',\n" +
                        "  `dataset_sql` longtext NOT NULL COMMENT '关联表',\n" +
                        "  `dataset_alias` varchar(100) NOT NULL COMMENT '表别名',\n" +
                        "  `join_type` varchar(1) DEFAULT NULL COMMENT 'JOIN类型，主表可为空',\n" +
                        "  `join_condition` longtext COMMENT 'ON 条件，主表可为空',\n" +
                        "  `remark` varchar(255) DEFAULT NULL COMMENT '备注',\n" +
                        "  `create_by` varchar(50) DEFAULT NULL COMMENT '创建者',\n" +
                        "  `create_at` datetime(3) DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',\n" +
                        "  `update_by` varchar(50) DEFAULT NULL COMMENT '更新者',\n" +
                        "  `update_at` datetime(3) DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',\n" +
                        "  PRIMARY KEY (`id`)\n" + ") COMMENT='低代码表格表视图';");
    }

    public List<SysPortalDataset> getByTableId(String tableId) {
        LambdaQueryWrapper<SysPortalDataset> wrapper = getQueryWrapper();
        wrapper.eq(SysPortalDataset::getTableId, tableId);
        wrapper.orderByAsc(SysPortalDataset::getDatasetOrder);
        return super.select(wrapper);
    }

    public void deleteByTableId(String tableId) {
        LambdaQueryWrapper<SysPortalDataset> wrapper = getQueryWrapper();
        wrapper.eq(SysPortalDataset::getTableId, tableId);
        super.delete(wrapper);
    }
}
