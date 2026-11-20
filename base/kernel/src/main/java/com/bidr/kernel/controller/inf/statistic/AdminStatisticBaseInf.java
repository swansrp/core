package com.bidr.kernel.controller.inf.statistic;

import com.bidr.kernel.controller.inf.base.AdminBaseInf;
import com.bidr.kernel.controller.inf.base.AdminBaseQueryControllerInf;
import com.bidr.kernel.utils.FuncUtil;
import com.bidr.kernel.vo.portal.Query;
import com.github.yulichang.wrapper.MPJLambdaWrapper;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/**
 * Title: AdminStatisticBaseInf
 * Description: Copyright: Copyright (c) 2025 Company: Bidr Ltd.
 *
 * @author Sharp
 * @since 2025/4/29 8:42
 */

public interface AdminStatisticBaseInf<ENTITY, VO> extends AdminBaseInf<ENTITY, VO>, AdminBaseQueryControllerInf<ENTITY, VO> {
    /**
     * 查询子表构建方法
     *
     * @param query 请求
     * @param from  查询wrapper
     * @return 带有查询子表的wrapper
     */
    default MPJLambdaWrapper<ENTITY> buildSubFromWrapper(Query query, MPJLambdaWrapper<ENTITY> from) {
        Set<String> havingFields = FuncUtil.isNotEmpty(getPortalService()) ? getPortalService().getHavingFields() : null;
        boolean defaultHaveHavingFields = hasHavingFields(query.getDefaultQuery(), havingFields);
        boolean conditionHaveHavingFields = hasHavingFields(query.getCondition(), havingFields);
        return buildSubFromWrapper(query, from, defaultHaveHavingFields, conditionHaveHavingFields);
    }

    default MPJLambdaWrapper<ENTITY> buildSubFromWrapper(Query query, MPJLambdaWrapper<ENTITY> from,
                                                         boolean defaultHaveHavingFields, boolean conditionHaveHavingFields) {
        if (FuncUtil.isEmpty(getPortalService())) {
            // 未挂 PortalCommonService 的通用 admin 控制器(如 AdminConfigController):
            // 没有 having/join/动态列扩展, 但仍要把查询条件构建进子查询;
            // 静默返回会丢掉 count/summary/statistic/pivot 的全部过滤条件。
            Map<String, String> selectAliasMap = parseSelectApply(query.getSelectColumnCondition(), null, null, from);
            getRepo().parseQuery(query, selectAliasMap, null, from);
            return from;
        }
        Map<String, String> selectAliasMap = parseSelectApply(query.getSelectColumnCondition(), getPortalService().getAliasMap(),
                getPortalService().getSelectApplyMap(), from);
        if (defaultHaveHavingFields || conditionHaveHavingFields) {
            Map<String, String> newAliasMap = new LinkedHashMap<>();
            buildSubWrapperWithoutHavingField(from, query, getPortalService().getHavingFields(), selectAliasMap, defaultHaveHavingFields,
                    conditionHaveHavingFields, newAliasMap);
            getRepo().parseQuery(query, newAliasMap, null, from);
        } else {
            getPortalService().getJoinWrapper(from);
            from = getPortalService().getDynamicJoinWrapper(from, query.getSelectColumnCondition());
            getRepo().parseQuery(query, selectAliasMap, getPortalService().getHavingFields(), from);
        }
        return from;
    }
}
