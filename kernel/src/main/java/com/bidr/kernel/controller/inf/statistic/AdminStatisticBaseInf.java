package com.bidr.kernel.controller.inf.statistic;

import com.bidr.kernel.controller.inf.base.AdminBaseInf;
import com.bidr.kernel.controller.inf.base.AdminBaseQueryControllerInf;
import com.bidr.kernel.utils.FuncUtil;
import com.bidr.kernel.vo.portal.AdvancedQueryReq;
import com.bidr.kernel.vo.portal.QueryConditionReq;
import com.github.yulichang.wrapper.MPJLambdaWrapper;

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
     * @param req  请求
     * @param from 查询wrapper
     * @return 带有查询子表的wrapper
     */
    default MPJLambdaWrapper<ENTITY> buildGeneralFromWrapper(QueryConditionReq req, MPJLambdaWrapper<ENTITY> from) {
        if (FuncUtil.isNotEmpty(getPortalService())) {
            getPortalService().getJoinWrapper(from);
            if (FuncUtil.isNotEmpty(req.getConditionList())) {
                Map<String, String> aliasMap = getRepo().parseSelectApply(req.getSelectColumnCondition(),
                        getPortalService().getAliasMap(), getPortalService().getSelectApplyMap(), from);
                getRepo().parseGeneralQuery(req.getConditionList(), aliasMap, getPortalService().getHavingFields(),
                        from);
            }
        }
        return from;
    }

    /**
     * 查询子表构建方法
     *
     * @param req  请求
     * @param from 查询wrapper
     * @return 带有查询子表的wrapper
     */
    default MPJLambdaWrapper<ENTITY> buildAdvancedFromWrapper(AdvancedQueryReq req, MPJLambdaWrapper<ENTITY> from) {
        if (FuncUtil.isNotEmpty(getPortalService())) {
            getPortalService().getJoinWrapper(from);
            if (FuncUtil.isNotEmpty(req.getCondition())) {
                Map<String, String> aliasMap = getRepo().parseSelectApply(req.getSelectColumnCondition(),
                        getPortalService().getAliasMap(), getPortalService().getSelectApplyMap(), from);
                getRepo().parseAdvancedQuery(req.getCondition(), aliasMap, from);
            }
        }
        return from;
    }


}
