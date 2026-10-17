package com.bidr.admin.service.statistic;

import com.bidr.admin.dao.entity.SysPortalDashboard;
import com.bidr.admin.dao.entity.SysPortalDashboardStatistic;
import com.bidr.admin.service.common.BasePortalService;
import com.bidr.admin.vo.statistic.DashboardStatisticVO;
import com.bidr.authorization.dao.entity.AcUser;
import com.bidr.authorization.holder.AccountContext;
import com.bidr.authorization.service.permit.ResourcePermFilterService;
import com.bidr.kernel.constant.CommonConst;
import com.bidr.kernel.constant.err.ErrCodeSys;
import com.bidr.kernel.utils.DbUtil;
import com.bidr.kernel.utils.FuncUtil;
import com.bidr.kernel.validate.Validator;
import com.github.yulichang.wrapper.MPJLambdaWrapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Title: AdminPortalIndicatorGroupService
 * Description: Copyright: Copyright (c) 2024 Company: Bidr Ltd.
 *
 * @author Sharp
 * @since 2025/9/2 13:37
 */
@Service
@RequiredArgsConstructor
public class AdminPortalDashboardStatisticService extends BasePortalService<SysPortalDashboardStatistic, DashboardStatisticVO> {

    private static final String RESOURCE_TYPE = "sys_portal_dashboard_statistic";

    private final ResourcePermFilterService resourcePermFilterService;

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
    public void beforeUpdate(SysPortalDashboardStatistic sysPortalDashboardStatistic) {
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
        List<DashboardStatisticVO> list = getRepo().selectJoinList(DashboardStatisticVO.class, wrapper);
        return filterByPermission(list);
    }

    /**
     * 根据通用资源权限过滤指标列表
     * 无授权记录的指标 → 所有人可见
     * 有授权记录的指标 → 仅授权用户可见
     */
    private List<DashboardStatisticVO> filterByPermission(List<DashboardStatisticVO> list) {
        if (FuncUtil.isEmpty(list)) {
            return list;
        }
        List<String> allIds = list.stream()
                .map(v -> String.valueOf(v.getId()))
                .collect(Collectors.toList());
        List<String> accessibleIds = resourcePermFilterService.filterAccessibleIds(RESOURCE_TYPE, allIds);
        return list.stream()
                .filter(v -> accessibleIds.contains(String.valueOf(v.getId())))
                .collect(Collectors.toList());
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
