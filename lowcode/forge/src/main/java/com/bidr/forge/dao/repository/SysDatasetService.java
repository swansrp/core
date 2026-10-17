package com.bidr.forge.dao.repository;

import com.bidr.admin.dao.entity.SysPortal;
import com.bidr.forge.bo.DatasetColumns;
import com.bidr.forge.dao.entity.SysDataset;
import com.bidr.forge.dao.entity.SysDatasetColumn;
import com.bidr.forge.dao.mapper.SysDatasetMapper;
import com.bidr.kernel.constant.CommonConst;
import com.bidr.kernel.mybatis.repository.BaseSqlRepo;
import com.github.yulichang.wrapper.MPJLambdaWrapper;
import org.springframework.stereotype.Service;

/**
 * 数据集主表 Repository Service
 *
 * @author sharp
 * @since 2025-11-25
 */
@Service
public class SysDatasetService extends BaseSqlRepo<SysDatasetMapper, SysDataset> {

    private static final String DATASET = "DATASET";

    /**
     * 按 portalName 获取 Dataset 配置以及列信息
     * Portal.referenceId 存的是 datasetId。
     */
    public DatasetColumns getDatasetColumnsByPortalName(String portalName) {
        MPJLambdaWrapper<SysDataset> wrapper = getMPJLambdaWrapper();
        wrapper.selectCollection(SysDatasetColumn.class, DatasetColumns::getColumns);
        wrapper.leftJoin(SysDatasetColumn.class, SysDatasetColumn::getDatasetId, SysDataset::getId);
        wrapper.leftJoin(SysPortal.class, SysPortal::getReferenceId, SysDataset::getId);
        wrapper.eq(SysPortal::getName, portalName);
        wrapper.eq(SysPortal::getDataMode, DATASET);

        // 如 Dataset/列 也有 valid 字段，则按 YES 过滤，保持与 Matrix 一致
        wrapper.eq(SysDataset::getValid, CommonConst.YES);

        return super.selectJoinOne(DatasetColumns.class, wrapper);
    }
}
