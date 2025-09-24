package com.bidr.admin.service.statistic;

import com.bidr.admin.dao.entity.SysPortalDashboard;
import com.bidr.admin.dao.entity.SysPortalDashboardStatistic;
import com.bidr.admin.service.common.BasePortalService;
import com.bidr.admin.vo.statistic.DashboardStatisticVO;
import com.bidr.authorization.dao.entity.AcUser;
import com.bidr.authorization.holder.AccountContext;
import com.bidr.kernel.constant.CommonConst;
import com.bidr.kernel.constant.err.ErrCodeSys;
import com.bidr.kernel.utils.DbUtil;
import com.bidr.kernel.validate.Validator;
import com.github.yulichang.wrapper.MPJLambdaWrapper;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Title: AdminPortalIndicatorGroupService
 * Description: Copyright: Copyright (c) 2024 Company: Bidr Ltd.
 *
 * @author Sharp
 * @since 2025/9/2 13:37
 */
@Service
public class AdminPortalDashboardStatisticService extends BasePortalService<SysPortalDashboardStatistic, DashboardStatisticVO> {

    @Override
    public void beforeAdd(SysPortalDashboardStatistic sysPortalDashboardStatistic) {
        if (sysPortalDashboardStatistic.getCustomerNumber().equals(CommonConst.NO)) {
            sysPortalDashboardStatistic.setCustomerNumber(null);
        } else {
            sysPortalDashboardStatistic.setCustomerNumber(AccountContext.getOperator());
        }
        super.beforeAdd(sysPortalDashboardStatistic);
    }

    @Override
    public void getJoinWrapper(MPJLambdaWrapper<SysPortalDashboardStatistic> wrapper) {
        super.getJoinWrapper(wrapper);
        wrapper.leftJoin(AcUser.class, DbUtil.getTableName(AcUser.class),
                on -> on.eq(AcUser::getValid, CommonConst.YES).eq(AcUser::getStatus, CommonConst.YES)
                        .eq(AcUser::getCustomerNumber, SysPortalDashboard::getCustomerNumber));
        wrapper.orderByAsc(SysPortalDashboardStatistic::getOrder);
    }

    public List<DashboardStatisticVO> getCommonStatistic(String tableId) {
        MPJLambdaWrapper<SysPortalDashboardStatistic> wrapper = getJoinWrapper();
        wrapper.eq(SysPortalDashboardStatistic::getTableId, tableId);
        wrapper.isNull(SysPortalDashboardStatistic::getCustomerNumber);
        return getRepo().selectJoinList(DashboardStatisticVO.class, wrapper);
    }

    public List<DashboardStatisticVO> getPersonalStatistic(String tableId) {
        MPJLambdaWrapper<SysPortalDashboardStatistic> wrapper = getJoinWrapper();
        String operator = AccountContext.getOperator();
        Validator.assertNotNull(operator, ErrCodeSys.PA_PARAM_NULL, "当前登录用户");
        wrapper.eq(SysPortalDashboardStatistic::getCustomerNumber, operator);
        wrapper.eq(SysPortalDashboardStatistic::getTableId, tableId);
        return getRepo().selectJoinList(DashboardStatisticVO.class, wrapper);
    }

    public List<DashboardStatisticVO> getStatisticList(List<Long> ids) {
        MPJLambdaWrapper<SysPortalDashboardStatistic> wrapper = getJoinWrapper();
        String operator = AccountContext.getOperator();
        Validator.assertNotNull(operator, ErrCodeSys.PA_PARAM_NULL, "当前登录用户");
        wrapper.in(SysPortalDashboardStatistic::getId, ids);
        return getRepo().selectJoinList(DashboardStatisticVO.class, wrapper);
    }
}
