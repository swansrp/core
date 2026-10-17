package com.bidr.authorization.service.permit;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.bidr.authorization.dao.entity.AcDept;
import com.bidr.authorization.dao.entity.AcGroup;
import com.bidr.authorization.dao.repository.AcDeptService;
import com.bidr.authorization.dao.repository.AcGroupService;
import com.bidr.kernel.constant.CommonConst;
import com.bidr.kernel.utils.FuncUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Title: PermissionTreeService
 * Description: 权限树的向下展开引擎——提供组树/部门树的「父→子」关系与子孙收集能力
 * <p>
 * 数据权限的语义是「领导能看下属」，即持有者的视野沿树<b>向下</b>扩张，故本类只向下展开，
 * 不收集祖先链（{@code dataScope} 控制的是往下看多深，不控制往上是否继承）。
 * </p>
 * <p>
 * <b>为什么用进程内快照而不是 {@code @Cacheable}：</b>
 * {@code DB-CACHE} 的实际载体是 Redis + {@code GenericJackson2JsonRedisSerializer}，
 * 裸容器（{@code Map<Long, List<Long>>}、{@code Set<Long>}）经 JSON 往返会丢失泛型信息——
 * map 的 key 退化为 String、Long 元素退化为 Integer，取值时静默得不到数据，
 * 对权限引擎而言这是「视野意外收窄」的隐蔽故障。而 {@code ac_group}/{@code ac_dept} 都是
 * 小表且结构极少变动，整表加载成不可变快照的代价仅为一次查询/30秒，因此不走分布式缓存。
 * 附带收益：用户与组/部门的<b>归属关系</b>不进入快照，改绑即时生效，只有<b>树形状</b>有最长
 * {@link #SNAPSHOT_TTL_MILLIS} 的滞后。
 * </p>
 * <p>
 * 快照内所有集合与 Map 均按<b>只读</b>约定对外提供：调用方只能读取，不得修改；
 * 需要返回给上层的集合一律先拷贝（见 {@link DataScopeResolver}）。
 * </p>
 *
 * @author Sharp
 * @since 2026/09/23
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PermissionTreeService {

    /**
     * 树结构快照有效期（毫秒），与项目 DB-CACHE#30 同口径：
     * 调整组树/部门树父子关系后，最长 30 秒反映到数据权限视野
     */
    private static final long SNAPSHOT_TTL_MILLIS = 30 * 1000L;

    private final AcGroupService acGroupService;
    private final AcDeptService acDeptService;

    /**
     * 组树快照，volatile 保证发布安全；过期重建时允许并发重复构建（幂等且代价极低），不加锁
     */
    private volatile GroupTreeSnapshot groupSnapshot;
    /**
     * 部门树快照
     */
    private volatile DeptTreeSnapshot deptSnapshot;

    /**
     * 用户组「父 id → 子 id 列表」关系图（只读）
     */
    public Map<Long, List<Long>> groupChildrenMap() {
        return groupSnapshot().childrenMap;
    }

    /**
     * 指定类型下的全部用户组 id，用于 dataScope=ALL 的短路展开
     *
     * @param groupType 组类型，为 null/空表示不限类型（全部组）
     * @return 组 id 集合（只读），无数据返回空集合
     */
    public Set<Long> groupIdsByType(String groupType) {
        GroupTreeSnapshot snapshot = groupSnapshot();
        if (FuncUtil.isEmpty(groupType)) {
            return snapshot.allGroupIds;
        }
        Set<Long> groupIds = snapshot.groupIdsByType.get(groupType);
        return FuncUtil.isEmpty(groupIds) ? Collections.<Long>emptySet() : groupIds;
    }

    /**
     * 部门「父 deptId → 子 deptId 列表」关系图（只读）
     * <p>
     * 故意包含停用部门：若建图时剔除无效节点，「父停用、子有效」的有效子部门会因链路断开而被漏掉。
     * 有效性统一在展开完成后用 {@link #validDeptIds()} 收敛。
     * </p>
     */
    public Map<String, List<String>> deptChildrenMap() {
        return deptSnapshot().childrenMap;
    }

    /**
     * 全部有效部门 id（只读）
     * <p>
     * 判定口径与 {@code AcDeptDict}、{@code PermitSourceService} 完全一致：
     * {@code status = CommonConst.YES}。不引入 {@code valid} 列——既有权限链路从未使用该列，
     * 贸然加入可能因历史脏数据把有效部门判为无效，造成权限意外收窄。
     * </p>
     */
    public Set<String> validDeptIds() {
        return deptSnapshot().validDeptIds;
    }

    /**
     * 向下收集种子的全部子孙节点（不含种子自身）
     * <p>
     * 广度优先 + visited 集合保证每个节点最多入队一次：一次 {@code visited.add} 调用同时完成
     * 三件事——菱形结构去重、多路可达去重、脏数据成环时自然终止。因此<b>不需要递归深度上限</b>：
     * 加深度上限反而会截断合法深树，导致权限意外收窄。
     * </p>
     *
     * @param seeds       起点集合（调用方自行决定是否计入结果）
     * @param childrenMap 父→子 关系图（只读）
     * @return 全部子孙节点，不含 seeds 中的任何元素
     */
    public static <T> Set<T> collectDescendants(Collection<T> seeds, Map<T, List<T>> childrenMap) {
        Set<T> result = new HashSet<>();
        if (FuncUtil.isEmpty(seeds) || FuncUtil.isEmpty(childrenMap)) {
            return result;
        }
        Set<T> visited = new HashSet<>();
        Deque<T> queue = new ArrayDeque<>();
        for (T seed : seeds) {
            if (FuncUtil.isNotEmpty(seed) && visited.add(seed)) {
                queue.add(seed);
            }
        }
        while (!queue.isEmpty()) {
            List<T> children = childrenMap.get(queue.poll());
            if (FuncUtil.isEmpty(children)) {
                continue;
            }
            for (T child : children) {
                // 种子已在 visited 中，故结果天然不含种子
                if (visited.add(child)) {
                    result.add(child);
                    queue.add(child);
                }
            }
        }
        return result;
    }

    private GroupTreeSnapshot groupSnapshot() {
        GroupTreeSnapshot snapshot = groupSnapshot;
        if (FuncUtil.isEmpty(snapshot) || snapshot.expired()) {
            snapshot = buildGroupSnapshot();
            groupSnapshot = snapshot;
        }
        return snapshot;
    }

    private DeptTreeSnapshot deptSnapshot() {
        DeptTreeSnapshot snapshot = deptSnapshot;
        if (FuncUtil.isEmpty(snapshot) || snapshot.expired()) {
            snapshot = buildDeptSnapshot();
            deptSnapshot = snapshot;
        }
        return snapshot;
    }

    private GroupTreeSnapshot buildGroupSnapshot() {
        List<AcGroup> groups = acGroupService.getAllGroups();
        Map<Long, List<Long>> childrenMap = new HashMap<>();
        Map<String, Set<Long>> groupIdsByType = new HashMap<>();
        Set<Long> allGroupIds = new HashSet<>();
        if (FuncUtil.isNotEmpty(groups)) {
            for (AcGroup group : groups) {
                allGroupIds.add(group.getId());
                groupIdsByType.computeIfAbsent(group.getType(), key -> new HashSet<>()).add(group.getId());
                if (FuncUtil.isNotEmpty(group.getPid())) {
                    childrenMap.computeIfAbsent(group.getPid(), key -> new ArrayList<>()).add(group.getId());
                }
            }
        }
        log.debug("[权限树] 组树快照刷新：节点数={}, 关系节点数={}", allGroupIds.size(), childrenMap.size());
        return new GroupTreeSnapshot(childrenMap, groupIdsByType, allGroupIds);
    }

    private DeptTreeSnapshot buildDeptSnapshot() {
        // 全量建图（含停用部门），保证树的连通性
        List<AcDept> allDepts = acDeptService.select(acDeptService.getQueryWrapper());
        Map<String, List<String>> childrenMap = new HashMap<>();
        if (FuncUtil.isNotEmpty(allDepts)) {
            for (AcDept dept : allDepts) {
                if (FuncUtil.isNotEmpty(dept.getPid())) {
                    childrenMap.computeIfAbsent(dept.getPid(), key -> new ArrayList<>()).add(dept.getDeptId());
                }
            }
        }
        // 有效部门单独查询，谓词与 AcDeptDict / PermitSourceService 保持字面一致
        LambdaQueryWrapper<AcDept> validWrapper = acDeptService.getQueryWrapper().eq(AcDept::getStatus, CommonConst.YES);
        List<AcDept> validDepts = acDeptService.select(validWrapper);
        Set<String> validDeptIds = new HashSet<>();
        if (FuncUtil.isNotEmpty(validDepts)) {
            for (AcDept dept : validDepts) {
                validDeptIds.add(dept.getDeptId());
            }
        }
        log.debug("[权限树] 部门树快照刷新：节点数={}, 有效部门数={}, 关系节点数={}",
                FuncUtil.isEmpty(allDepts) ? 0 : allDepts.size(), validDeptIds.size(), childrenMap.size());
        return new DeptTreeSnapshot(childrenMap, validDeptIds);
    }

    /**
     * 用户组树快照（不可变，构建后只读）
     */
    private static class GroupTreeSnapshot {
        private final Map<Long, List<Long>> childrenMap;
        private final Map<String, Set<Long>> groupIdsByType;
        private final Set<Long> allGroupIds;
        private final long buildAt;

        GroupTreeSnapshot(Map<Long, List<Long>> childrenMap, Map<String, Set<Long>> groupIdsByType, Set<Long> allGroupIds) {
            this.childrenMap = childrenMap;
            this.groupIdsByType = groupIdsByType;
            this.allGroupIds = allGroupIds;
            this.buildAt = System.currentTimeMillis();
        }

        boolean expired() {
            return System.currentTimeMillis() - buildAt > SNAPSHOT_TTL_MILLIS;
        }
    }

    /**
     * 部门树快照（不可变，构建后只读）
     */
    private static class DeptTreeSnapshot {
        private final Map<String, List<String>> childrenMap;
        private final Set<String> validDeptIds;
        private final long buildAt;

        DeptTreeSnapshot(Map<String, List<String>> childrenMap, Set<String> validDeptIds) {
            this.childrenMap = childrenMap;
            this.validDeptIds = validDeptIds;
            this.buildAt = System.currentTimeMillis();
        }

        boolean expired() {
            return System.currentTimeMillis() - buildAt > SNAPSHOT_TTL_MILLIS;
        }
    }
}
