package com.bidr.admin.service.statistic;

import com.bidr.admin.dao.entity.SysPortalDashboard;
import com.bidr.admin.dao.entity.SysPortalDashboardStatistic;
import com.bidr.admin.service.common.BasePortalService;
import com.bidr.admin.vo.statistic.DashboardVO;
import com.bidr.authorization.holder.AccountContext;
import com.bidr.kernel.constant.err.ErrCodeSys;
import com.bidr.kernel.utils.DbUtil;
import com.bidr.kernel.utils.FuncUtil;
import com.bidr.kernel.utils.ReflectionUtil;
import com.bidr.kernel.validate.Validator;
import com.bidr.kernel.vo.common.IdReqVO;
import com.github.yulichang.wrapper.MPJLambdaWrapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Title: AdminPortalIndicatorGroupService
 * Description: Copyright: Copyright (c) 2024 Company: Bidr Ltd.
 *
 * @author Sharp
 * @since 2025/9/2 13:37
 */
@Service
@RequiredArgsConstructor
public class AdminPortalDashboardService extends BasePortalService<SysPortalDashboard, DashboardVO> {

    private final AdminPortalDashboardStatisticService adminPortalDashboardStatisticService;

    @Override
    public void beforeDelete(IdReqVO vo) {
        super.beforeDelete(vo);
    }

    @Override
    public void getJoinWrapper(MPJLambdaWrapper<SysPortalDashboard> wrapper) {
        String operator = AccountContext.getOperator();
        Validator.assertNotNull(operator, ErrCodeSys.PA_PARAM_NULL, "当前登录用户");
        super.getJoinWrapper(wrapper);
        wrapper.leftJoin(SysPortalDashboardStatistic.class, DbUtil.getTableName(SysPortalDashboardStatistic.class),
                on -> on.eq(SysPortalDashboardStatistic::getId, SysPortalDashboard::getStatisticId)
                        .and(o -> o.eq(SysPortalDashboardStatistic::getCustomerNumber,
                                        SysPortalDashboard::getCustomerNumber).or()
                                .isNull(SysPortalDashboardStatistic::getCustomerNumber)));
        wrapper.eq(SysPortalDashboard::getCustomerNumber, operator);
        wrapper.orderByAsc(SysPortalDashboard::getXPosition, SysPortalDashboard::getYPosition);
    }

    public List<DashboardVO> getPersonalDashboard(String tableId) {
        MPJLambdaWrapper<SysPortalDashboard> wrapper = getJoinWrapper();
        wrapper.eq(SysPortalDashboardStatistic::getTableId, tableId);
        return getRepo().selectJoinList(DashboardVO.class, wrapper);
    }

    public void addStatistic(List<DashboardVO> dashboardList, String tableId) {
        if (FuncUtil.isNotEmpty(dashboardList)) {
            List<DashboardVO> personalDashboard = getPersonalDashboard(tableId);
            // 如果已经添加了就不在添加了
            Map<Long, DashboardVO> map = ReflectionUtil.reflectToMap(personalDashboard, DashboardVO::getStatisticId);
            int order = personalDashboard.size() + 1;
            List<SysPortalDashboard> entityList = new ArrayList<>();
            for (DashboardVO dashboardVO : dashboardList) {
                if (!map.containsKey(dashboardVO.getStatisticId())) {
                    SysPortalDashboard sysPortalDashboard = ReflectionUtil.copy(dashboardVO, SysPortalDashboard.class);
                    sysPortalDashboard.setCustomerNumber(AccountContext.getOperator());
                    entityList.add(sysPortalDashboard);
                }
            }
            getRepo().insert(entityList);

        }
    }
}
