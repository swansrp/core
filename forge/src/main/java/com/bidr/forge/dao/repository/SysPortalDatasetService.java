package com.bidr.forge.dao.repository;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.bidr.forge.dao.entity.SysPortalDataset;
import com.bidr.forge.dao.mapper.SysPortalDatasetMapper;
import com.bidr.kernel.mybatis.repository.BaseSqlRepo;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * @author Sharp
 */
@Service
public class SysPortalDatasetService extends BaseSqlRepo<SysPortalDatasetMapper, SysPortalDataset> {

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
