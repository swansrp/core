package com.bidr.admin.dao.repository;


import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.bidr.admin.dao.entity.SysPortalColumn;
import com.bidr.admin.dao.mapper.SysPortalColumnMapper;
import com.bidr.kernel.mybatis.repository.BaseSqlRepo;
import com.bidr.kernel.utils.FuncUtil;
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

    public boolean existed(Long portalId, String property, Long roleId) {
        LambdaQueryWrapper<SysPortalColumn> wrapper = super.getQueryWrapper();
        wrapper.eq(SysPortalColumn::getPortalId, portalId);
        wrapper.eq(SysPortalColumn::getProperty, property);
        wrapper.eq(SysPortalColumn::getRoleId, roleId);
        return existed(wrapper);
    }

    public void deleteByPortalId(Long portalId) {
        LambdaQueryWrapper<SysPortalColumn> wrapper = super.getQueryWrapper();
        wrapper.eq(SysPortalColumn::getPortalId, portalId);
        delete(wrapper);
    }

    public List<SysPortalColumn> getPropertyListByPortalId(Long portalId, Long roleId) {
        LambdaQueryWrapper<SysPortalColumn> wrapper = super.getQueryWrapper();
        wrapper.eq(SysPortalColumn::getPortalId, portalId);
        wrapper.eq(SysPortalColumn::getRoleId, roleId);
        return select(wrapper);
    }

    public boolean deleteByRoleId(Long roleId) {
        LambdaQueryWrapper<SysPortalColumn> wrapper = super.getQueryWrapper()
                .eq(SysPortalColumn::getRoleId, roleId);
        return super.delete(wrapper);
    }

    /**
     * 根据Portal ID列表批量删除字段配置
     *
     * @param portalIds Portal ID列表
     */
    public void deleteByPortalIds(List<Long> portalIds) {
        if (FuncUtil.isNotEmpty(portalIds)) {
            LambdaQueryWrapper<SysPortalColumn> wrapper = super.getQueryWrapper()
                    .in(SysPortalColumn::getPortalId, portalIds);
            super.delete(wrapper);
        }
    }
}
