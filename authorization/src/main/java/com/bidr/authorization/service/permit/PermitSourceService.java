package com.bidr.authorization.service.permit;


import com.bidr.authorization.constants.dict.DataPermitScopeDict;
import com.bidr.authorization.dao.entity.*;
import com.bidr.authorization.dao.repository.*;
import com.bidr.authorization.holder.ClientTypeHolder;
import com.bidr.authorization.vo.permit.UserPermitRes;
import com.bidr.kernel.constant.CommonConst;
import com.bidr.kernel.mybatis.dao.repository.RecursionService;
import com.bidr.kernel.utils.FuncUtil;
import com.bidr.kernel.utils.ReflectionUtil;
import com.github.yulichang.wrapper.MPJLambdaWrapper;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

/**
 * @author Sharp
 * @since 2026/2/5 17:27
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class PermitSourceService {
    private final AcDeptMenuService acDeptMenuService;
    private final AcGroupMenuService acGroupMenuService;
    private final AcUserMenuService acUserMenuService;
    private final AcUserService acUserService;
    private final AcRoleMenuService acRoleMenuService;
    private final AcUserRoleService acUserRoleService;
    private final AcUserDeptService acUserDeptService;
    private final AcUserGroupService acUserGroupService;
    private final RecursionService recursionService;
    private final AcDeptService acDeptService;
    private final AcGroupService acGroupService;

    /**
     * 获取用户的可解释权限信息
     *
     * @param menuId         菜单ID
     * @param customerNumber 用户编码
     * @return 可解释的权限列表，包含权限来源详情
     */
    public List<UserPermitRes> getUserPermitSource(Long menuId, String customerNumber) {
        log.info("开始查询用户 {} 对菜单 {} 的可解释权限", customerNumber, menuId);

        // 获取用户信息
        AcUser user = acUserService.getByCustomerNumber(customerNumber);
        if (user == null) {
            log.warn("用户 {} 不存在", customerNumber);
            return new ArrayList<>();
        }

        Long userId = user.getUserId();
        String clientType = ClientTypeHolder.get();

        // 1. 查询角色权限来源
        List<UserPermitRes> rolePermits = getRolePermitSources(userId, menuId, clientType);
        List<UserPermitRes> permitResults = new ArrayList<>(rolePermits);

        // 2. 查询部门权限来源（包括继承）
        List<UserPermitRes> deptPermits = getDeptPermitSources(userId, menuId, clientType);
        permitResults.addAll(deptPermits);

        // 3. 查询用户组权限来源（包括继承）
        List<UserPermitRes> groupPermits = getGroupPermitSources(userId, menuId, clientType);
        permitResults.addAll(groupPermits);

        // 4. 查询用户直接权限来源
        List<UserPermitRes> userPermits = getUserDirectPermitSources(customerNumber, userId, menuId, clientType);
        permitResults.addAll(userPermits);

        log.info("用户 {} 对菜单 {} 共找到 {} 条权限来源", customerNumber, menuId, permitResults.size());
        return permitResults;
    }

    /**
     * 获取角色权限来源
     */
    private List<UserPermitRes> getRolePermitSources(Long userId, Long menuId, String clientType) {
        List<UserPermitRes> results = new ArrayList<>();

        try {
            // 查询用户拥有的角色
            MPJLambdaWrapper<AcUserRole> roleWrapper = new MPJLambdaWrapper<AcUserRole>().selectAll(AcRole.class).leftJoin(AcRole.class, AcRole::getRoleId,
                    AcUserRole::getRoleId).eq(AcUserRole::getUserId, userId);

            List<AcRole> roles = acUserRoleService.selectJoinList(AcRole.class, roleWrapper);

            // 收集角色ID列表
            List<Long> roleIds = roles.stream().map(AcRole::getRoleId).collect(Collectors.toList());

            // 使用wrapper in一次性查询所有角色的菜单权限
            if (FuncUtil.isNotEmpty(roleIds)) {
                MPJLambdaWrapper<AcRoleMenu> menuWrapper = new MPJLambdaWrapper<AcRoleMenu>().selectAll(AcMenu.class).innerJoin(AcMenu.class,
                        AcMenu::getMenuId, AcRoleMenu::getMenuId).in(AcRoleMenu::getRoleId, roleIds).eq(AcMenu::getMenuId, menuId).eq(AcMenu::getClientType,
                        clientType).eq(AcMenu::getStatus, CommonConst.YES);

                List<AcMenu> menus = acRoleMenuService.selectJoinList(AcMenu.class, menuWrapper);

                // 如果找到了匹配的菜单权限，构建结果
                if (FuncUtil.isNotEmpty(menus)) {
                    // 创建角色ID到角色名称的映射
                    Map<Long, String> roleMap = roles.stream().collect(Collectors.toMap(AcRole::getRoleId, AcRole::getRoleName));

                    // 为每个有权限的角色创建结果
                    roleIds.forEach(roleId -> {
                        UserPermitRes permitRes = new UserPermitRes(UserPermitRes.PermitSourceType.ROLE, roleId, roleMap.get(roleId), String.format("通过角色[%s" +
                                "]获得权限", roleMap.get(roleId)));
                        results.add(permitRes);
                    });
                }
            }
        } catch (Exception e) {
            log.error("查询角色权限来源失败", e);
        }

        return results;
    }

    /**
     * 获取部门权限来源（包括继承权限）
     */
    private List<UserPermitRes> getDeptPermitSources(Long userId, Long menuId, String clientType) {
        List<UserPermitRes> results = new ArrayList<>();

        try {
            // 查询用户所属部门信息
            List<DeptWithScope> userDepts = getUserDepartments(userId);
            if (FuncUtil.isEmpty(userDepts)) {
                return results;
            }

            // 处理直接权限
            processDeptPermissions(results, userDepts, menuId, clientType, "直接");

            // 处理继承权限
            List<DeptWithScope> inheritDepts = new ArrayList<>();
            for (DeptWithScope dept : userDepts) {
                if (DataPermitScopeDict.SUBORDINATE.getValue().equals(dept.getDataScope())) {
                    List<String> childIds = recursionService.getChildList(AcDept::getDeptId, AcDept::getPid, dept.getDeptId());
                    if (FuncUtil.isNotEmpty(childIds)) {
                        for (String childId : childIds) {
                            DeptWithScope inheritDeptWithScope = new DeptWithScope();
                            inheritDeptWithScope.setDeptId(childId);
                            AcDept inheritDept = acDeptService.selectById(childId);
                            // 实际应用中应查询真实部门名称
                            inheritDeptWithScope.setName("继承部门-" + inheritDept.getName());
                            inheritDeptWithScope.setDataScope(DataPermitScopeDict.SUBORDINATE.getValue());
                            inheritDepts.add(inheritDeptWithScope);
                        }
                    }
                }
            }

            if (FuncUtil.isNotEmpty(inheritDepts)) {
                processDeptPermissions(results, inheritDepts, menuId, clientType, "继承");
            }

        } catch (Exception e) {
            log.error("查询部门权限来源失败，userId={}, menuId={}, clientType={}", userId, menuId, clientType, e);
        }

        return results;
    }

    /**
     * 获取用户组权限来源（包括继承权限）
     */
    private List<UserPermitRes> getGroupPermitSources(Long userId, Long menuId, String clientType) {
        List<UserPermitRes> results = new ArrayList<>();

        try {
            // 查询用户所属的用户组
            List<GroupWithScope> userGroups = getUserGroupsWithScope(userId);
            if (FuncUtil.isEmpty(userGroups)) {
                return results;
            }

            // 处理直接权限
            processGroupDirectPermissions(results, userGroups, menuId, clientType, "直接");

            // 处理继承权限
            List<GroupWithScope> inheritGroups = new ArrayList<>();
            for (GroupWithScope group : userGroups) {
                if (DataPermitScopeDict.SUBORDINATE.getValue().equals(group.getDataScope())) {
                    List<Long> childIds = recursionService.getChildList(AcGroup::getId, AcGroup::getPid, group.getId());
                    if (FuncUtil.isNotEmpty(childIds)) {
                        for (Long childId : childIds) {
                            GroupWithScope inheritGroupWithScope = new GroupWithScope();
                            inheritGroupWithScope.setId(childId);
                            AcGroup inheritAcGroup = acGroupService.selectById(childId);
                            inheritGroupWithScope.setName("继承用户组-" + inheritAcGroup.getName());
                            inheritGroupWithScope.setDataScope(DataPermitScopeDict.SUBORDINATE.getValue());
                            inheritGroups.add(inheritGroupWithScope);
                        }
                    }
                }
            }

            if (FuncUtil.isNotEmpty(inheritGroups)) {
                processGroupDirectPermissions(results, inheritGroups, menuId, clientType, "继承");
            }

        } catch (Exception e) {
            log.error("查询用户组权限来源失败，userId={}, menuId={}, clientType={}", userId, menuId, clientType, e);
        }

        return results;
    }


    /**
     * 查询用户所属部门信息
     */
    private List<DeptWithScope> getUserDepartments(Long userId) {
        MPJLambdaWrapper<AcUserDept> userDeptWrapper = new MPJLambdaWrapper<AcUserDept>().selectAll(AcDept.class).selectAs(AcUserDept::getUserId,
                DeptWithScope::getUserId).selectAs(AcUserDept::getDataScope, DeptWithScope::getDataScope).leftJoin(AcDept.class, AcDept::getDeptId,
                AcUserDept::getDeptId).eq(AcUserDept::getUserId, userId).eq(AcDept::getStatus, CommonConst.YES);
        return acUserDeptService.selectJoinList(DeptWithScope.class, userDeptWrapper);
    }

    /**
     * 查询用户所属的用户组（包含数据范围信息）
     */
    private List<GroupWithScope> getUserGroupsWithScope(Long userId) {
        MPJLambdaWrapper<AcUserGroup> userGroupWrapper = new MPJLambdaWrapper<AcUserGroup>().selectAll(AcGroup.class).selectAs(AcUserGroup::getUserId,
                GroupWithScope::getUserId).selectAs(AcUserGroup::getDataScope, GroupWithScope::getDataScope).leftJoin(AcGroup.class, AcGroup::getId,
                AcUserGroup::getGroupId).eq(AcUserGroup::getUserId, userId);
        return acUserGroupService.selectJoinList(GroupWithScope.class, userGroupWrapper);
    }


    /**
     * 处理用户组直接权限（支持GroupWithScope类型）
     */
    private void processGroupDirectPermissions(List<UserPermitRes> results, List<GroupWithScope> groups, Long menuId, String clientType,
                                               String permissionType) {
        // 收集用户组ID列表
        List<Long> groupIds = groups.stream().map(AcGroup::getId).collect(Collectors.toList());

        if (FuncUtil.isNotEmpty(groupIds)) {
            // 创建用户组ID到用户组名称的映射
            Map<Long, String> groupMap = groups.stream().collect(Collectors.toMap(AcGroup::getId, AcGroup::getName));

            // 为每个有权限的用户组创建结果
            groupIds.forEach(groupId -> {
                if (checkGroupMenuPermission(groupId, menuId, clientType)) {
                    UserPermitRes permitRes = new UserPermitRes(UserPermitRes.PermitSourceType.GROUP, groupId, groupMap.get(groupId), String.format("通过用户组[%s" +
                            "]%s获得权限", groupMap.get(groupId), permissionType));
                    results.add(permitRes);
                }
            });
        }
    }

    /**
     * 批量检查部门直接权限
     */
    private Set<String> batchCheckDeptDirectPermissions(List<String> deptIds, Long menuId, String clientType) {
        Set<String> permitDeptIds = new HashSet<>();

        if (FuncUtil.isEmpty(deptIds)) {
            return permitDeptIds;
        }

        MPJLambdaWrapper<AcDeptMenu> directMenuWrapper = new MPJLambdaWrapper<AcDeptMenu>().select(AcDeptMenu::getDeptId).innerJoin(AcMenu.class,
                AcMenu::getMenuId, AcDeptMenu::getMenuId).in(AcDeptMenu::getDeptId, deptIds).eq(AcMenu::getMenuId, menuId).eq(AcMenu::getClientType,
                clientType).eq(AcMenu::getStatus, CommonConst.YES).groupBy(AcDeptMenu::getDeptId);

        List<AcDeptMenu> permittedDepts = acDeptMenuService.selectJoinList(AcDeptMenu.class, directMenuWrapper);

        return permittedDepts.stream().map(AcDeptMenu::getDeptId).filter(Objects::nonNull).collect(Collectors.toSet());
    }

    /**
     * 处理部门权限（通用方法用于直属和继承权限）
     */
    private void processDeptPermissions(List<UserPermitRes> results, List<DeptWithScope> depts, Long menuId, String clientType, String permissionType) {
        // 构建部门映射和ID列表
        Map<String, DeptWithScope> deptMap = ReflectionUtil.reflectToMap(depts, DeptWithScope::getDeptId);
        List<String> deptIds = new ArrayList<>(deptMap.keySet());

        // 批量检查权限
        Set<String> permitDeptIds = batchCheckDeptDirectPermissions(deptIds, menuId, clientType);

        // 添加权限结果
        permitDeptIds.forEach(deptId -> {
            DeptWithScope dept = deptMap.get(deptId);
            if (dept != null) {
                results.add(createDeptPermitResult(UserPermitRes.PermitSourceType.DEPT, dept.getDeptId(), dept.getName(), String.format("通过部门[%s]%s获得权限",
                        dept.getName(), permissionType)));
            }
        });
    }

    /**
     * 创建部门权限结果对象
     */
    private UserPermitRes createDeptPermitResult(UserPermitRes.PermitSourceType sourceType, String deptId, String deptName, String path) {
        return new UserPermitRes(sourceType, Long.valueOf(deptId), deptName, path);
    }

    /**
     * 检查用户组是否拥有指定菜单权限
     */
    private boolean checkGroupMenuPermission(Long groupId, Long menuId, String clientType) {
        MPJLambdaWrapper<AcGroupMenu> wrapper =
                new MPJLambdaWrapper<AcGroupMenu>().innerJoin(AcMenu.class, AcMenu::getMenuId, AcGroupMenu::getMenuId).eq(AcGroupMenu::getGroupId, groupId).eq(AcMenu::getMenuId, menuId).eq(AcMenu::getClientType, clientType).eq(AcMenu::getStatus, CommonConst.YES);

        return acGroupMenuService.existed(wrapper);
    }

    /**
     * 获取用户直接权限来源
     */
    private List<UserPermitRes> getUserDirectPermitSources(String customerNumber, Long userId, Long menuId, String clientType) {
        List<UserPermitRes> results = new ArrayList<>();

        try {
            MPJLambdaWrapper<AcUserMenu> wrapper = new MPJLambdaWrapper<AcUserMenu>().selectAll(AcMenu.class).innerJoin(AcMenu.class, AcMenu::getMenuId,
                    AcUserMenu::getMenuId).eq(AcUserMenu::getCustomerNumber, customerNumber).eq(AcMenu::getMenuId, menuId).eq(AcMenu::getClientType,
                    clientType).eq(AcMenu::getStatus, CommonConst.YES);

            List<AcMenu> menus = acUserMenuService.selectJoinList(AcMenu.class, wrapper);
            if (FuncUtil.isNotEmpty(menus)) {
                UserPermitRes permitRes = new UserPermitRes(UserPermitRes.PermitSourceType.USER, userId, "用户直接授权", "通过用户直接授权获得权限");
                results.add(permitRes);
            }
        } catch (Exception e) {
            log.error("查询用户直接权限来源失败", e);
        }

        return results;
    }

    @Data
    @EqualsAndHashCode(callSuper = true)
    public static class DeptWithScope extends AcDept {
        private Integer dataScope;
        private Long userId;
    }

    @Data
    @EqualsAndHashCode(callSuper = true)
    public static class GroupWithScope extends AcGroup {
        private Integer dataScope;
        private Long userId;
    }
}