package com.bidr.kernel.controller.inf.statistic;

import com.bidr.kernel.vo.portal.AdvancedQueryReq;
import com.bidr.kernel.vo.portal.QueryConditionReq;
import com.github.yulichang.wrapper.MPJLambdaWrapper;
import com.github.yulichang.wrapper.segments.SelectString;

/**
 * Title: AdminStatisticCountInf
 * Description: Copyright: Copyright (c) 2025 Company: Bidr Ltd.
 *
 * @author Sharp
 * @since 2025/4/29 8:43
 */

public interface AdminStatisticCountInf<ENTITY, VO> extends AdminStatisticBaseInf<ENTITY, VO> {
    /**
     * 统计个数
     *
     * @param req 查询条件
     * @return 统计个数数据
     */
    default Long countByGeneralReq(QueryConditionReq req) {
        defaultQuery(req);
        if (!isAdmin()) {
            beforeQuery(req);
        }
        MPJLambdaWrapper<ENTITY> wrapper = new MPJLambdaWrapper<>(getEntityClass());
        wrapper.getSelectColum().add(new SelectString("count(1)", wrapper.getAlias()));
        wrapper.from(from -> buildGeneralFromWrapper(req, from));
        return getRepo().selectJoinCount(wrapper);
    }

    /**
     * 统计个数
     *
     * @param req 高级查询条件
     * @return 统计个数数据
     */
    default Long countByAdvancedReq(AdvancedQueryReq req) {
        defaultQuery(req);
        if (!isAdmin()) {
            beforeQuery(req);
        }
        MPJLambdaWrapper<ENTITY> wrapper = new MPJLambdaWrapper<>(getEntityClass());
        wrapper.getSelectColum().add(new SelectString("count(1)", wrapper.getAlias()));
        wrapper.from(from -> buildAdvancedFromWrapper(req, from));
        return getRepo().selectJoinCount(wrapper);
    }
}
