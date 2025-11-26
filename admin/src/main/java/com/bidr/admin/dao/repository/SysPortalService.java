package com.bidr.admin.dao.repository;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.bidr.admin.dao.entity.SysPortal;
import com.bidr.admin.dao.entity.SysPortalColumn;
import com.bidr.admin.dao.mapper.SysPortalMapper;
import com.bidr.admin.vo.PortalWithColumnsRes;
import com.bidr.kernel.constant.CommonConst;
import com.bidr.kernel.mybatis.repository.BaseSqlRepo;
import com.bidr.kernel.utils.FuncUtil;
import com.bidr.kernel.vo.common.KeyValueResVO;
import com.github.yulichang.wrapper.MPJLambdaWrapper;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Title: SysPortalService
 * Description: Copyright: Copyright (c) 2023
 *
 * @author Sharp
 * @since 2023/11/21 18:00
 */
@Service
public class SysPortalService extends BaseSqlRepo<SysPortalMapper, SysPortal> {

    public SysPortal getByName(String name, Long roleId) {
        LambdaQueryWrapper<SysPortal> wrapper = super.getQueryWrapper().eq(SysPortal::getName, name)
                .eq(FuncUtil.isNotEmpty(roleId), SysPortal::getRoleId, roleId);
        return super.selectOne(wrapper);
    }

    public List<SysPortal> getByBeanName(String dbEntityClassName, String name) {
        LambdaQueryWrapper<SysPortal> wrapper = super.getQueryWrapper().eq(SysPortal::getBean, dbEntityClassName)
                .eq(SysPortal::getName, name);
        return super.select(wrapper);
    }

    public List<SysPortal> getAllPortalList() {
        MPJLambdaWrapper<SysPortal> wrapper = new MPJLambdaWrapper<>();
        wrapper.orderByAsc(SysPortal::getDisplayName);
        return select(wrapper);
    }

    public List<KeyValueResVO> getPortalList(String name, Long roleId, String dataMode, Long referenceId) {
        MPJLambdaWrapper<SysPortal> wrapper = new MPJLambdaWrapper<>();
        wrapper.selectAs(SysPortal::getDisplayName, KeyValueResVO::getLabel);
        wrapper.selectAs(SysPortal::getName, KeyValueResVO::getValue);
        wrapper.eq(SysPortal::getRoleId, roleId);
        wrapper.nested(FuncUtil.isNotEmpty(name), wr -> {
            wr.like(SysPortal::getName, name);
            wr.or().like(SysPortal::getDisplayName, name);
        });
        wrapper.eq(FuncUtil.isNotEmpty(dataMode), SysPortal::getDataMode, dataMode);
        wrapper.eq(FuncUtil.isNotEmpty(referenceId), SysPortal::getReferenceId, referenceId);


        wrapper.orderByAsc(SysPortal::getDisplayName);
        return selectJoinList(KeyValueResVO.class, wrapper);
    }

    public Boolean existedByName(String name, Long roleId) {
        LambdaQueryWrapper<SysPortal> wrapper = super.getQueryWrapper().eq(SysPortal::getName, name)
                .eq(SysPortal::getRoleId, roleId);
        return super.existed(wrapper);
    }

    public PortalWithColumnsRes getImportPortal(String portalName, Long roleId) {
        MPJLambdaWrapper<SysPortal> wrapper = new MPJLambdaWrapper();
        wrapper.selectCollection(SysPortalColumn.class, PortalWithColumnsRes::getColumns);
        wrapper.leftJoin(SysPortalColumn.class, SysPortalColumn::getPortalId, SysPortal::getId);
        wrapper.eq(SysPortal::getRoleId, roleId);
        wrapper.eq(SysPortalColumn::getRoleId, roleId);
        wrapper.eq(SysPortalColumn::getEnable, CommonConst.YES);
        // wrapper.eq(SysPortalColumn::getAddShow, CommonConst.YES);
        wrapper.eq(SysPortal::getName, portalName);
        wrapper.orderByAsc(SysPortalColumn::getDisplayOrder);
        return selectJoinOne(PortalWithColumnsRes.class, wrapper);
    }

    public PortalWithColumnsRes getExportPortal(String portalName, Long roleId) {
        MPJLambdaWrapper<SysPortal> wrapper = new MPJLambdaWrapper();
        wrapper.selectCollection(SysPortalColumn.class, PortalWithColumnsRes::getColumns);
        wrapper.leftJoin(SysPortalColumn.class, SysPortalColumn::getPortalId, SysPortal::getId);
        wrapper.eq(SysPortal::getRoleId, roleId);
        wrapper.eq(SysPortalColumn::getRoleId, roleId);
        wrapper.eq(SysPortalColumn::getEnable, CommonConst.YES);
        wrapper.eq(SysPortalColumn::getDetailShow, CommonConst.YES);
        wrapper.eq(SysPortal::getName, portalName);
        wrapper.orderByAsc(SysPortalColumn::getDisplayOrder);
        return selectJoinOne(PortalWithColumnsRes.class, wrapper);
    }

    public List<PortalWithColumnsRes> getPortalWithColumnsByRoleId(Long roleId) {
        MPJLambdaWrapper<SysPortal> wrapper = new MPJLambdaWrapper();
        wrapper.selectCollection(SysPortalColumn.class, PortalWithColumnsRes::getColumns);
        wrapper.leftJoin(SysPortalColumn.class, SysPortalColumn::getPortalId, SysPortal::getId);
        wrapper.eq(SysPortal::getRoleId, roleId);
        wrapper.eq(SysPortalColumn::getRoleId, roleId);
        wrapper.orderByAsc(SysPortalColumn::getDisplayOrder);
        return selectJoinList(PortalWithColumnsRes.class, wrapper);
    }

    public boolean deleteByRoleId(Long roleId) {
        LambdaQueryWrapper<SysPortal> wrapper = super.getQueryWrapper()
                .eq(SysPortal::getRoleId, roleId);
        return super.delete(wrapper);
    }

    /**
     * 根据dataMode和referenceId删除Portal配置
     *
     * @param dataMode    数据模式（MATRIX/DATASET）
     * @param referenceId 关联的数据模型ID
     * @return 删除的Portal ID列表
     */
    public List<Long> deleteByDataModeAndReferenceId(String dataMode, Long referenceId) {
        LambdaQueryWrapper<SysPortal> wrapper = super.getQueryWrapper()
                .eq(SysPortal::getDataMode, dataMode)
                .eq(SysPortal::getReferenceId, String.valueOf(referenceId));
        List<SysPortal> portals = super.select(wrapper);
        if (FuncUtil.isNotEmpty(portals)) {
            List<Long> portalIds = portals.stream()
                    .map(SysPortal::getId)
                    .collect(java.util.stream.Collectors.toList());
            super.delete(wrapper);
            return portalIds;
        }
        return new java.util.ArrayList<>();
    }
}
