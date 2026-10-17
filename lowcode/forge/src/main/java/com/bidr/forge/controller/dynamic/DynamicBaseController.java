package com.bidr.forge.controller.dynamic;


import com.bidr.admin.controller.inf.PortalSqlInf;
import com.bidr.admin.dao.entity.SysPortal;
import com.bidr.admin.dao.repository.SysPortalService;
import com.bidr.admin.holder.PortalConfigContext;
import com.bidr.admin.service.perm.PortalColumnPolicyService;
import com.bidr.forge.engine.PortalDataMode;
import com.bidr.forge.engine.builder.SqlBuilder;
import com.bidr.forge.engine.driver.DatasetDriver;
import com.bidr.forge.engine.driver.MatrixDriver;
import com.bidr.forge.engine.driver.PortalDriver;
import com.bidr.forge.service.perm.PortalAccessGuard;
import com.bidr.forge.service.perm.PortalRowPolicyService;
import com.bidr.kernel.constant.err.ErrCodeSys;
import com.bidr.kernel.exception.NoticeException;
import com.bidr.kernel.utils.FuncUtil;
import com.bidr.kernel.validate.Validator;
import com.bidr.kernel.vo.portal.AdvancedQuery;
import com.bidr.kernel.vo.portal.AdvancedQueryReq;
import com.bidr.kernel.vo.portal.ConditionVO;
import com.bidr.kernel.vo.portal.QueryConditionReq;
import com.bidr.kernel.vo.portal.statistic.AdvancedPivotReq;
import com.bidr.kernel.vo.portal.statistic.AdvancedStatisticReq;
import com.bidr.kernel.vo.portal.statistic.AdvancedSummaryReq;
import com.bidr.kernel.vo.portal.statistic.GeneralStatisticReq;
import com.bidr.kernel.vo.portal.statistic.GeneralSummaryReq;

import javax.annotation.Resource;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * @author Sharp
 * @since 2025/11/27 13:35
 */

public class DynamicBaseController implements PortalSqlInf {

    @Resource
    protected MatrixDriver matrixDriver;

    @Resource
    protected DatasetDriver datasetDriver;

    @Resource
    protected SysPortalService sysPortalService;

    @Resource
    protected PortalAccessGuard portalAccessGuard;

    @Resource
    protected PortalRowPolicyService portalRowPolicyService;

    @Resource
    protected PortalColumnPolicyService portalColumnPolicyService;

    /**
     * 将行级权限条件并入本次查询请求
     * <p>所有以 {@link AdvancedQueryReq#getCondition()} 为 WHERE 源的读接口（分页/列表/计数/汇总/统计/透视）
     * 均经此收口：把当前用户命中的行策略以 AND 并入其查询条件，与用户自带条件叠加而非替换。
     * 无命中策略（含未配权限、非低代码 Portal）时 buildRowPolicy 返回 null，直接不改写，零行为变更。</p>
     *
     * @param portalName Portal 逻辑名（行策略的资源标识）
     * @param req        已归一为高级查询的请求对象（含其子类 Summary/Statistic/Pivot）
     */
    protected void applyRowPolicy(String portalName, AdvancedQueryReq req) {
        if (FuncUtil.isEmpty(req)) {
            return;
        }
        AdvancedQuery policy = portalRowPolicyService.buildRowPolicy(portalName);
        if (policy == null) {
            return;
        }
        req.setCondition(andMerge(req.getCondition(), policy));
    }

    /**
     * 以 AND 组合已有条件与行策略：任一方为空则取另一方，两者皆非空则并入一个 AND 根节点
     */
    private AdvancedQuery andMerge(AdvancedQuery existing, AdvancedQuery policy) {
        if (FuncUtil.isEmpty(existing)) {
            return policy;
        }
        AdvancedQuery root = new AdvancedQuery();
        root.setAndOr(AdvancedQuery.AND);
        root.getConditionList().add(existing);
        root.getConditionList().add(policy);
        return root;
    }

    /**
     * 将列级权限（黑名单）并入本次透视查询：在聚合前从请求中剔掉当前用户无权的列，无权列不参与拼 SQL/聚合
     * <p>
     * token 来两类资源：原表列（{@code sys_portal_column} 的 {@code c:field}）过滤行维度 groupColumns；
     * 透视列（{@code sys_portal_pivot} 的 {@code p:itemValue}/{@code m:field}）过滤 pivotColumns/measures。
     * 隐藏集为空（未配权限/非登录上下文）时不改写，零行为变更。
     * </p>
     *
     * @param portalName Portal 逻辑名
     * @param req        透视请求
     */
    protected void applyPivotColumnPolicy(String portalName, AdvancedPivotReq req) {
        if (FuncUtil.isEmpty(req)) {
            return;
        }
        Set<String> hiddenC = portalColumnPolicyService.resolveHiddenTokens(
                PortalAccessGuard.RESOURCE_TYPE_PORTAL_COLUMN, portalName);
        Set<String> hiddenPM = portalColumnPolicyService.resolveHiddenTokens(
                PortalAccessGuard.RESOURCE_TYPE_PORTAL_PIVOT, portalName);
        if (FuncUtil.isEmpty(hiddenC) && FuncUtil.isEmpty(hiddenPM)) {
            return;
        }
        if (FuncUtil.isNotEmpty(req.getGroupColumns())) {
            List<com.bidr.kernel.vo.common.KeyValueResVO> kept = req.getGroupColumns().stream()
                    .filter(g -> !hiddenC.contains("c:" + g.getValue()))
                    .collect(Collectors.toList());
            req.setGroupColumns(kept);
        }
        if (FuncUtil.isNotEmpty(req.getPivotColumns())) {
            req.setPivotColumns(req.getPivotColumns().stream()
                    .filter(pv -> !hiddenPM.contains("p:" + pv.getValue()))
                    .collect(Collectors.toList()));
        }
        if (FuncUtil.isNotEmpty(req.getMeasures())) {
            req.setMeasures(req.getMeasures().stream()
                    .filter(m -> !hiddenPM.contains("m:" + m.getField()))
                    .collect(Collectors.toList()));
        }
        // fail-closed：行维度与度量均被剔空时，不能给下游一个空聚合去兜底成不限制
        if (FuncUtil.isEmpty(req.getGroupColumns()) && FuncUtil.isEmpty(req.getMeasures())) {
            throw new NoticeException(ErrCodeSys.SYS_PERMIT_ERROR, "无该数据配置的列查看权限：" + portalName);
        }
    }

    /**
     * 对透视结果逐行剪掉命中隐藏 token 的键（防被构造的 req 绕过，与 {@link #applyPivotColumnPolicy} 双保险）
     * <p>
     * 列键形态：行维度=字段名（{@code c:field}）；度量子列={@code ${itemValue}__${field}}（{@code p:}/{@code m:}）。
     * </p>
     */
    protected List<Map<String, Object>> stripPivotHiddenColumns(String portalName, List<Map<String, Object>> rows) {
        if (FuncUtil.isEmpty(rows)) {
            return rows;
        }
        Set<String> hiddenC = portalColumnPolicyService.resolveHiddenTokens(
                PortalAccessGuard.RESOURCE_TYPE_PORTAL_COLUMN, portalName);
        Set<String> hiddenPM = portalColumnPolicyService.resolveHiddenTokens(
                PortalAccessGuard.RESOURCE_TYPE_PORTAL_PIVOT, portalName);
        if (FuncUtil.isEmpty(hiddenC) && FuncUtil.isEmpty(hiddenPM)) {
            return rows;
        }
        for (Map<String, Object> row : rows) {
            row.keySet().removeIf(key -> {
                int idx = key.indexOf("__");
                if (idx < 0) {
                    return hiddenC.contains("c:" + key);
                }
                String itemValue = key.substring(0, idx);
                String field = key.substring(idx + 2);
                return hiddenPM.contains("p:" + itemValue)
                        || hiddenPM.contains("m:" + field)
                        || hiddenC.contains("c:" + field);
            });
        }
        return rows;
    }

    /**
     * 根据portalName获取对应的驱动
     * <p>所有动态Portal端点（查询/统计/透视/增删改）都经此路由，故整体粒度的数据授权在此统一拦截</p>
     */
    protected PortalDriver<Map<String, Object>> getDriver(String portalName) {
        SysPortal portal = sysPortalService.getByNameOrDefault(portalName, getRoleId());
        Validator.assertNotNull(portal, ErrCodeSys.SYS_ERR_MSG, "Portal配置不存在: " + portalName);

        String dataMode = portal.getDataMode();
        Validator.assertNotBlank(dataMode, ErrCodeSys.PA_DATA_NOT_SUPPORT, "Portal未配置数据模式");

        portalAccessGuard.assertPortalAccessible(portalName, portal);

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
     * 获取指定Portal的基础查询SQL（不含条件与分页）
     * <p>动态Portal多个配置共享本 Bean，需按portalName路由到对应驱动（Dataset/Matrix），
     * 由驱动的SqlBuilder基于已保存的表/列配置拼装SQL</p>
     */
    @Override
    public String getPortalSql(String portalName) {
        PortalDriver<Map<String, Object>> driver = getDriver(portalName);
        Map<String, String> aliasMap = driver.buildAliasMap(portalName, getRoleId());
        SqlBuilder builder = driver.getSqlBuilder(portalName, getRoleId());
        return builder.buildSelect(new AdvancedQueryReq(), aliasMap, new LinkedHashMap<>());
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
