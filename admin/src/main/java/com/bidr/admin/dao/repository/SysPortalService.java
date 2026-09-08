package com.bidr.admin.dao.repository;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.bidr.admin.dao.entity.SysPortal;
import com.bidr.admin.dao.entity.SysPortalColumn;
import com.bidr.admin.dao.mapper.SysPortalMapper;
import com.bidr.admin.service.PortalConfigService;
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

    /**
     * 运行时取 portal 配置：优先角色副本，无副本回退默认配置（roleId=0）。
     * <p>per-portal 绑定模式下角色可能只复制了部分 portal，
     * 未复制的不应直接报不存在，而应回落到默认配置。</p>
     */
    public SysPortal getByNameOrDefault(String name, Long roleId) {
        SysPortal portal = getByName(name, roleId);
        if (FuncUtil.isEmpty(portal) && FuncUtil.isNotEmpty(roleId)
                && !PortalConfigService.DEFAULT_CONFIG_ROLE_ID.equals(roleId)) {
            portal = getByName(name, PortalConfigService.DEFAULT_CONFIG_ROLE_ID);
        }
        return portal;
    }

    /**
     * 运行时解析生效 roleId：角色下无该 portal 副本时回退默认配置的 roleId。
     */
    private Long resolveRunRoleId(String portalName, Long roleId) {
        if (FuncUtil.isNotEmpty(roleId) && !PortalConfigService.DEFAULT_CONFIG_ROLE_ID.equals(roleId)
                && FuncUtil.isEmpty(getByName(portalName, roleId))) {
            return PortalConfigService.DEFAULT_CONFIG_ROLE_ID;
        }
        return roleId;
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
        roleId = resolveRunRoleId(portalName, roleId);
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
        roleId = resolveRunRoleId(portalName, roleId);
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

    public List<SysPortal> getByRoleId(Long roleId) {
        LambdaQueryWrapper<SysPortal> wrapper = super.getQueryWrapper()
                .eq(SysPortal::getRoleId, roleId);
        return super.select(wrapper);
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

    public List<SysPortalColumn> getColumnsByPortalName(String portalName, Long roleId) {
        roleId = resolveRunRoleId(portalName, roleId);
        MPJLambdaWrapper<SysPortal> wrapper = super.getMPJLambdaWrapper();
        wrapper.selectAll(SysPortalColumn.class);
        wrapper.leftJoin(SysPortalColumn.class, SysPortalColumn::getPortalId, SysPortal::getId);
        wrapper.eq(SysPortal::getName, portalName);
        wrapper.eq(SysPortal::getRoleId, roleId);
        wrapper.eq(SysPortalColumn::getRoleId, roleId);
        wrapper.orderByAsc(SysPortalColumn::getDisplayOrder);
        return super.selectJoinList(SysPortalColumn.class, wrapper);
    }
}
