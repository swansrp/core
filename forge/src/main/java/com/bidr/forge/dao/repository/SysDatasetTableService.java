package com.bidr.forge.dao.repository;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.bidr.forge.dao.entity.SysDatasetTable;
import com.bidr.forge.dao.mapper.SysDatasetTableMapper;
import com.bidr.kernel.mybatis.repository.BaseSqlRepo;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 数据集关联表 Repository Service
 *
 * @author sharp
 * @since 2025-11-25
 */
@Service
public class SysDatasetTableService extends BaseSqlRepo<SysDatasetTableMapper, SysDatasetTable> {

    /**
     * 根据数据集ID查询所有关联表配置
     */
    public List<SysDatasetTable> getByDatasetId(Long datasetId) {
        LambdaQueryWrapper<SysDatasetTable> wrapper = getQueryWrapper();
        wrapper.eq(SysDatasetTable::getDatasetId, datasetId);
        wrapper.orderByAsc(SysDatasetTable::getTableOrder);
        return super.select(wrapper);
    }

    /**
     * 根据数据集ID删除所有关联表配置
     */
    public void deleteByDatasetId(Long datasetId) {
        LambdaQueryWrapper<SysDatasetTable> wrapper = getQueryWrapper();
        wrapper.eq(SysDatasetTable::getDatasetId, datasetId);
        super.delete(wrapper);
    }
}
