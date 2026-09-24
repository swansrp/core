package com.bidr.forge.service.perm;

import com.bidr.authorization.constants.dict.ResourceSubjectTypeDict;
import com.bidr.authorization.dao.entity.AcResourcePerm;
import com.bidr.authorization.holder.AccountContext;
import com.bidr.authorization.service.permit.ResourcePermFilterService;
import com.bidr.kernel.utils.ConditionVariableUtil;
import com.bidr.kernel.utils.FuncUtil;
import com.bidr.kernel.utils.JsonUtil;
import com.bidr.kernel.vo.portal.AdvancedQuery;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Title: PortalRowPolicyService
 * Description: 低代码 Portal 的行级权限策略装配
 * <p>
 * 行条件挂在 {@code ac_resource_perm}（{@code resourceType=sys_portal}，{@code resourceId=} Portal 逻辑名）
 * 命中当前用户的授权行的 {@code extra_data} 上，结构 {@code {"condition":{...}}} 与
 * {@code sys_portal.default_condition} 完全同构，故前端复用同一套高级搜索弹窗产出。
 * </p>
 * <p>
 * 多条命中策略（同一用户经不同主体维度命中多条授权行）按 <b>OR 并集</b> 合并：
 * 「角色A允许看X类 + 部门B允许看Y类」应看到 X∪Y，取交集会表现为莫名少数据且难归因。
 * 每个策略内部的条件仍按各自 andOr 原样保留。
 * </p>
 * <p>
 * 策略条件的字段名与被列权限隐藏的列的交互：本服务只负责把条件拼进 req.getCondition()，
 * 条件字段最终仍走 {@link ColumnAliasMap#resolve} —— 即策略只能引用当前用户可见的列。
 * 引用被隐藏列属配置矛盾（既要按它过滤又不让它可见），会按普通越权字段被拒绝。
 * </p>
 *
 * @author Sharp
 * @since 2026-09-23
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PortalRowPolicyService {

    /**
     * extra_data 顶层结构：仅取 condition 一节，其余扩展位忽略
     */
    @Data
    @NoArgsConstructor
    private static class RowPolicyExtra {
        private AdvancedQuery condition;
    }

    private final ResourcePermFilterService resourcePermFilterService;

    /**
     * 装配当前用户对该 Portal 的行级过滤条件
     *
     * @param portalName Portal 逻辑名（资源标识）
     * @return 合并后的条件树；无任何行策略时返回 null（表示不加限制）
     */
    public AdvancedQuery buildRowPolicy(String portalName) {
        if (FuncUtil.isEmpty(portalName)) {
            return null;
        }
        List<AcResourcePerm> matchedRows =
                resourcePermFilterService.getMatchedRows(PortalAccessGuard.RESOURCE_TYPE_PORTAL, portalName);
        if (FuncUtil.isEmpty(matchedRows)) {
            return null;
        }
        // 变量上下文整体解析一次，逐条件复用（避免每个值都去查一遍主体标识）
        Map<ResourceSubjectTypeDict, Set<String>> userSubjects = resourcePermFilterService.currentUserSubjects();
        String operator = AccountContext.getOperator();

        List<AdvancedQuery> policyGroups = new ArrayList<>();
        for (AcResourcePerm row : matchedRows) {
            AdvancedQuery policy = parseCondition(row.getExtraData());
            if (policy == null) {
                continue;
            }
            resolveVariables(policy, userSubjects, operator);
            policyGroups.add(policy);
        }
        if (policyGroups.isEmpty()) {
            return null;
        }
        if (policyGroups.size() == 1) {
            return policyGroups.get(0);
        }
        AdvancedQuery or = new AdvancedQuery();
        or.setAndOr(AdvancedQuery.OR);
        or.getConditionList().addAll(policyGroups);
        return or;
    }

    /**
     * 解析 extra_data 为条件树；空/非法/无条件均返回 null（视为该行不带行限制）
     */
    private AdvancedQuery parseCondition(String extraData) {
        if (FuncUtil.isEmpty(extraData)) {
            return null;
        }
        try {
            RowPolicyExtra extra = JsonUtil.readJson(extraData, RowPolicyExtra.class);
            if (FuncUtil.isEmpty(extra) || isEmptyCondition(extra.getCondition())) {
                return null;
            }
            return extra.getCondition();
        } catch (Exception e) {
            // 配置脏数据不能连坐整页查询：记警告后按“无行限制”处理
            log.warn("[行权限] extra_data 解析失败，跳过该行策略：{}", extraData, e);
            return null;
        }
    }

    /**
     * 条件树是否为空（既无叶子字段也无子组）
     */
    private boolean isEmptyCondition(AdvancedQuery query) {
        return FuncUtil.isEmpty(query)
                || (FuncUtil.isEmpty(query.getProperty()) && FuncUtil.isEmpty(query.getConditionList()));
    }

    /**
     * 递归解析条件值中的动态变量（时间变量 + 主体变量）
     * <p>
     * 主体变量（如 ${currentGroupIds}）解析为 JSON 数组串，遇到会展开成多个值并入条件值列表，
     * 供 IN 关系使用；单个时间变量解析为标量，原位替换。
     * </p>
     */
    private void resolveVariables(AdvancedQuery query,
                                  Map<ResourceSubjectTypeDict, Set<String>> userSubjects,
                                  String operator) {
        if (FuncUtil.isEmpty(query)) {
            return;
        }
        if (FuncUtil.isNotEmpty(query.getValue())) {
            query.setValue(expandValues(query.getValue(), userSubjects, operator));
        }
        if (FuncUtil.isNotEmpty(query.getConditionList())) {
            for (AdvancedQuery sub : query.getConditionList()) {
                resolveVariables(sub, userSubjects, operator);
            }
        }
    }

    /**
     * 逐值解析：命中标量变量原地替换，命中数组变量展开为多值，普通值原样保留
     */
    private List<Object> expandValues(List<?> values,
                                      Map<ResourceSubjectTypeDict, Set<String>> userSubjects,
                                      String operator) {
        List<Object> resolved = new ArrayList<>(values.size());
        for (Object value : values) {
            if (!(value instanceof String)) {
                resolved.add(value);
                continue;
            }
            String token = (String) value;
            String single = resolveToken(token, userSubjects, operator);
            if (single == null) {
                // 非变量或无法解析：原样保留
                resolved.add(value);
                continue;
            }
            List<String> asArray = tryParseJsonArray(single);
            if (asArray != null) {
                resolved.addAll(asArray);
            } else {
                resolved.add(single);
            }
        }
        return resolved;
    }

    /**
     * 解析单个值：先主体变量（authorization 侧），再时间变量（kernel 侧）；均不命中返回 null
     */
    private String resolveToken(String value,
                                Map<ResourceSubjectTypeDict, Set<String>> userSubjects,
                                String operator) {
        if (!ConditionVariableUtil.isVariableForm(value)) {
            return null;
        }
        String token = value.substring(2, value.length() - 1);
        String subjectValue = ResourcePermFilterService.resolveSubjectToken(token, userSubjects, operator);
        if (subjectValue != null) {
            return subjectValue;
        }
        String timeValue = ConditionVariableUtil.resolve(value);
        return timeValue.equals(value) ? null : timeValue;
    }

    /**
     * 值若为 JSON 数组串则解析为列表（主体集合变量产物），否则返回 null
     */
    private List<String> tryParseJsonArray(String value) {
        if (value == null || value.length() < 2 || value.charAt(0) != '[') {
            return null;
        }
        try {
            List<String> list = JsonUtil.readJson(value, List.class, String.class);
            return list;
        } catch (Exception e) {
            return null;
        }
    }
}
