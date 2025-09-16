package com.bidr.admin.service.statistic;

import com.bidr.admin.dao.entity.SysPortalDashboard;
import com.bidr.admin.dao.entity.SysPortalDashboardStatistic;
import com.bidr.admin.service.common.BasePortalService;
import com.bidr.admin.vo.statistic.DashboardStatisticVO;
import com.bidr.admin.vo.statistic.DashboardVO;
import com.bidr.authorization.holder.AccountContext;
import com.bidr.kernel.constant.err.ErrCodeSys;
import com.bidr.kernel.utils.DbUtil;
import com.bidr.kernel.utils.FuncUtil;
import com.bidr.kernel.validate.Validator;
import com.bidr.kernel.vo.common.IdReqVO;
import com.github.yulichang.wrapper.MPJLambdaWrapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

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
        wrapper.orderByAsc(SysPortalDashboard::getOrder);
    }

    public List<DashboardVO> getPersonalDashboard(String tableId) {
        MPJLambdaWrapper<SysPortalDashboard> wrapper = getJoinWrapper();
        wrapper.eq(SysPortalDashboardStatistic::getTableId, tableId);
        List<DashboardVO> res = getRepo().selectJoinList(DashboardVO.class, wrapper);
        if (FuncUtil.isEmpty(res)) {
            List<DashboardStatisticVO> list = adminPortalDashboardStatisticService.getCommonStatistic(tableId);
            if (FuncUtil.isNotEmpty(list)) {
                List<SysPortalDashboard> entityList = new ArrayList<>();
                for (DashboardStatisticVO dashboardStatisticVO : list) {
                    SysPortalDashboard entity = buildSysPortalDashboard(dashboardStatisticVO, entityList.size());
                    entityList.add(entity);
                }
                getRepo().insert(entityList);
            }
            res = getRepo().selectJoinList(DashboardVO.class, wrapper);
        }
        return res;
    }

    private SysPortalDashboard buildSysPortalDashboard(DashboardStatisticVO dashboardStatisticVO, Integer order) {
        SysPortalDashboard sysPortalDashboard = new SysPortalDashboard();
        sysPortalDashboard.setStatisticId(dashboardStatisticVO.getId());
        sysPortalDashboard.setCustomerNumber(AccountContext.getOperator());
        sysPortalDashboard.setOrder(order);
        sysPortalDashboard.setYGrid(1);
        sysPortalDashboard.setXGrid(1);
        return sysPortalDashboard;
    }

    public List<DashboardVO> addCommonStatistic(String[] ids) {
        return null;
    }

    public List<DashboardVO> addPersonalStatistic(String[] ids) {
        return null;
    }
}
