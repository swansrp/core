package com.bidr.admin.dao.repository;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.bidr.admin.dao.entity.SysPortalDatasetColumn;
import com.bidr.admin.dao.mapper.SysPortalDatasetColumnMapper;
import com.bidr.kernel.mybatis.repository.BaseSqlRepo;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * @author Sharp
 */
@Service
public class SysPortalDatasetColumnService extends BaseSqlRepo<SysPortalDatasetColumnMapper, SysPortalDatasetColumn> {

    public List<SysPortalDatasetColumn> getByTableId(String tableId) {
        LambdaQueryWrapper<SysPortalDatasetColumn> wrapper = getQueryWrapper();
        wrapper.eq(SysPortalDatasetColumn::getTableId, tableId);
        wrapper.orderByAsc(SysPortalDatasetColumn::getDisplayOrder);
        return super.select(wrapper);
    }

    public void deleteByTableId(String tableId) {
        LambdaQueryWrapper<SysPortalDatasetColumn> wrapper = getQueryWrapper();
        wrapper.eq(SysPortalDatasetColumn::getTableId, tableId);
        super.delete(wrapper);
    }
}
