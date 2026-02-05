package com.bidr.authorization.service.permit;


import com.bidr.authorization.dao.entity.*;
import com.bidr.authorization.dao.repository.*;
import com.bidr.authorization.dao.repository.join.AcUserRoleMenuService;
import com.bidr.authorization.holder.AccountContext;
import com.bidr.authorization.holder.ClientTypeHolder;
import com.bidr.authorization.vo.permit.UserPermitRes;
import com.bidr.kernel.constant.CommonConst;
import com.bidr.kernel.utils.FuncUtil;
import com.github.yulichang.wrapper.MPJLambdaWrapper;
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
    private final AcUserRoleMenuService acUserRoleMenuService;
    private final AcDeptMenuService acDeptMenuService;
    private final AcGroupMenuService acGroupMenuService;
    private final AcUserMenuService acUserMenuService;
    
    private final AcUserService acUserService;
    private final AcRoleService acRoleService;
    private final AcDeptService acDeptService;
    private final AcGroupService acGroupService;
    private final AcMenuService acMenuService;
    private final AcRoleMenuService acRoleMenuService;
    private final AcUserRoleService acUserRoleService;
    private final AcUserDeptService acUserDeptService;
    private final AcUserGroupService acUserGroupService;



    /**
     * 获取用户的可解释权限信息
     * 
     * @param menuId 菜单ID
     * @param customerNumber 用户编码
     * @return 可解释的权限列表，包含权限来源详情
     */
    public List<UserPermitRes> getUserPermit(Long menuId, String customerNumber) {
        log.info("开始查询用户 {} 对菜单 {} 的可解释权限", customerNumber, menuId);
        
        // 获取用户信息
        AcUser user = acUserService.getByCustomerNumber(customerNumber);
        if (user == null) {
            log.warn("用户 {} 不存在", customerNumber);
            return new ArrayList<>();
        }
        
        Long userId = user.getUserId();
        String clientType = ClientTypeHolder.get();
        
        List<UserPermitRes> permitResults = new ArrayList<>();
        
        // 1. 查询角色权限来源
        List<UserPermitRes> rolePermits = getRolePermitSources(userId, menuId, clientType);
        permitResults.addAll(rolePermits);
        
        // 2. 查询部门权限来源（包括继承）
        List<UserPermitRes> deptPermits = getDeptPermitSources(userId, menuId, clientType);
        permitResults.addAll(deptPermits);
        
        // 3. 查询用户组权限来源（包括继承）
        List<UserPermitRes> groupPermits = getGroupPermitSources(userId, menuId, clientType);
        permitResults.addAll(groupPermits);
        
        // 4. 查询用户直接权限来源
        List<UserPermitRes> userPermits = getUserDirectPermitSources(userId, menuId, clientType);
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
            MPJLambdaWrapper<AcUserRole> roleWrapper = new MPJLambdaWrapper<AcUserRole>()
                    .selectAll(AcRole.class)
                    .leftJoin(AcRole.class, AcRole::getRoleId, AcUserRole::getRoleId)
                    .eq(AcUserRole::getUserId, userId)
                    .eq(AcRole::getStatus, CommonConst.YES);
            
            List<AcRole> roles = acUserRoleService.selectJoinList(AcRole.class, roleWrapper);
            
            // 收集角色ID列表
            List<Long> roleIds = roles.stream()
                    .map(AcRole::getRoleId)
                    .collect(Collectors.toList());
            
            // 使用wrapper in一次性查询所有角色的菜单权限
            if (FuncUtil.isNotEmpty(roleIds)) {
                MPJLambdaWrapper<AcRoleMenu> menuWrapper = new MPJLambdaWrapper<AcRoleMenu>()
                        .selectAll(AcMenu.class)
                        .innerJoin(AcMenu.class, AcMenu::getMenuId, AcRoleMenu::getMenuId)
                        .in(AcRoleMenu::getRoleId, roleIds)
                        .eq(AcMenu::getMenuId, menuId)
                        .eq(AcMenu::getClientType, clientType)
                        .eq(AcMenu::getStatus, CommonConst.YES)
                        .eq(AcMenu::getVisible, CommonConst.YES);
                
                List<AcMenu> menus = acRoleMenuService.selectJoinList(AcMenu.class, menuWrapper);
                
                // 如果找到了匹配的菜单权限，构建结果
                if (FuncUtil.isNotEmpty(menus)) {
                    // 创建角色ID到角色名称的映射
                    Map<Long, String> roleMap = roles.stream()
                            .collect(Collectors.toMap(AcRole::getRoleId, AcRole::getRoleName));
                    
                    // 为每个有权限的角色创建结果
                    roleIds.forEach(roleId -> {
                        UserPermitRes permitRes = new UserPermitRes(
                                UserPermitRes.PermitSourceType.ROLE,
                                roleId,
                                roleMap.get(roleId),
                                String.format("通过角色[%s]获得权限", roleMap.get(roleId))
                        );
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
            // 查询用户所属的部门
            MPJLambdaWrapper<AcUserDept> userDeptWrapper = new MPJLambdaWrapper<AcUserDept>()
                    .selectAll(AcDept.class)
                    .leftJoin(AcDept.class, AcDept::getDeptId, AcUserDept::getDeptId)
                    .eq(AcUserDept::getUserId, userId)
                    .eq(AcDept::getStatus, CommonConst.YES);
            
            List<AcDept> depts = acUserDeptService.selectJoinList(AcDept.class, userDeptWrapper);
            
            // 收集部门ID列表
            List<String> deptIds = depts.stream()
                    .map(AcDept::getDeptId)
                    .collect(Collectors.toList());
            
            // 使用wrapper in一次性查询所有部门的直接菜单权限
            if (FuncUtil.isNotEmpty(deptIds)) {
                MPJLambdaWrapper<AcDeptMenu> directMenuWrapper = new MPJLambdaWrapper<AcDeptMenu>()
                        .selectAll(AcMenu.class)
                        .innerJoin(AcMenu.class, AcMenu::getMenuId, AcDeptMenu::getMenuId)
                        .in(AcDeptMenu::getDeptId, deptIds)
                        .eq(AcMenu::getMenuId, menuId)
                        .eq(AcMenu::getClientType, clientType)
                        .eq(AcMenu::getStatus, CommonConst.YES)
                        .eq(AcMenu::getVisible, CommonConst.YES);
                
                List<AcMenu> directMenus = acDeptMenuService.selectJoinList(AcMenu.class, directMenuWrapper);
                
                if (FuncUtil.isNotEmpty(directMenus)) {
                    // 创建部门ID到部门名称的映射
                    Map<String, String> deptMap = depts.stream()
                            .collect(Collectors.toMap(AcDept::getDeptId, AcDept::getName));
                    
                    // 为每个有直接权限的部门创建结果
                    deptIds.forEach(deptId -> {
                        if (checkDeptMenuPermission(deptId, menuId, clientType)) {
                            UserPermitRes permitRes = new UserPermitRes(
                                    UserPermitRes.PermitSourceType.DEPT,
                                    Long.valueOf(deptId),
                                    deptMap.get(deptId),
                                    String.format("通过部门[%s]直接获得权限", deptMap.get(deptId))
                            );
                            results.add(permitRes);
                        }
                    });
                }
                
                // 检查继承权限
                depts.forEach(dept -> {
                    if (checkDeptInheritancePermission(dept, menuId, clientType)) {
                        UserPermitRes permitRes = new UserPermitRes(
                                UserPermitRes.PermitSourceType.DEPT_DATA_SCOPE,
                                Long.valueOf(dept.getDeptId()),
                                dept.getName(),
                                String.format("通过部门[%s]的数据范围继承获得权限", dept.getName())
                        );
                        results.add(permitRes);
                    }
                });
            }
        } catch (Exception e) {
            log.error("查询部门权限来源失败", e);
        }
        
        return results;
    }
    
    /**
     * 检查部门是否拥有指定菜单权限
     */
    private boolean checkDeptMenuPermission(String deptId, Long menuId, String clientType) {
        MPJLambdaWrapper<AcDeptMenu> wrapper = new MPJLambdaWrapper<AcDeptMenu>()
                .innerJoin(AcMenu.class, AcMenu::getMenuId, AcDeptMenu::getMenuId)
                .eq(AcDeptMenu::getDeptId, deptId)
                .eq(AcMenu::getMenuId, menuId)
                .eq(AcMenu::getClientType, clientType)
                .eq(AcMenu::getStatus, CommonConst.YES)
                .eq(AcMenu::getVisible, CommonConst.YES);
        
        return acDeptMenuService.existed(wrapper);
    }
    
    /**
     * 检查部门继承权限
     */
    private boolean checkDeptInheritancePermission(AcDept dept, Long menuId, String clientType) {
        // 这里可以实现更复杂的继承逻辑检查
        // 目前简化处理：如果有子部门且子部门有权限，则认为有继承权限
        return false; // 简化实现，实际可根据需要扩展
    }
    
    /**
     * 获取用户组权限来源（包括继承权限）
     */
    private List<UserPermitRes> getGroupPermitSources(Long userId, Long menuId, String clientType) {
        List<UserPermitRes> results = new ArrayList<>();
        
        try {
            // 查询用户所属的用户组
            MPJLambdaWrapper<AcUserGroup> userGroupWrapper = new MPJLambdaWrapper<AcUserGroup>()
                    .selectAll(AcGroup.class)
                    .leftJoin(AcGroup.class, AcGroup::getId, AcUserGroup::getGroupId)
                    .eq(AcUserGroup::getUserId, userId);
            
            List<AcGroup> groups = acUserGroupService.selectJoinList(AcGroup.class, userGroupWrapper);
            
            // 收集用户组ID列表
            List<Long> groupIds = groups.stream()
                    .map(AcGroup::getId)
                    .collect(Collectors.toList());
            
            // 使用wrapper in一次性查询所有用户组的直接菜单权限
            if (FuncUtil.isNotEmpty(groupIds)) {
                MPJLambdaWrapper<AcGroupMenu> directMenuWrapper = new MPJLambdaWrapper<AcGroupMenu>()
                        .selectAll(AcMenu.class)
                        .innerJoin(AcMenu.class, AcMenu::getMenuId, AcGroupMenu::getMenuId)
                        .in(AcGroupMenu::getGroupId, groupIds)
                        .eq(AcMenu::getMenuId, menuId)
                        .eq(AcMenu::getClientType, clientType)
                        .eq(AcMenu::getStatus, CommonConst.YES)
                        .eq(AcMenu::getVisible, CommonConst.YES);
                
                List<AcMenu> directMenus = acGroupMenuService.selectJoinList(AcMenu.class, directMenuWrapper);
                
                if (FuncUtil.isNotEmpty(directMenus)) {
                    // 创建用户组ID到用户组名称的映射
                    Map<Long, String> groupMap = groups.stream()
                            .collect(Collectors.toMap(AcGroup::getId, AcGroup::getName));
                    
                    // 为每个有直接权限的用户组创建结果
                    groupIds.forEach(groupId -> {
                        if (checkGroupMenuPermission(groupId, menuId, clientType)) {
                            UserPermitRes permitRes = new UserPermitRes(
                                    UserPermitRes.PermitSourceType.GROUP,
                                    groupId,
                                    groupMap.get(groupId),
                                    String.format("通过用户组[%s]直接获得权限", groupMap.get(groupId))
                            );
                            results.add(permitRes);
                        }
                    });
                }
                
                // 检查继承权限
                groups.forEach(group -> {
                    if (checkGroupInheritancePermission(group, menuId, clientType)) {
                        UserPermitRes permitRes = new UserPermitRes(
                                UserPermitRes.PermitSourceType.GROUP_DATA_SCOPE,
                                group.getId(),
                                group.getName(),
                                String.format("通过用户组[%s]的数据范围继承获得权限", group.getName())
                        );
                        results.add(permitRes);
                    }
                });
            }
        } catch (Exception e) {
            log.error("查询用户组权限来源失败", e);
        }
        
        return results;
    }
    
    /**
     * 检查用户组是否拥有指定菜单权限
     */
    private boolean checkGroupMenuPermission(Long groupId, Long menuId, String clientType) {
        MPJLambdaWrapper<AcGroupMenu> wrapper = new MPJLambdaWrapper<AcGroupMenu>()
                .innerJoin(AcMenu.class, AcMenu::getMenuId, AcGroupMenu::getMenuId)
                .eq(AcGroupMenu::getGroupId, groupId)
                .eq(AcMenu::getMenuId, menuId)
                .eq(AcMenu::getClientType, clientType)
                .eq(AcMenu::getStatus, CommonConst.YES)
                .eq(AcMenu::getVisible, CommonConst.YES);
        
        return acGroupMenuService.existed(wrapper);
    }
    
    /**
     * 检查用户组继承权限
     */
    private boolean checkGroupInheritancePermission(AcGroup group, Long menuId, String clientType) {
        // 简化实现，可根据需要扩展
        return false;
    }
    
    /**
     * 获取用户直接权限来源
     */
    private List<UserPermitRes> getUserDirectPermitSources(Long userId, Long menuId, String clientType) {
        List<UserPermitRes> results = new ArrayList<>();
        
        try {
            MPJLambdaWrapper<AcUserMenu> wrapper = new MPJLambdaWrapper<AcUserMenu>()
                    .selectAll(AcMenu.class)
                    .innerJoin(AcMenu.class, AcMenu::getMenuId, AcUserMenu::getMenuId)
                    .eq(AcUserMenu::getUserId, userId)
                    .eq(AcMenu::getMenuId, menuId)
                    .eq(AcMenu::getClientType, clientType)
                    .eq(AcMenu::getStatus, CommonConst.YES)
                    .eq(AcMenu::getVisible, CommonConst.YES);
            
            List<AcMenu> menus = acUserMenuService.selectJoinList(AcMenu.class, wrapper);
            if (FuncUtil.isNotEmpty(menus)) {
                UserPermitRes permitRes = new UserPermitRes(
                        UserPermitRes.PermitSourceType.USER,
                        userId,
                        "用户直接授权",
                        "通过用户直接授权获得权限"
                );
                results.add(permitRes);
            }
        } catch (Exception e) {
            log.error("查询用户直接权限来源失败", e);
        }
        
        return results;
    }
}
