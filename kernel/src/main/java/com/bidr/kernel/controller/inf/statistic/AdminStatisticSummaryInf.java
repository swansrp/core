package com.bidr.kernel.controller.inf.statistic;

import com.bidr.kernel.utils.DbUtil;
import com.bidr.kernel.utils.FuncUtil;
import com.bidr.kernel.vo.portal.Query;
import com.bidr.kernel.vo.portal.statistic.AdvancedSummaryReq;
import com.bidr.kernel.vo.portal.statistic.GeneralSummaryReq;
import com.github.yulichang.wrapper.MPJLambdaWrapper;
import com.github.yulichang.wrapper.segments.SelectString;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Title: AdminStatisticCountInf
 * Description: Copyright: Copyright (c) 2025 Company: Bidr Ltd.
 *
 * @author Sharp
 * @since 2025/4/29 8:43
 */

public interface AdminStatisticSummaryInf<ENTITY, VO> extends AdminStatisticBaseInf<ENTITY, VO> {
    /**
     * 汇总
     *
     * @param req 查询条件
     * @return 汇总数据
     */
    default Map<String, Object> summaryByGeneralReq(GeneralSummaryReq req) {
        Query query = new Query(req);
        defaultQuery(query);
        if (!isAdmin()) {
            beforeQuery(req);
        }
        return summary(query, req.getColumns());
    }

    /**
     * 汇总
     *
     * @param req 高级查询条件
     * @return 汇总数据
     */
    default Map<String, Object> summaryByAdvancedReq(AdvancedSummaryReq req) {
        Query query = new Query(req);
        defaultQuery(query);
        if (!isAdmin()) {
            beforeQuery(req);
        }
        return summary(query, req.getColumns());
    }

    default Map<String, Object> summary(Query query, List<String> summaryColumns) {
        MPJLambdaWrapper<ENTITY> wrapper = new MPJLambdaWrapper<>(getEntityClass());
        boolean defaultHaveHavingFields = hasHavingFields(query.getDefaultQuery(), getPortalService().getHavingFields());
        boolean conditionHaveHavingFields = hasHavingFields(query.getCondition(), getPortalService().getHavingFields());
        if (FuncUtil.isNotEmpty(summaryColumns)) {
            if (defaultHaveHavingFields || conditionHaveHavingFields) {
                for (String column : summaryColumns) {
                    wrapper.getSelectColum().add(new SelectString(String.format("sum(%s) as '%s'", column, column), wrapper.getAlias()));
                }
            } else {
                for (String column : summaryColumns) {
                    DbUtil.addSumSelect(wrapper, column, column);
                }
            }
        } else {
            return new HashMap<>(0);
        }
        wrapper.from(from -> buildSubFromWrapper(query, from));
        return getRepo().selectJoinMap(wrapper);
    }
}
