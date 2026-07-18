package com.bidr.authorization.dao.repository;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.bidr.authorization.constants.dict.DataPermitScopeDict;
import com.bidr.authorization.dao.entity.AcGroup;
import com.bidr.authorization.dao.entity.AcGroupBind;
import com.bidr.authorization.dao.entity.AcUserGroup;
import com.bidr.authorization.dao.mapper.AcGroupBindMapper;
import com.bidr.kernel.mybatis.dao.repository.RecursionService;
import com.bidr.kernel.mybatis.repository.BaseSqlRepo;
import com.bidr.kernel.utils.FuncUtil;
import com.github.yulichang.wrapper.MPJLambdaWrapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 用户组通用绑定关系表Service
 *
 * @author sharp
 * @since 2026/07/18
 */
@Service
@RequiredArgsConstructor
public class AcGroupBindService extends BaseSqlRepo<AcGroupBindMapper, AcGroupBind> {

    private final RecursionService recursionService;
    private final AcUserGroupService acUserGroupService;
    private final AcGroupService acGroupService;

    /**
     * 按 groupId + bindType 查询绑定列表
     */
    public List<AcGroupBind> listBind(Object entityId, String bindType) {
        return list(getQueryWrapper()
                .eq(AcGroupBind::getGroupId, entityId)
                .eq(AcGroupBind::getBindType, bindType));
    }

    /**
     * 按 groupId + bindType + attachValue 查询单条
     */
    public AcGroupBind getOneBind(Object entityId, Object attachId, String bindType) {
        return getOne(getQueryWrapper()
                .eq(AcGroupBind::getGroupId, entityId)
                .eq(AcGroupBind::getBindType, bindType)
                .eq(AcGroupBind::getAttachValue, String.valueOf(attachId)), false);
    }

    /**
     * 替换绑定（按 groupId + bindType 维度做全量替换）。
     * <p>
     * 以 (groupId, bindType) 为作用域：新列表中存在但旧库没有的 → 新增；
     * 旧库存在但新列表没有的 → 删除；交集保持不变（extraData 不变）。
     * <p>
     * 事务必须放在 Service 层：Controller 中的 Resp.notice 本质是抛异常返回，
     * 会导致 Controller 上的 @Transactional 事务行为异常。
     *
     * @param entityId     用户组id
     * @param bindType     绑定类型
     * @param attachValues 绑定目标值列表（整体覆盖该维度下的绑定）
     */
    @Transactional(rollbackFor = Exception.class)
    public void replace(Object entityId, String bindType, Collection<Object> attachValues) {
        Long groupId = Long.valueOf(String.valueOf(entityId));

        // 1. 查询旧绑定
        List<AcGroupBind> oldBinds = listBind(entityId, bindType);
        Set<String> oldValues = oldBinds.stream()
                .map(AcGroupBind::getAttachValue)
                .collect(Collectors.toSet());

        // 2. 新值集合（统一转字符串，去重）
        Set<String> newValues = new HashSet<>();
        if (FuncUtil.isNotEmpty(attachValues)) {
            attachValues.forEach(v -> newValues.add(String.valueOf(v)));
        }

        // 3. 需要解绑的（旧有但新列表没有）
        Set<String> toUnbind = oldValues.stream()
                .filter(v -> !newValues.contains(v))
                .collect(Collectors.toSet());
        if (!toUnbind.isEmpty()) {
            delete(getQueryWrapper()
                    .eq(AcGroupBind::getGroupId, entityId)
                    .eq(AcGroupBind::getBindType, bindType)
                    .in(AcGroupBind::getAttachValue, toUnbind));
        }

        // 4. 需要新绑定的（新列表有但旧库没有，交集不动保留 extraData）
        List<AcGroupBind> toBind = newValues.stream()
                .filter(v -> !oldValues.contains(v))
                .map(value -> {
                    AcGroupBind bind = new AcGroupBind();
                    bind.setGroupId(groupId);
                    bind.setBindType(bindType);
                    bind.setAttachValue(value);
                    return bind;
                })
                .collect(Collectors.toList());
        if (!toBind.isEmpty()) {
            // 批量插入（BaseSqlRepo#insert(Collection) 内部走 saveBatch）
            insert(toBind);
        }
    }

    /**
     * 修改绑定信息（upsert 语义：不存在则新建，存在则更新 extraData）。
     * <p>
     * data 中的字段会整体覆盖 extra_data（非合并），避免历史脏字段残留。
     *
     * @param entityId  用户组id
     * @param attachId  绑定目标值
     * @param bindType  绑定类型
     * @param extraJson 绑定属性 JSON 字符串（可为 null 表示清空）
     */
    @Transactional(rollbackFor = Exception.class)
    public void upsertBindInfo(Object entityId, Object attachId, String bindType, String extraJson) {
        AcGroupBind bind = getOneBind(entityId, attachId, bindType);
        if (bind == null) {
            bind = new AcGroupBind();
            bind.setGroupId(Long.valueOf(String.valueOf(entityId)));
            bind.setBindType(bindType);
            bind.setAttachValue(String.valueOf(attachId));
            bind.setExtraData(extraJson);
            save(bind);
        } else {
            bind.setExtraData(extraJson);
            updateById(bind);
        }
    }

    /**
     * 按用户数据权限范围，查询其在指定 groupType + bindType 下的所有绑定 attachValue。
     * <p>
     * 权限范围处理：
     * <ul>
     *   <li>ALL（全体）：返回该 groupType 下所有组的绑定</li>
     *   <li>SUBORDINATE（本组及子组）：递归获取子组 + 本组</li>
     *   <li>其他（本组/本人等）：仅本组</li>
     * </ul>
     * attachValue 结果去重。
     *
     * @param userId    用户id
     * @param groupType 用户组类型（对应 ac_group.type）
     * @param bindType  绑定类型（对应 ac_group_bind.bind_type）
     * @return 去重后的 attachValue 列表，无数据时返回空列表
     */
    public List<String> listAttachValuesByDataScope(Long userId, String groupType, String bindType) {
        // 1. 查用户在该 groupType 下的所有组关系（含 dataScope）
        MPJLambdaWrapper<AcUserGroup> userGroupWrapper = new MPJLambdaWrapper<>(AcUserGroup.class).distinct()
                .leftJoin(AcGroup.class, AcGroup::getId, AcUserGroup::getGroupId)
                .eq(AcGroup::getType, groupType)
                .eq(AcUserGroup::getUserId, userId);
        List<AcUserGroup> userGroups = acUserGroupService.selectJoinList(AcUserGroup.class, userGroupWrapper);

        if (FuncUtil.isEmpty(userGroups)) {
            return new ArrayList<>();
        }

        // 2. 按数据权限范围汇总 groupId
        Set<Long> groupIds = new HashSet<>();
        for (AcUserGroup userGroup : userGroups) {
            DataPermitScopeDict scope = DataPermitScopeDict.of(userGroup.getDataScope());
            if (scope == DataPermitScopeDict.ALL) {
                // ALL：该 groupType 下所有组，直接返回全量
                List<AcGroup> allGroups = acGroupService.getGroupByType(groupType);
                if (FuncUtil.isNotEmpty(allGroups)) {
                    for (AcGroup g : allGroups) {
                        groupIds.add(g.getId());
                    }
                }
                // ALL 涵盖一切，无需继续处理其他组
                break;
            } else if (scope == DataPermitScopeDict.SUBORDINATE) {
                // SUBORDINATE：递归获取子组
                List<Long> subGroups = recursionService.getChildList(
                        AcGroup::getId, AcGroup::getPid, userGroup.getGroupId());
                if (FuncUtil.isNotEmpty(subGroups)) {
                    groupIds.addAll(subGroups);
                }
                groupIds.add(userGroup.getGroupId());
            } else {
                // 其他（本组/本人等）：仅本组
                groupIds.add(userGroup.getGroupId());
            }
        }

        if (FuncUtil.isEmpty(groupIds)) {
            return new ArrayList<>();
        }

        // 3. 查这些组在 bindType 下的绑定，取去重的 attachValue
        LambdaQueryWrapper<AcGroupBind> bindWrapper = super.getQueryWrapper()
                .in(AcGroupBind::getGroupId, groupIds)
                .eq(AcGroupBind::getBindType, bindType);
        List<AcGroupBind> binds = super.list(bindWrapper);

        return binds.stream()
                .map(AcGroupBind::getAttachValue)
                .distinct()
                .collect(Collectors.toList());
    }
}
