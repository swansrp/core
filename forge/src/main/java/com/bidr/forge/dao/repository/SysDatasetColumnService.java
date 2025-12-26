package com.bidr.forge.dao.repository;

import com.baomidou.dynamic.datasource.annotation.DS;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.bidr.forge.dao.entity.SysDatasetColumn;
import com.bidr.forge.dao.mapper.SysDatasetColumnMapper;
import com.bidr.kernel.mybatis.repository.BaseSqlRepo;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 数据集列配置 Repository Service
 *
 * @author sharp
 * @since 2025-11-25
 */
@Service
public class SysDatasetColumnService extends BaseSqlRepo<SysDatasetColumnMapper, SysDatasetColumn> {

    /**
     * 根据数据集ID查询所有列配置
     */
    public List<SysDatasetColumn> getByDatasetId(Long datasetId) {
        LambdaQueryWrapper<SysDatasetColumn> wrapper = getQueryWrapper();
        wrapper.eq(SysDatasetColumn::getDatasetId, datasetId);
        wrapper.orderByAsc(SysDatasetColumn::getDisplayOrder);
        return super.select(wrapper);
    }

    /**
     * 根据数据集ID删除所有列配置
     */
    public void deleteByDatasetId(Long datasetId) {
        LambdaQueryWrapper<SysDatasetColumn> wrapper = getQueryWrapper();
        wrapper.eq(SysDatasetColumn::getDatasetId, datasetId);
        super.delete(wrapper);
    }
}
