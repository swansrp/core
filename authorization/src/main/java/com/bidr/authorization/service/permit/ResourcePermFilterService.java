package com.bidr.authorization.service.permit;

import com.bidr.authorization.constants.dict.ResourceSubjectTypeDict;
import com.bidr.authorization.dao.entity.AcResourcePerm;
import com.bidr.authorization.dao.repository.AcResourcePermService;
import com.bidr.authorization.holder.AccountContext;
import com.bidr.authorization.service.permit.subject.PermSubjectResolver;
import com.bidr.kernel.utils.FuncUtil;
import com.bidr.kernel.utils.JsonUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.stream.Collectors;

/**
 * 通用资源权限过滤Service
 * <p>
 * 权限判断规则：
 * 1. 无授权记录 → 不限制，所有人可见
 * 2. 有授权记录 → 检查当前用户是否匹配任一授权主体
 * 3. 管理员 → 跳过检查，全部可见（由 {@code resource.perm.admin-bypass} 控制，置 false 可临时关闭）
 * <p>
 * 「当前用户在各主体维度上持有哪些标识」由 {@link PermSubjectResolver} 提供：
 * 角色/用户直接取自登录上下文，用户组/部门还要按 dataScope 沿树向下展开（领导能看下属）。
 * 新增一个授权维度只需新增一个解析器实现，本类的匹配逻辑无需修改。
 *
 * @author Sharp
 * @since 2026/07/20
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ResourcePermFilterService {

    private final AcResourcePermService acResourcePermService;
    private final PermitService permitService;
    /**
     * 各授权主体维度的解析器，由 Spring 收集全部实现
     */
    private final List<PermSubjectResolver> permSubjectResolvers;

    /**
     * 管理员是否跳过资源权限判定
     * <p>
     * 默认 true（生产语义：管理员可见全部）。只有当手上只有管理员账号、需要实测权限拦截效果时，
     * 才在本地配置中置为 false；该开关不影响任何非管理员用户的判定结果。
     * </p>
     */
    @Value("${resource.perm.admin-bypass:true}")
    private boolean adminBypass;

    /**
     * subject_type 取值 → 解析器；字段自带初值，故不会被 @RequiredArgsConstructor 纳入构造器
     * <p>
     * 用 TreeMap 而非 HashMap：启动自检日志与解析器遍历顺序按 subject_type 升序固定，不受哈希桶分布影响。
     * </p>
     */
    private final Map<Integer, PermSubjectResolver> resolverBySubjectType = new TreeMap<>();

    @PostConstruct
    public void loadResolvers() {
        for (PermSubjectResolver resolver : permSubjectResolvers) {
            PermSubjectResolver duplicated = resolverBySubjectType.put(resolver.subjectType().getValue(), resolver);
            if (FuncUtil.isNotEmpty(duplicated)) {
                log.warn("[资源权限] 主体类型 {} 存在多个解析器：{} 覆盖 {}", resolver.subjectType().getLabel(),
                        duplicated.getClass().getSimpleName(), resolver.getClass().getSimpleName());
            }
        }
        // 只打数字看不出哪个维度缺失，故带上枚举名与实现类名：[角色(0)=RolePermSubjectResolver, ...]
        log.info("[资源权限] 已装载授权主体解析器：{}", resolverBySubjectType.entrySet().stream()
                .map(entry -> String.format("%s(%d)=%s", entry.getValue().subjectType().getLabel(), entry.getKey(),
                        entry.getValue().getClass().getSimpleName()))
                .collect(Collectors.joining(", ")));
    }

    /**
     * 判断当前用户是否有某资源的权限
     *
     * @param resourceType 资源类型（表名）
     * @param resourceId   资源ID（表主键）
     * @return true=有权限
     */
    public boolean hasPermission(String resourceType, String resourceId) {
        // 管理员直接通过
        if (adminBypass && permitService.isAdmin()) {
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
     * 查询当前用户在某资源上命中的授权行（含 extra_data），供行级权限策略提取
     * <p>
     * 与 hasPermission 的区别：那个只答「能不能用」，这里给出「命中的是哪几条」——
     * 每条命中记录可各自携带行条件，由调用方按 OR 合并。
     * 未命中任何记录时返回空列表：要么资源未配置授权（默认放行且无行限制），
     * 要么整体访问已被层 1 拒绝（走不到这里）。
     * 管理员不做特殊处理：层 1 的 admin-bypass 只管可见性，行策略仍按实际命中的记录生效，
     * 保证 admin-bypass=false 实测时管理员同样能被行条件限住。
     * </p>
     *
     * @param resourceType 资源类型（表名）
     * @param resourceId   资源标识
     * @return 命中的授权行列表，按 subject_type、subject_id 稳定排序
     */
    public List<AcResourcePerm> getMatchedRows(String resourceType, String resourceId) {
        List<AcResourcePerm> permList = acResourcePermService.getByResource(resourceType, resourceId);
        if (FuncUtil.isEmpty(permList)) {
            return Collections.emptyList();
        }
        Map<ResourceSubjectTypeDict, Set<String>> userSubjects = resolveUserSubjects();
        List<AcResourcePerm> matched = new ArrayList<>();
        for (AcResourcePerm perm : permList) {
            PermSubjectResolver resolver = resolverBySubjectType.get(perm.getSubjectType());
            if (FuncUtil.isEmpty(resolver)) {
                // 与 matchReason 同口径：未装载解析器的维度一律不参与匹配
                continue;
            }
            Set<String> ownedIds = userSubjects.get(resolver.subjectType());
            if (FuncUtil.isNotEmpty(ownedIds) && ownedIds.contains(perm.getSubjectId())) {
                matched.add(perm);
            }
        }
        // TreeMap 排序后遍历：多条策略的 OR 拼接顺序稳定，生成的 SQL 可复现便于比对
        Map<Integer, List<AcResourcePerm>> grouped = matched.stream()
                .collect(Collectors.groupingBy(AcResourcePerm::getSubjectType, TreeMap::new, Collectors.toList()));
        List<AcResourcePerm> ordered = new ArrayList<>(matched.size());
        grouped.values().forEach(rows -> {
            rows.sort((a, b) -> a.getSubjectId().compareTo(b.getSubjectId()));
            ordered.addAll(rows);
        });
        return ordered;
    }

    /**
     * 解析主体类条件变量（${currentCustomerNumber} 等）为当前用户的实际取值，供行权限条件注入
     * <p>
     * 组/部门返回 JSON 数组串（如 ["100","10001"]），配合 IN 关系使用；非数组场景调用方自行拆。
     * 未知 token 返回 null（交回解析链继续处理或原样保留）。
     * </p>
     *
     * @param token          变量名（不含 ${ 与 }）
     * @param userSubjects   {@link #currentUserSubjects()} 的结果（批量解析一次，逐值复用）
     * @param customerNumber 当前登录用户业务编号
     * @return 解析后的值；不识别的 token 返回 null
     */
    public static String resolveSubjectToken(String token, Map<ResourceSubjectTypeDict, Set<String>> userSubjects, String customerNumber) {
        if (FuncUtil.isEmpty(token)) {
            return null;
        }
        switch (token) {
            case "currentCustomerNumber":
                return customerNumber;
            case "currentDeptId":
                return firstOf(userSubjects.get(ResourceSubjectTypeDict.DEPT));
            case "currentGroupId":
                return firstOf(userSubjects.get(ResourceSubjectTypeDict.GROUP));
            case "currentDeptIds":
                return JsonUtil.toJson(sortedList(userSubjects.get(ResourceSubjectTypeDict.DEPT)));
            case "currentGroupIds":
                return JsonUtil.toJson(sortedList(userSubjects.get(ResourceSubjectTypeDict.GROUP)));
            default:
                return null;
        }
    }

    /**
     * 当前用户在各主体维度上持有的标识集合（供行权限条件变量解析，一次解析逐值复用）
     */
    public Map<ResourceSubjectTypeDict, Set<String>> currentUserSubjects() {
        return resolveUserSubjects();
    }

    /**
     * 取首个标识（排序后）：语义是「用户的任一个（主）部门/组」，多归属时结果稳定可复现
     */
    private static String firstOf(Set<String> ids) {
        if (FuncUtil.isEmpty(ids)) {
            return null;
        }
        return ids instanceof List ? String.valueOf(((List<?>) ids).get(0))
                : ids.stream().sorted().findFirst().orElse(null);
    }

    private static List<String> sortedList(Set<String> ids) {
        if (FuncUtil.isEmpty(ids)) {
            // 空数组让下游 IN () 拼不出非法 SQL：行策略遇空集合会拒绝整个条件，等价于「无归属即无权看」
            return Collections.emptyList();
        }
        return new ArrayList<>(new LinkedHashSet<>(ids)).stream().sorted().collect(Collectors.toList());
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
        if (adminBypass && permitService.isAdmin()) {
            log.debug("[资源权限] resourceType={}, 请求资源数={} → 当前用户是管理员，跳过过滤全部可见", resourceType, resourceIds.size());
            return resourceIds;
        }

        // 批量查询所有相关授权记录（1次SQL）
        List<AcResourcePerm> allPerms = acResourcePermService.getByResourceIds(resourceType, resourceIds);
        log.debug("[资源权限] resourceType={}, 请求资源数={}, 查到授权记录数={}", resourceType, resourceIds.size(), FuncUtil.isEmpty(allPerms) ? 0 : allPerms.size());

        // 无授权记录 → 全部可见
        if (FuncUtil.isEmpty(allPerms)) {
            log.debug("[资源权限] resourceType={} 无任何授权记录 → 全部资源不限制，全部可见", resourceType);
            return resourceIds;
        }

        // 提前一次性解析当前用户在各主体维度上持有的标识（避免循环内重复查SQL）
        Map<ResourceSubjectTypeDict, Set<String>> userSubjects = resolveUserSubjects();
        log.info("[资源权限] 当前用户主体标识规模: {}", subjectSizes(userSubjects));
        // 明细可能很大（ALL 档展开整棵组/部门树），只在 debug 下输出
        log.debug("[资源权限] 当前用户主体标识明细: {}", userSubjects);

        // 按 resourceId 分组
        Map<String, List<AcResourcePerm>> permMap = allPerms.stream()
                .collect(Collectors.groupingBy(AcResourcePerm::getResourceId));

        // 过滤（纯内存操作，无SQL）
        // 日志口径：列级权限会把整个列清单一次传进来，循环内每个资源 id 一条日志，
        // 故「可见」只记 debug，「不可见」保留 info（数量少，且是排查时真正需要的信息）
        List<String> accessibleIds = new ArrayList<>();
        for (String resourceId : resourceIds) {
            List<AcResourcePerm> perms = permMap.get(resourceId);
            // 该资源无授权记录 → 不限制
            if (FuncUtil.isEmpty(perms)) {
                accessibleIds.add(resourceId);
                log.debug("[资源权限] resourceId={} → 可见（该资源无授权记录，不限制）", resourceId);
            } else {
                String reason = matchReason(perms, userSubjects);
                if (reason != null) {
                    accessibleIds.add(resourceId);
                    log.debug("[资源权限] resourceId={} → 可见（{}）", resourceId, reason);
                } else {
                    log.info("[资源权限] resourceId={} → 不可见（有{}条授权记录但当前用户不匹配）", resourceId, perms.size());
                }
            }
        }
        log.info("[资源权限] resourceType={} 过滤结果: 请求{}个 → 可见{}个", resourceType, resourceIds.size(), accessibleIds.size());
        return accessibleIds;
    }

    /**
     * 一次性解析当前用户在全部主体维度上持有的标识集合（每维度最多一次查询，避免循环SQL）
     */
    private Map<ResourceSubjectTypeDict, Set<String>> resolveUserSubjects() {
        PermSubjectResolver.PermSubjectContext context = new PermSubjectResolver.PermSubjectContext(
                AccountContext.getUserId(), AccountContext.getOperator(), AccountContext.getRoleIdList());
        Map<ResourceSubjectTypeDict, Set<String>> userSubjects = new EnumMap<>(ResourceSubjectTypeDict.class);
        for (PermSubjectResolver resolver : resolverBySubjectType.values()) {
            Set<String> ownedIds = resolver.resolveSubjectIds(context);
            userSubjects.put(resolver.subjectType(),
                    FuncUtil.isEmpty(ownedIds) ? Collections.<String>emptySet() : ownedIds);
        }
        return userSubjects;
    }

    /**
     * 各维度持有标识的数量概览，用于 info 日志（明细放在 debug，避开 ALL 档展开时刷屏）
     */
    private Map<String, Integer> subjectSizes(Map<ResourceSubjectTypeDict, Set<String>> userSubjects) {
        Map<String, Integer> sizes = new LinkedHashMap<>(userSubjects.size());
        userSubjects.forEach((type, ids) -> sizes.put(type.getLabel(), ids.size()));
        return sizes;
    }

    /**
     * 判断单个资源是否有权限（内部调用，会查SQL，适用于单条判断场景）
     */
    private boolean matchCurrentUser(List<AcResourcePerm> permList) {
        return matchReason(permList, resolveUserSubjects()) != null;
    }

    /**
     * 纯内存匹配：返回命中的授权原因（用于日志跟踪），未命中返回 null
     * <p>
     * 判定即「被授权标识 ∩ 当前用户持有标识」，逐维度交给对应解析器，本方法不感知具体维度。
     * 未装载解析器的主体类型一律不参与匹配（无法判定用户是否持有标识时不得放行）。
     * </p>
     */
    private String matchReason(List<AcResourcePerm> permList, Map<ResourceSubjectTypeDict, Set<String>> userSubjects) {
        // 按主体类型分组：TreeMap 保证按 type 升序判定，多条授权同时命中时原因输出稳定
        Map<Integer, Set<String>> permSubjectMap = permList.stream()
                .collect(Collectors.groupingBy(
                        AcResourcePerm::getSubjectType,
                        TreeMap::new,
                        Collectors.mapping(AcResourcePerm::getSubjectId, Collectors.toSet())
                ));

        for (Map.Entry<Integer, Set<String>> entry : permSubjectMap.entrySet()) {
            PermSubjectResolver resolver = resolverBySubjectType.get(entry.getKey());
            if (FuncUtil.isEmpty(resolver)) {
                log.warn("[资源权限] subjectType={} 未装载解析器，该维度授权记录不参与匹配", entry.getKey());
                continue;
            }
            Set<String> ownedIds = userSubjects.get(resolver.subjectType());
            if (FuncUtil.isEmpty(ownedIds)) {
                continue;
            }
            // 授权侧集合规模小，遍历它并对用户持有集合做 O(1) contains
            for (String subjectId : entry.getValue()) {
                if (ownedIds.contains(subjectId)) {
                    return resolver.hitLabel(subjectId);
                }
            }
        }

        return null;
    }
}
