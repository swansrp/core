package com.bidr.kernel.controller.inf.statistic;

import com.bidr.kernel.controller.inf.base.AdminBaseInf;
import com.bidr.kernel.controller.inf.base.AdminBaseQueryControllerInf;
import com.bidr.kernel.utils.FuncUtil;
import com.bidr.kernel.vo.portal.Query;
import com.github.yulichang.wrapper.MPJLambdaWrapper;

import java.util.LinkedHashMap;
import java.util.Map;

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
        boolean defaultHaveHavingFields = hasHavingFields(query.getDefaultQuery(), getPortalService().getHavingFields());
        boolean conditionHaveHavingFields = hasHavingFields(query.getCondition(), getPortalService().getHavingFields());
        return buildSubFromWrapper(query, from, defaultHaveHavingFields, conditionHaveHavingFields);
    }

    default MPJLambdaWrapper<ENTITY> buildSubFromWrapper(Query query, MPJLambdaWrapper<ENTITY> from,
                                                         boolean defaultHaveHavingFields, boolean conditionHaveHavingFields) {
        if (FuncUtil.isNotEmpty(getPortalService())) {
            Map<String, String> selectAliasMap = parseSelectApply(query.getSelectColumnCondition(), getPortalService().getAliasMap(),
                    getPortalService().getSelectApplyMap(), from);
            if (defaultHaveHavingFields || conditionHaveHavingFields) {
                Map<String, String> newAliasMap = new LinkedHashMap<>();
                buildSubWrapperWithoutHavingField(from, query, getPortalService().getHavingFields(), selectAliasMap, defaultHaveHavingFields,
                        conditionHaveHavingFields, newAliasMap);
                getRepo().parseQuery(query, newAliasMap, null, from);
            } else {
                getPortalService().getJoinWrapper(from);
                getRepo().parseQuery(query, selectAliasMap, getPortalService().getHavingFields(), from);
            }
        }
        return from;
    }
}
