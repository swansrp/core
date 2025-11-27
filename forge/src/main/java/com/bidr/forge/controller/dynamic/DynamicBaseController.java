package com.bidr.forge.controller.dynamic;


import com.bidr.admin.dao.entity.SysPortal;
import com.bidr.admin.dao.repository.SysPortalService;
import com.bidr.admin.holder.PortalConfigContext;
import com.bidr.forge.engine.PortalDataMode;
import com.bidr.forge.engine.driver.DatasetDriver;
import com.bidr.forge.engine.driver.MatrixDriver;
import com.bidr.forge.engine.driver.PortalDriver;
import com.bidr.kernel.constant.err.ErrCodeSys;
import com.bidr.kernel.exception.NoticeException;
import com.bidr.kernel.utils.FuncUtil;
import com.bidr.kernel.validate.Validator;
import com.bidr.kernel.vo.common.KeyValueResVO;
import com.bidr.kernel.vo.portal.AdvancedQuery;
import com.bidr.kernel.vo.portal.AdvancedQueryReq;
import com.bidr.kernel.vo.portal.ConditionVO;
import com.bidr.kernel.vo.portal.QueryConditionReq;
import com.bidr.kernel.vo.portal.statistic.*;

import javax.annotation.Resource;
import java.util.List;
import java.util.Map;

/**
 * @author Sharp
 * @since 2025/11/27 13:35
 */

public class DynamicBaseController {

    @Resource
    protected MatrixDriver matrixDriver;

    @Resource
    protected DatasetDriver datasetDriver;

    @Resource
    protected SysPortalService sysPortalService;
    /**
     * 根据portalName获取对应的驱动
     */
    protected PortalDriver<Map<String, Object>> getDriver(String portalName) {
        SysPortal portal = sysPortalService.getByName(portalName, getRoleId());
        Validator.assertNotNull(portal, ErrCodeSys.SYS_ERR_MSG, "Portal配置不存在: " + portalName);

        String dataMode = portal.getDataMode();
        Validator.assertNotBlank(dataMode, ErrCodeSys.PA_DATA_NOT_SUPPORT, "Portal未配置数据模式");

        if (PortalDataMode.MATRIX.name().equals(dataMode)) {
            return matrixDriver;
        } else if (PortalDataMode.DATASET.name().equals(dataMode)) {
            return datasetDriver;
        }
        throw new NoticeException(ErrCodeSys.PA_DATA_NOT_SUPPORT, "不支持的数据模式: " + dataMode);
    }

    protected Long getRoleId() {
        return PortalConfigContext.getPortalConfigRoleId();
    }

    /**
     * 将通用查询请求转换为高级查询请求
     */
    protected AdvancedQueryReq convertToAdvancedReq(QueryConditionReq req) {
        AdvancedQueryReq advReq = new AdvancedQueryReq();
        advReq.setCurrentPage(req.getCurrentPage());
        advReq.setPageSize(req.getPageSize());
        advReq.setSelectColumnCondition(req.getSelectColumnCondition());
        advReq.setSortList(req.getSortList());

        // 转换查询条件
        if (FuncUtil.isNotEmpty(req.getConditionList())) {
            AdvancedQuery condition = new AdvancedQuery();
            condition.setAndOr(AdvancedQuery.AND);
            for (ConditionVO conditionVO : req.getConditionList()) {
                condition.addCondition(conditionVO);
            }
            advReq.setCondition(condition);
        }
        return advReq;
    }

    /**
     * 将通用查询请求转换为高级查询请求
     */
    protected AdvancedSummaryReq convertToAdvancedReq(GeneralSummaryReq req) {
        AdvancedSummaryReq advReq = new AdvancedSummaryReq();
        advReq.setColumns(req.getColumns());
        advReq.setSelectColumnCondition(req.getSelectColumnCondition());
        advReq.setSortList(req.getSortList());

        // 转换查询条件
        if (FuncUtil.isNotEmpty(req.getConditionList())) {
            AdvancedQuery condition = new AdvancedQuery();
            condition.setAndOr(AdvancedQuery.AND);
            for (ConditionVO conditionVO : req.getConditionList()) {
                condition.addCondition(conditionVO);
            }
            advReq.setCondition(condition);
        }
        return advReq;
    }

    /**
     * 将通用查询请求转换为高级查询请求
     */
    protected AdvancedStatisticReq convertToAdvancedReq(GeneralStatisticReq req) {
        AdvancedStatisticReq advReq = new AdvancedStatisticReq();

        advReq.setMetricColumn(req.getMetricColumn());
        advReq.setMetricCondition(req.getMetricCondition());
        advReq.setMajorCondition(req.getMajorCondition());
        advReq.setStatisticColumn(req.getStatisticColumn());
        advReq.setSort(req.getSort());

        advReq.setSelectColumnCondition(req.getSelectColumnCondition());
        advReq.setSortList(req.getSortList());

        // 转换查询条件
        if (FuncUtil.isNotEmpty(req.getConditionList())) {
            AdvancedQuery condition = new AdvancedQuery();
            condition.setAndOr(AdvancedQuery.AND);
            for (ConditionVO conditionVO : req.getConditionList()) {
                condition.addCondition(conditionVO);
            }
            advReq.setCondition(condition);
        }
        return advReq;
    }

}
