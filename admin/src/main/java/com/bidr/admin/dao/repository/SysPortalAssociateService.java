package com.bidr.admin.dao.repository;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.bidr.admin.dao.entity.SysPortalAssociate;
import com.bidr.admin.dao.mapper.SysPortalAssociateMapper;
import com.bidr.kernel.mybatis.repository.BaseSqlRepo;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Title: SysPortalAssociateService
 * Description: Copyright: Copyright (c) 2024
 *
 * @author Sharp
 * @since 2024/7/3 13:34
 */
@Service
public class SysPortalAssociateService extends BaseSqlRepo<SysPortalAssociateMapper, SysPortalAssociate> {
    public List<SysPortalAssociate> getPropertyListByPortalId(Long portalId, Long roleId) {
        LambdaQueryWrapper<SysPortalAssociate> wrapper = super.getQueryWrapper();
        wrapper.eq(SysPortalAssociate::getPortalId, portalId);
        wrapper.eq(SysPortalAssociate::getRoleId, roleId);
        return select(wrapper);
    }
}
