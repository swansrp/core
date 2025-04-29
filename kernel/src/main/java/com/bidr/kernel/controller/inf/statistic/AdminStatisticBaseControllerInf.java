package com.bidr.kernel.controller.inf.statistic;

import com.bidr.kernel.controller.inf.base.AdminBaseControllerInf;
import com.bidr.kernel.controller.inf.base.AdminBaseQueryControllerInf;
import com.bidr.kernel.utils.FuncUtil;
import com.bidr.kernel.vo.portal.AdvancedQueryReq;
import com.bidr.kernel.vo.portal.QueryConditionReq;
import com.github.yulichang.wrapper.MPJLambdaWrapper;

import java.util.Map;

/**
 * Title: AdminStatisticBaseControllerInf
 * Description: Copyright: Copyright (c) 2025 Company: Bidr Ltd.
 *
 * @author Sharp
 * @since 2025/4/29 8:42
 */

public interface AdminStatisticBaseControllerInf<ENTITY, VO> extends AdminBaseControllerInf<ENTITY, VO>, AdminBaseQueryControllerInf<ENTITY, VO> {
    default MPJLambdaWrapper<ENTITY> buildGeneralFromWapper(QueryConditionReq req, MPJLambdaWrapper<ENTITY> from) {
        if (FuncUtil.isNotEmpty(getPortalService())) {
            getPortalService().getJoinWrapper(from);
            Map<String, String> aliasMap = getRepo().parseSelectApply(req.getConditionList(),
                    getPortalService().getAliasMap(), getPortalService().getSelectApplyMap(), from);
            if (FuncUtil.isNotEmpty(req.getConditionList())) {
                getRepo().parseGeneralQuery(req.getConditionList(), aliasMap, getPortalService().getHavingFields(),
                        from);
            } else {
                getRepo().parseGeneralQuery(req.getConditionList(), null, null, from);
            }
        }
        return from;
    }

    default MPJLambdaWrapper<ENTITY> buildAdvancedFromWapper(AdvancedQueryReq req, MPJLambdaWrapper<ENTITY> from) {
        if (FuncUtil.isNotEmpty(getPortalService())) {
            getPortalService().getJoinWrapper(from);
            Map<String, String> aliasMap = getRepo().parseSelectApply(req.getSelectApplyList(),
                    getPortalService().getAliasMap(), getPortalService().getSelectApplyMap(), from);
            if (FuncUtil.isNotEmpty(req.getCondition())) {
                getRepo().parseAdvancedQuery(req.getCondition(), aliasMap, from);
            } else {
                getRepo().parseAdvancedQuery(req.getCondition(), null, from);
            }
        }
        return from;
    }


}
