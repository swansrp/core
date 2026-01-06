package com.bidr.admin.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.bidr.admin.dao.entity.SysPortal;
import com.bidr.admin.dao.entity.SysPortalColumn;
import com.bidr.admin.dao.entity.SysPortalIndicator;
import com.bidr.admin.dao.entity.SysPortalIndicatorGroup;
import com.bidr.admin.dao.repository.SysPortalColumnService;
import com.bidr.admin.dao.repository.SysPortalIndicatorGroupService;
import com.bidr.admin.dao.repository.SysPortalIndicatorService;
import com.bidr.admin.dao.repository.SysPortalService;
import com.bidr.admin.holder.PortalConfigContext;
import com.bidr.admin.vo.*;
import com.bidr.admin.vo.statistic.IndicatorItem;
import com.bidr.admin.vo.statistic.IndicatorRes;
import com.bidr.kernel.config.response.Resp;
import com.bidr.kernel.constant.CommonConst;
import com.bidr.kernel.constant.err.ErrCodeSys;
import com.bidr.kernel.controller.inf.AdminControllerInf;
import com.bidr.kernel.utils.BeanUtil;
import com.bidr.kernel.utils.FuncUtil;
import com.bidr.kernel.utils.ReflectionUtil;
import com.bidr.kernel.utils.StringUtil;
import com.bidr.kernel.validate.Validator;
import com.bidr.kernel.vo.common.IdOrderReqVO;
import com.bidr.kernel.vo.common.IdReqVO;
import com.bidr.kernel.vo.common.KeyValueResVO;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import java.lang.reflect.Field;
import java.util.*;

/**
 * Title: PortalService
 * Description: Copyright: Copyright (c) 2023
 *
 * @author Sharp
 * @since 2023/11/21 18:02
 */
@Service
@RequiredArgsConstructor
public class PortalService {
    private final SysPortalService sysPortalService;
    private final SysPortalColumnService sysPortalColumnService;
    private final SysPortalIndicatorGroupService sysPortalIndicatorGroupService;
    private final SysPortalIndicatorService sysPortalIndicatorService;
    @Lazy
    @Resource
    private PortalConfigService portalConfigService;

    public PortalWithColumnsRes getPortalWithColumnsConfig(PortalReq req) {
        if (FuncUtil.isEmpty(req.getRoleId())) {
            req.setRoleId(PortalConfigContext.getPortalConfigRoleId());
        }
        return getPortalWithColumnsConfig(req.getName(), req.getRoleId());
    }

    public PortalWithColumnsRes getPortalWithColumnsConfig(String name, Long roleId) {
        SysPortal portal = sysPortalService.getByName(name, roleId);
        Validator.assertNotNull(portal, ErrCodeSys.PA_DATA_NOT_EXIST, "实体");
        return Resp.convert(portal, PortalWithColumnsRes.class);
    }

    public PortalUpdateReq getPortalConfig(PortalReq req) {
        SysPortal portal = sysPortalService.getByName(req.getName(), req.getRoleId());
        Validator.assertNotNull(portal, ErrCodeSys.PA_DATA_NOT_EXIST, "实体");
        return Resp.convert(portal, PortalUpdateReq.class);
    }

    public List<KeyValueResVO> getPortalList(PortalReq req) {
        return sysPortalService.getPortalList(req.getName(), req.getRoleId(), req.getDataMode(), req.getReferenceId());
    }

    @Transactional(rollbackFor = Exception.class)
    public void updatePortalConfig(PortalUpdateReq req) {
        SysPortal portal = ReflectionUtil.copy(req, SysPortal.class);
        if (FuncUtil.isEmpty(req.getPidColumn())) {
            portal.setPidColumn(StringUtil.EMPTY);
        }
        sysPortalService.updateById(portal, false);
    }

    @Transactional(rollbackFor = Exception.class)
    public void updatePortalColumnOrder(List<IdOrderReqVO> orderList) {
        if (FuncUtil.isNotEmpty(orderList)) {
            List<SysPortalColumn> entityList = new ArrayList<>();
            for (IdOrderReqVO order : orderList) {
                SysPortalColumn column = new SysPortalColumn();
                column.setId(Long.parseLong(StringUtil.parse(order.getId())));
                column.setDisplayOrder(order.getShowOrder());
                entityList.add(column);
            }
            sysPortalColumnService.updateBatchById(entityList);
        }
    }

    @Transactional(rollbackFor = Exception.class)
    public void updatePortalColumn(PortalColumnReq req) {
        SysPortalColumn column = ReflectionUtil.copy(req, SysPortalColumn.class);
        sysPortalColumnService.updateById(column);
    }

    @Transactional(rollbackFor = Exception.class)
    public void deletePortalConfig(IdReqVO req) {
        SysPortal sysPortal = sysPortalService.getByName(req.getId(), PortalConfigService.DEFAULT_CONFIG_ROLE_ID);
        List<SysPortal> portalList = sysPortalService.getByBeanName(sysPortal.getBean(), sysPortal.getName());
        for (SysPortal portal : portalList) {
            sysPortalService.deleteById(portal.getId());
            sysPortalColumnService.deleteByPortalId(portal.getId());
        }
    }

    @Transactional(rollbackFor = Exception.class)
    public void refreshPortalConfig(PortalReq req) {
        SysPortal sysPortal = sysPortalService.getByName(req.getName(), req.getRoleId());
        List<SysPortal> portalList = new ArrayList<>();
        if (FuncUtil.equals(req.getRoleId(), PortalConfigService.DEFAULT_CONFIG_ROLE_ID)) {
            portalList.addAll(sysPortalService.getByBeanName(sysPortal.getBean(), sysPortal.getName()));
        } else {
            portalList.add(sysPortal);
        }

        List<PortalWithColumnsRes> portalWithColumnsResList = Resp.convert(portalList, PortalWithColumnsRes.class);
        for (PortalWithColumnsRes portalWithColumns : portalWithColumnsResList) {
            if (!portalWithColumns.getBean().equals("dynamicPortalController")) {
                AdminControllerInf<?, ?> bean = (AdminControllerInf<?, ?>) BeanUtil.getBean(portalWithColumns.getBean());
                Class<?> voClass = bean.getVoClass();
                for (SysPortalColumn column : portalWithColumns.getColumns()) {
                    Field field = ReflectionUtil.getField(voClass, column.getProperty());
                    SysPortalColumn sysPortalColumn = portalConfigService.buildSysPortalColumn(portalWithColumns,
                            column.getDisplayOrder(), field, column.getRoleId());
                    ReflectionUtil.merge(sysPortalColumn, column, true);
                    sysPortalColumnService.updateById(column);
                }
            }
            sysPortalService.updateById(portalWithColumns);
        }
    }

    public void validatePortalExisted(PortalReq req) {
        Validator.assertFalse(sysPortalService.existedByName(req.getName(), PortalConfigService.DEFAULT_CONFIG_ROLE_ID),
                ErrCodeSys.SYS_ERR_MSG, "表格编码已存在,请删除原有表格或者使用其他编码");
    }

    @Transactional(rollbackFor = Exception.class)
    public void copyPortalConfig(PortalCopyReq req) {
        Validator.assertFalse(sysPortalService.existedByName(req.getTargetName(), req.getRoleId()),
                ErrCodeSys.PA_DATA_HAS_EXIST, "表格编码");
        SysPortal sourcePortal = sysPortalService.getById(req.getSourceConfigId());
        Validator.assertNotNull(sourcePortal, ErrCodeSys.PA_DATA_NOT_EXIST, "实体");
        PortalWithColumnsRes sourcePortalWithColumn = Resp.convert(sourcePortal, PortalWithColumnsRes.class);
        List<IndicatorRes> indicatorResList = sysPortalIndicatorGroupService.getIndicator(sourcePortal.getName());

        // 清理目标portal的现有indicatorGroup和indicator数据
        cleanExistingIndicators(req.getTargetName());

        Collection<Long> bindRoleIdList = portalConfigService.getBindRoleIdList();
        for (Long roleId : bindRoleIdList) {
            SysPortal targetPortal = ReflectionUtil.copy(sourcePortal, SysPortal.class);
            targetPortal.setRoleId(roleId);
            targetPortal.setId(null);
            targetPortal.setName(req.getTargetName());
            targetPortal.setDisplayName(req.getTargetDisplayName());
            sysPortalService.insert(targetPortal);
            List<SysPortalColumn> columnList = new ArrayList<>();
            if (FuncUtil.isNotEmpty(sourcePortalWithColumn.getColumns())) {
                for (SysPortalColumn column : sourcePortalWithColumn.getColumns()) {
                    column.setId(null);
                    column.setPortalId(targetPortal.getId());
                    column.setRoleId(roleId);
                    columnList.add(column);
                }
            }
            sysPortalColumnService.insert(columnList);

            // 复制indicatorGroup树形结构和indicator数据
            copyIndicatorGroupsAndIndicators(indicatorResList, sourcePortal.getName(), targetPortal.getName());
        }

    }

    /**
     * 清理目标portal的现有indicatorGroup和indicator数据
     *
     * @param targetPortalName 目标portal名称
     */
    private void cleanExistingIndicators(String targetPortalName) {
        // 先查询目标portal的所有indicatorGroup
        LambdaQueryWrapper<SysPortalIndicatorGroup> groupWrapper = new LambdaQueryWrapper<>();
        groupWrapper.eq(SysPortalIndicatorGroup::getPortalName, targetPortalName);
        List<SysPortalIndicatorGroup> existingGroups = sysPortalIndicatorGroupService.select(groupWrapper);

        if (FuncUtil.isNotEmpty(existingGroups)) {
            // 收集所有group的ID
            List<Long> groupIds = existingGroups.stream()
                    .map(SysPortalIndicatorGroup::getId)
                    .collect(java.util.stream.Collectors.toList());

            // 删除所有关联的indicator
            if (FuncUtil.isNotEmpty(groupIds)) {
                LambdaQueryWrapper<SysPortalIndicator> indicatorWrapper = new LambdaQueryWrapper<>();
                indicatorWrapper.in(SysPortalIndicator::getGroupId, groupIds);
                sysPortalIndicatorService.delete(indicatorWrapper);
            }

            // 删除所有indicatorGroup
            sysPortalIndicatorGroupService.delete(groupWrapper);
        }
    }

    /**
     * 复制indicatorGroup树形结构和indicator数据
     *
     * @param indicatorResList 源indicator列表（扫平列表，包含pid信息）
     * @param sourcePortalName 源portal名称
     * @param targetPortalName 目标portal名称
     */
    private void copyIndicatorGroupsAndIndicators(List<IndicatorRes> indicatorResList, String sourcePortalName, String targetPortalName) {
        if (FuncUtil.isEmpty(indicatorResList)) {
            return;
        }

        // 使用Map保存旧ID到新实体的映射关系
        Map<String, SysPortalIndicatorGroup> oldToNewGroupMap = new HashMap<>();

        // 第一步：按照pid分组，分层次插入
        // 先找出所有根节点（pid为null或空）
        List<IndicatorRes> rootNodes = new ArrayList<>();
        Map<String, List<IndicatorRes>> childrenMap = new HashMap<>();

        for (IndicatorRes res : indicatorResList) {
            if (FuncUtil.isEmpty(res.getPid()) || "null".equals(res.getPid())) {
                rootNodes.add(res);
            } else {
                childrenMap.computeIfAbsent(res.getPid(), k -> new ArrayList<>()).add(res);
            }
        }

        // 递归插入节点，保持层级关系
        insertIndicatorGroupsByLevel(rootNodes, null, targetPortalName, oldToNewGroupMap, childrenMap);

        // 第二步：收集所有indicator并批量插入
        List<SysPortalIndicator> newIndicators = new ArrayList<>();
        for (IndicatorRes res : indicatorResList) {
            SysPortalIndicatorGroup newGroup = oldToNewGroupMap.get(res.getId());
            if (newGroup != null && FuncUtil.isNotEmpty(res.getItems())) {
                Long newGroupId = newGroup.getId();
                int order = 0;
                for (IndicatorItem item : res.getItems()) {
                    SysPortalIndicator indicator = new SysPortalIndicator();
                    indicator.setGroupId(newGroupId);
                    indicator.setItemValue(item.getKey());
                    indicator.setItemName(item.getTitle());
                    indicator.setCondition(item.getCondition());
                    indicator.setDynamicColumn(item.getDynamicColumns());
                    indicator.setDisplayOrder(order++);
                    indicator.setValid(CommonConst.YES);
                    newIndicators.add(indicator);
                }
            }
        }

        if (FuncUtil.isNotEmpty(newIndicators)) {
            sysPortalIndicatorService.insert(newIndicators);
        }
    }

    /**
     * 按层级递归插入indicatorGroup
     *
     * @param nodes            当前层级的节点列表
     * @param parentId         父节点ID
     * @param targetPortalName 目标portal名称
     * @param oldToNewGroupMap 旧ID到新实体的映射
     * @param childrenMap      父ID到子节点列表的映射
     */
    private void insertIndicatorGroupsByLevel(List<IndicatorRes> nodes, Long parentId,
                                              String targetPortalName,
                                              Map<String, SysPortalIndicatorGroup> oldToNewGroupMap,
                                              Map<String, List<IndicatorRes>> childrenMap) {
        if (FuncUtil.isEmpty(nodes)) {
            return;
        }

        // 创建当前层级的所有节点
        List<SysPortalIndicatorGroup> currentLevelGroups = new ArrayList<>();
        for (IndicatorRes res : nodes) {
            SysPortalIndicatorGroup newGroup = new SysPortalIndicatorGroup();
            newGroup.setPortalName(targetPortalName);
            newGroup.setName(res.getTitle());
            newGroup.setDisplayOrder(res.getDisplayOrder() != null ? res.getDisplayOrder() : 0);
            newGroup.setPid(parentId);
            currentLevelGroups.add(newGroup);
        }

        // 批量插入当前层级
        if (FuncUtil.isNotEmpty(currentLevelGroups)) {
            sysPortalIndicatorGroupService.insert(currentLevelGroups);

            // 更新映射关系
            for (int i = 0; i < nodes.size(); i++) {
                oldToNewGroupMap.put(nodes.get(i).getId(), currentLevelGroups.get(i));
            }
        }

        // 递归处理每个节点的子节点
        for (IndicatorRes node : nodes) {
            List<IndicatorRes> children = childrenMap.get(node.getId());
            if (FuncUtil.isNotEmpty(children)) {
                // 递归插入子节点，使用新插入节点的ID作为父ID
                insertIndicatorGroupsByLevel(children,
                        oldToNewGroupMap.getOrDefault(node.getId(), new SysPortalIndicatorGroup()).getId(),
                        targetPortalName,
                        oldToNewGroupMap,
                        childrenMap);
            }
        }
    }
}
