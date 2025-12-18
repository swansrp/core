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
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

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
            AdminControllerInf<?, ?> bean = (AdminControllerInf<?, ?>) BeanUtil.getBean(portalWithColumns.getBean());
            Class<?> voClass = bean.getVoClass();
            for (SysPortalColumn column : portalWithColumns.getColumns()) {
                Field field = ReflectionUtil.getField(voClass, column.getProperty());
                SysPortalColumn sysPortalColumn = portalConfigService.buildSysPortalColumn(portalWithColumns,
                        column.getDisplayOrder(), field, column.getRoleId());
                ReflectionUtil.merge(sysPortalColumn, column, true);
                sysPortalColumnService.updateById(column);
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
     * @param indicatorResList 源indicator列表（已经是树形结构）
     * @param sourcePortalName 源portal名称
     * @param targetPortalName 目标portal名称
     */
    private void copyIndicatorGroupsAndIndicators(List<IndicatorRes> indicatorResList, String sourcePortalName, String targetPortalName) {
        if (FuncUtil.isEmpty(indicatorResList)) {
            return;
        }

        // 使用Map保存旧ID到新实体的映射关系
        Map<String, SysPortalIndicatorGroup> oldToNewGroupMap = new HashMap<>();
        
        // 递归复制树形结构的indicatorGroup，从根节点开始，null表示根节点没有父节点
        copyIndicatorGroupTree(indicatorResList, null, targetPortalName, oldToNewGroupMap);

        // 收集所有indicator并批量插入
        List<SysPortalIndicator> newIndicators = new ArrayList<>();
        collectIndicators(indicatorResList, oldToNewGroupMap, newIndicators);
        
        if (FuncUtil.isNotEmpty(newIndicators)) {
            sysPortalIndicatorService.insert(newIndicators);
        }
    }

    /**
     * 递归复制indicatorGroup树形结构
     * @param indicatorResList indicator列表
     * @param parentId 父节点ID（null表示根节点）
     * @param targetPortalName 目标portal名称
     * @param oldToNewGroupMap 旧ID到新实体的映射
     */
    private void copyIndicatorGroupTree(List<IndicatorRes> indicatorResList, Long parentId,
                                       String targetPortalName,
                                       Map<String, SysPortalIndicatorGroup> oldToNewGroupMap) {
        if (FuncUtil.isEmpty(indicatorResList)) {
            return;
        }

        // 当前层级的所有节点
        List<SysPortalIndicatorGroup> currentLevelGroups = new ArrayList<>();
        
        for (IndicatorRes res : indicatorResList) {
            SysPortalIndicatorGroup newGroup = new SysPortalIndicatorGroup();
            newGroup.setPortalName(targetPortalName);
            newGroup.setName(res.getTitle());
            newGroup.setDisplayOrder(res.getDisplayOrder() != null ? res.getDisplayOrder() : 0);
            
            // 根据原有的pid关系设置新的pid
            // 如果原来的pid为null或空，说明是根节点，新的pid也设为null
            // 否则，从映射中找到对应的新父节点ID
            if (FuncUtil.isEmpty(res.getPid()) || "null".equals(res.getPid())) {
                newGroup.setPid(null);
            } else {
                // 从映射中查找父节点的新ID
                SysPortalIndicatorGroup parentGroup = oldToNewGroupMap.get(res.getPid());
                if (parentGroup != null) {
                    newGroup.setPid(parentGroup.getId());
                } else {
                    // 如果找不到父节点，设为null（理论上不应该发生）
                    newGroup.setPid(null);
                }
            }
            
            currentLevelGroups.add(newGroup);
        }
        
        // 批量插入当前层级的节点
        if (FuncUtil.isNotEmpty(currentLevelGroups)) {
            sysPortalIndicatorGroupService.insert(currentLevelGroups);
            
            // 更新映射关系
            int index = 0;
            for (IndicatorRes res : indicatorResList) {
                if (index < currentLevelGroups.size()) {
                    oldToNewGroupMap.put(res.getId(), currentLevelGroups.get(index));
                    index++;
                }
            }
        }
        
        // 递归处理子节点
        for (int i = 0; i < indicatorResList.size(); i++) {
            IndicatorRes res = indicatorResList.get(i);
            if (FuncUtil.isNotEmpty(res.getChildren())) {
                // 递归处理子节点，不需要传入parentId，因为在子节点的pid中已经包含了
                copyIndicatorGroupTree(res.getChildren(), null, targetPortalName, oldToNewGroupMap);
            }
        }
    }

    /**
     * 收集所有indicator
     * @param indicatorResList indicator列表
     * @param oldToNewGroupMap 旧ID到新实体的映射
     * @param result 结果集合
     */
    private void collectIndicators(List<IndicatorRes> indicatorResList, 
                                  Map<String, SysPortalIndicatorGroup> oldToNewGroupMap,
                                  List<SysPortalIndicator> result) {
        if (FuncUtil.isEmpty(indicatorResList)) {
            return;
        }

        for (IndicatorRes res : indicatorResList) {
            // 获取新的group ID
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
                    result.add(indicator);
                }
            }
            
            // 递归处理子节点
            if (FuncUtil.isNotEmpty(res.getChildren())) {
                collectIndicators(res.getChildren(), oldToNewGroupMap, result);
            }
        }
    }
}
