package com.bidr.authorization.service.permit;

import com.bidr.authorization.constants.dict.DataPermitScopeDict;
import com.bidr.authorization.dao.entity.AcGroup;
import com.bidr.authorization.dao.entity.AcUserDept;
import com.bidr.authorization.dao.entity.AcUserGroup;
import com.bidr.authorization.dao.repository.AcUserDeptService;
import com.bidr.authorization.dao.repository.AcUserGroupService;
import com.bidr.kernel.utils.FuncUtil;
import com.github.yulichang.wrapper.MPJLambdaWrapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Title: DataScopeResolver
 * Description: dataScope 分档展开引擎——把「用户与组/部门的绑定关系 + 各自的数据权限范围」解析为用户的有效视野集合
 * <p>
 * 展开规则（语义：领导能看下属，只向下扩张，不向上继承），多条绑定取并集：
 * <ul>
 *   <li>ALL(3) 全体部门：同类全部节点，短路返回</li>
 *   <li>SUBORDINATE(2) 本部门及子部门：{自身} 并 全部子孙</li>
 *   <li>DEPARTMENT(0) / OWNER(1) / OTHER(4) / 未配置：{自身}</li>
 * </ul>
 * </p>
 * <p>
 * 返回集合为<b>新拷贝</b>的不可变视图，调用方可安全持有，不会污染树快照。
 * 用户与组/部门的绑定关系每次实时查询（不缓存），因此改绑即时生效；
 * 只有树形状受 {@link PermissionTreeService} 快照周期影响。
 * </p>
 *
 * @author Sharp
 * @since 2026/09/23
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DataScopeResolver {

    private final AcUserGroupService acUserGroupService;
    private final AcUserDeptService acUserDeptService;
    private final PermissionTreeService permissionTreeService;

    /**
     * 解析用户在用户组维度上的有效视野
     *
     * @param userId    用户id
     * @param groupType 组类型；为 null/空 表示不限类型，且 dataScope=ALL 时展开为全部组
     * @return 可见的组 id 集合（绑定自身 + 按 dataScope 向下扩张的部分），不可变；无绑定时返回空集合
     */
    public Set<Long> resolveEffectiveGroupIds(Long userId, String groupType) {
        Set<Long> result = new HashSet<>();
        if (FuncUtil.isEmpty(userId)) {
            return Collections.unmodifiableSet(result);
        }
        List<AcUserGroup> userGroups = listUserGroups(userId, groupType);
        if (FuncUtil.isEmpty(userGroups)) {
            return Collections.unmodifiableSet(result);
        }
        Set<Long> subordinateSeeds = new HashSet<>();
        for (AcUserGroup userGroup : userGroups) {
            if (FuncUtil.isEmpty(userGroup) || FuncUtil.isEmpty(userGroup.getGroupId())) {
                continue;
            }
            DataPermitScopeDict scope = DataPermitScopeDict.of(userGroup.getDataScope());
            if (DataPermitScopeDict.ALL == scope) {
                // ALL 涵盖一切，无需再看其它绑定
                return immutableCopy(permissionTreeService.groupIdsByType(groupType));
            } else if (DataPermitScopeDict.SUBORDINATE == scope) {
                subordinateSeeds.add(userGroup.getGroupId());
            } else {
                result.add(userGroup.getGroupId());
            }
        }
        if (FuncUtil.isNotEmpty(subordinateSeeds)) {
            // 与 SQL 递归分支口径一致：起始组自身也计入视野
            result.addAll(subordinateSeeds);
            result.addAll(PermissionTreeService.collectDescendants(subordinateSeeds, permissionTreeService.groupChildrenMap()));
        }
        log.debug("[数据权限] 组维度展开 userId={}, groupType={}, 绑定数={}, 可见组={}", userId, groupType,
                userGroups.size(), result);
        return Collections.unmodifiableSet(result);
    }

    /**
     * 解析用户在部门维度上的有效视野
     * <p>
     * 只保留有效部门：停用部门既不能作为展开起点，也不能出现在展开结果中。
     * 有效性口径与 {@code AcDeptDict}、{@code PermitSourceService} 一致（{@code status = YES}）。
     * </p>
     *
     * @param userId 用户id
     * @return 可见的部门 id 集合，不可变；无绑定时返回空集合
     */
    public Set<String> resolveEffectiveDeptIds(Long userId) {
        Set<String> result = new HashSet<>();
        if (FuncUtil.isEmpty(userId)) {
            return Collections.unmodifiableSet(result);
        }
        List<AcUserDept> userDepts = acUserDeptService.listByUserId(userId);
        if (FuncUtil.isEmpty(userDepts)) {
            return Collections.unmodifiableSet(result);
        }
        Set<String> validDeptIds = permissionTreeService.validDeptIds();
        Set<String> subordinateSeeds = new HashSet<>();
        for (AcUserDept userDept : userDepts) {
            if (FuncUtil.isEmpty(userDept) || FuncUtil.isEmpty(userDept.getDeptId())) {
                continue;
            }
            String deptId = userDept.getDeptId();
            if (!validDeptIds.contains(deptId)) {
                continue;
            }
            DataPermitScopeDict scope = DataPermitScopeDict.of(userDept.getDataScope());
            if (DataPermitScopeDict.ALL == scope) {
                return immutableCopy(validDeptIds);
            } else if (DataPermitScopeDict.SUBORDINATE == scope) {
                subordinateSeeds.add(deptId);
            } else {
                result.add(deptId);
            }
        }
        if (FuncUtil.isNotEmpty(subordinateSeeds)) {
            result.addAll(subordinateSeeds);
            result.addAll(PermissionTreeService.collectDescendants(subordinateSeeds, permissionTreeService.deptChildrenMap()));
        }
        // 建图为保证连通性包含了停用部门，结果统一收敛回有效部门
        result.retainAll(validDeptIds);
        log.debug("[数据权限] 部门维度展开 userId={}, 绑定数={}, 可见部门={}", userId, userDepts.size(), result);
        return Collections.unmodifiableSet(result);
    }

    /**
     * 查询用户在指定类型下的全部组绑定（含各自的 dataScope）
     */
    private List<AcUserGroup> listUserGroups(Long userId, String groupType) {
        MPJLambdaWrapper<AcUserGroup> wrapper = new MPJLambdaWrapper<>(AcUserGroup.class).distinct()
                .leftJoin(AcGroup.class, AcGroup::getId, AcUserGroup::getGroupId)
                .eq(FuncUtil.isNotEmpty(groupType), AcGroup::getType, groupType)
                .eq(AcUserGroup::getUserId, userId);
        return acUserGroupService.selectJoinList(AcUserGroup.class, wrapper);
    }

    private <T> Set<T> immutableCopy(Set<T> source) {
        return Collections.unmodifiableSet(new HashSet<>(source));
    }
}
