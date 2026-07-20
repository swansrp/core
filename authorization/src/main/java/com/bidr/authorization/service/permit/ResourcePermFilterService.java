package com.bidr.authorization.service.permit;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.bidr.authorization.dao.entity.AcResourcePerm;
import com.bidr.authorization.dao.entity.AcUserDept;
import com.bidr.authorization.dao.entity.AcUserGroup;
import com.bidr.authorization.dao.repository.AcResourcePermService;
import com.bidr.authorization.dao.repository.AcUserDeptService;
import com.bidr.authorization.dao.repository.AcUserGroupService;
import com.bidr.authorization.holder.AccountContext;
import com.bidr.kernel.utils.FuncUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 通用资源权限过滤Service
 * <p>
 * 权限判断规则：
 * 1. 无授权记录 → 不限制，所有人可见
 * 2. 有授权记录 → 检查当前用户是否匹配任一授权主体
 * 3. 管理员 → 跳过检查，全部可见
 *
 * @author Sharp
 * @since 2026/07/20
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ResourcePermFilterService {

    private final AcResourcePermService acResourcePermService;
    private final AcUserGroupService acUserGroupService;
    private final AcUserDeptService acUserDeptService;
    private final PermitService permitService;

    /**
     * 授权主体类型常量
     */
    public static final int SUBJECT_TYPE_ROLE = 0;
    public static final int SUBJECT_TYPE_USER = 1;
    public static final int SUBJECT_TYPE_GROUP = 2;
    public static final int SUBJECT_TYPE_DEPT = 3;

    /**
     * 判断当前用户是否有某资源的权限
     *
     * @param resourceType 资源类型（表名）
     * @param resourceId   资源ID（表主键）
     * @return true=有权限
     */
    public boolean hasPermission(String resourceType, String resourceId) {
        // 管理员直接通过
        if (permitService.isAdmin()) {
            return true;
        }

        // 查询该资源的授权记录
        List<AcResourcePerm> permList = acResourcePermService.getByResource(resourceType, resourceId);

        // 无授权记录 → 不限制
        if (FuncUtil.isEmpty(permList)) {
            return true;
        }

        // 有授权记录 → 匹配当前用户
        return matchCurrentUser(permList);
    }

    /**
     * 批量过滤：传入资源ID列表，返回当前用户有权限的子集
     *
     * @param resourceType 资源类型（表名）
     * @param resourceIds  资源ID列表
     * @return 有权限的资源ID子集
     */
    public List<String> filterAccessibleIds(String resourceType, List<String> resourceIds) {
        if (FuncUtil.isEmpty(resourceIds)) {
            return new ArrayList<>();
        }

        // 管理员直接返回全部
        if (permitService.isAdmin()) {
            log.info("[资源权限] resourceType={}, 请求资源数={} → 当前用户是管理员，跳过过滤全部可见", resourceType, resourceIds.size());
            return resourceIds;
        }

        // 批量查询所有相关授权记录（1次SQL）
        List<AcResourcePerm> allPerms = acResourcePermService.getByResourceIds(resourceType, resourceIds);
        log.info("[资源权限] resourceType={}, 请求资源数={}, 查到授权记录数={}", resourceType, resourceIds.size(), FuncUtil.isEmpty(allPerms) ? 0 : allPerms.size());

        // 无授权记录 → 全部可见
        if (FuncUtil.isEmpty(allPerms)) {
            log.info("[资源权限] resourceType={} 无任何授权记录 → 全部资源不限制，全部可见", resourceType);
            return resourceIds;
        }

        // 提前一次性查询当前用户的身份信息（避免循环内重复查SQL）
        UserIdentity identity = resolveUserIdentity();
        log.info("[资源权限] 当前用户身份: customerNumber={}, roleIds={}, groupIds={}, deptId={}",
                identity.customerNumber, identity.roleIds, identity.groupIds, identity.deptId);

        // 按 resourceId 分组
        Map<String, List<AcResourcePerm>> permMap = allPerms.stream()
                .collect(Collectors.groupingBy(AcResourcePerm::getResourceId));

        // 过滤（纯内存操作，无SQL）
        List<String> accessibleIds = new ArrayList<>();
        for (String resourceId : resourceIds) {
            List<AcResourcePerm> perms = permMap.get(resourceId);
            // 该资源无授权记录 → 不限制
            if (FuncUtil.isEmpty(perms)) {
                accessibleIds.add(resourceId);
                log.info("[资源权限] resourceId={} → 可见（该资源无授权记录，不限制）", resourceId);
            } else {
                String reason = matchReason(perms, identity);
                if (reason != null) {
                    accessibleIds.add(resourceId);
                    log.info("[资源权限] resourceId={} → 可见（{}）", resourceId, reason);
                } else {
                    log.info("[资源权限] resourceId={} → 不可见（有{}条授权记录但当前用户不匹配）", resourceId, perms.size());
                }
            }
        }
        log.info("[资源权限] resourceType={} 过滤结果: 请求{}个 → 可见{}个", resourceType, resourceIds.size(), accessibleIds.size());
        return accessibleIds;
    }

    /**
     * 当前用户身份信息（一次性查询，避免循环SQL）
     */
    private static class UserIdentity {
        String customerNumber;
        List<Long> roleIds;
        Set<String> groupIds;
        String deptId;
    }

    /**
     * 一次性解析当前用户的完整身份信息
     */
    private UserIdentity resolveUserIdentity() {
        UserIdentity identity = new UserIdentity();
        identity.customerNumber = AccountContext.getOperator();
        identity.roleIds = AccountContext.getRoleIdList();
        Long userId = AccountContext.getUserId();

        // 查用户组（1次SQL）
        identity.groupIds = new HashSet<>();
        if (FuncUtil.isNotEmpty(userId)) {
            List<AcUserGroup> userGroups = acUserGroupService.select(
                    new LambdaQueryWrapper<AcUserGroup>().eq(AcUserGroup::getUserId, userId));
            if (FuncUtil.isNotEmpty(userGroups)) {
                for (AcUserGroup ug : userGroups) {
                    identity.groupIds.add(String.valueOf(ug.getGroupId()));
                }
            }
        }

        // 查部门（1次SQL）
        identity.deptId = null;
        if (FuncUtil.isNotEmpty(userId)) {
            AcUserDept userDept = acUserDeptService.getByUserId(userId);
            if (FuncUtil.isNotEmpty(userDept)) {
                identity.deptId = userDept.getDeptId();
            }
        }

        return identity;
    }

    /**
     * 判断单个资源是否有权限（内部调用，会查SQL，适用于单条判断场景）
     */
    private boolean matchCurrentUser(List<AcResourcePerm> permList) {
        return matchIdentity(permList, resolveUserIdentity());
    }

    /**
     * 纯内存匹配：判断授权列表是否命中用户身份（无SQL）
     */
    private boolean matchIdentity(List<AcResourcePerm> permList, UserIdentity identity) {
        return matchReason(permList, identity) != null;
    }

    /**
     * 纯内存匹配：返回命中的授权原因（用于日志追踪），未命中返回 null
     */
    private String matchReason(List<AcResourcePerm> permList, UserIdentity identity) {
        // 按主体类型分组
        Map<Integer, Set<String>> subjectMap = permList.stream()
                .collect(Collectors.groupingBy(
                        AcResourcePerm::getSubjectType,
                        Collectors.mapping(AcResourcePerm::getSubjectId, Collectors.toSet())
                ));

        // 1. 检查角色匹配
        Set<String> permRoleIds = subjectMap.get(SUBJECT_TYPE_ROLE);
        if (FuncUtil.isNotEmpty(permRoleIds) && FuncUtil.isNotEmpty(identity.roleIds)) {
            for (Long roleId : identity.roleIds) {
                if (permRoleIds.contains(String.valueOf(roleId))) {
                    return "角色匹配 roleId=" + roleId;
                }
            }
        }

        // 2. 检查用户匹配
        Set<String> permUsers = subjectMap.get(SUBJECT_TYPE_USER);
        if (FuncUtil.isNotEmpty(permUsers) && FuncUtil.isNotEmpty(identity.customerNumber)) {
            if (permUsers.contains(identity.customerNumber)) {
                return "用户匹配 customerNumber=" + identity.customerNumber;
            }
        }

        // 3. 检查用户组匹配
        Set<String> permGroups = subjectMap.get(SUBJECT_TYPE_GROUP);
        if (FuncUtil.isNotEmpty(permGroups) && FuncUtil.isNotEmpty(identity.groupIds)) {
            for (String groupId : identity.groupIds) {
                if (permGroups.contains(groupId)) {
                    return "用户组匹配 groupId=" + groupId;
                }
            }
        }

        // 4. 检查部门匹配
        Set<String> permDepts = subjectMap.get(SUBJECT_TYPE_DEPT);
        if (FuncUtil.isNotEmpty(permDepts) && FuncUtil.isNotEmpty(identity.deptId)) {
            if (permDepts.contains(identity.deptId)) {
                return "部门匹配 deptId=" + identity.deptId;
            }
        }

        return null;
    }
}
