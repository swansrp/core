package com.bidr.platform.dao.repository;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.bidr.kernel.mybatis.repository.BaseSqlRepo;
import com.bidr.platform.dao.entity.SysPortalColumn;
import com.bidr.platform.dao.mapper.SysPortalColumnMapper;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Title: SysPortalColumnService
 * Description: Copyright: Copyright (c) 2023
 *
 * @author Sharp
 * @since 2023/11/21 18:00
 */
@Service
public class SysPortalColumnService extends BaseSqlRepo<SysPortalColumnMapper, SysPortalColumn> {

    public boolean existed(Long portalId, String property) {
        LambdaQueryWrapper<SysPortalColumn> wrapper = super.getQueryWrapper();
        wrapper.eq(SysPortalColumn::getPortalId, portalId);
        wrapper.eq(SysPortalColumn::getProperty, property);
        return existed(wrapper);
    }

    public void deleteByPortalId(String portalId) {
        LambdaQueryWrapper<SysPortalColumn> wrapper = super.getQueryWrapper();
        wrapper.eq(SysPortalColumn::getPortalId, portalId);
        delete(wrapper);
    }

    public List<SysPortalColumn> getPropertyListByPortalId(Long portalId) {
        LambdaQueryWrapper<SysPortalColumn> wrapper = super.getQueryWrapper();
        wrapper.select(SysPortalColumn::getId, SysPortalColumn::getProperty);
        wrapper.eq(SysPortalColumn::getPortalId, portalId);
        return select(wrapper);
    }
}
