package com.bidr.authorization.service.admin;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.bidr.authorization.dao.entity.AcSubsystem;
import com.bidr.authorization.dao.entity.AcTenantApp;
import com.bidr.authorization.dao.repository.AcSubsystemService;
import com.bidr.authorization.dao.repository.AcTenantAppService;
import com.bidr.authorization.vo.tenantapp.BindTenantAppReq;
import com.bidr.authorization.vo.tenantapp.QueryTenantAppReq;
import com.bidr.authorization.vo.tenantapp.QueryTenantAppRes;
import com.bidr.authorization.vo.tenantapp.UnbindTenantAppReq;
import com.bidr.kernel.config.response.Resp;
import com.bidr.kernel.utils.FuncUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Title: AdminTenantAppService
 * Description: 租户应用开通 admin 面服务（方案B 棒1，照 AdminSubsystemService 形态：controller 编排、
 * 本类组装、仓储层守卫）。
 *
 * <p>appName＝两步查（非 SQL join）：分页取开通行后按 app_code 批量取子系统投影——
 * UK(tenant_id, app_code) 语义下一行至多引一 app，两步无放大（弱引用纪律：不建实体关联）。</p>
 *
 * @author Sharp
 * @since 2026-11-07
 */
@Service
@RequiredArgsConstructor
public class AdminTenantAppService {

    private final AcTenantAppService acTenantAppService;
    private final AcSubsystemService acSubsystemService;

    /**
     * 分页列表：按 tenantId 列开通行，行带经 app_code 查出的子系统显示名。
     */
    public Page<QueryTenantAppRes> query(QueryTenantAppReq req) {
        Page<AcTenantApp> page = acTenantAppService.queryTenantApp(req);
        Page<QueryTenantAppRes> res = Resp.convert(page, QueryTenantAppRes.class);
        fillAppName(res.getRecords());
        return res;
    }

    /**
     * 开通：守卫与幂等 upsert 都在仓储 {@link AcTenantAppService#bind} 腿。
     */
    public void bind(BindTenantAppReq req) {
        acTenantAppService.bind(req.getTenantId(), req.getAppCode(), req.getExpireAt());
    }

    /**
     * 取消开通：软删在仓储 {@link AcTenantAppService#unbind} 腿（静默幂等）。
     */
    public void unbind(UnbindTenantAppReq req) {
        acTenantAppService.unbind(req.getTenantId(), req.getAppCode());
    }

    /** 列表行回填 appName：批量取子系统的 (appCode, name) 投影，app 已注销行留 null。 */
    private void fillAppName(List<QueryTenantAppRes> rows) {
        if (FuncUtil.isEmpty(rows)) {
            return;
        }
        Set<String> appCodes = rows.stream()
                .map(QueryTenantAppRes::getAppCode)
                .filter(code -> FuncUtil.isNotEmpty(code))
                .collect(Collectors.toSet());
        if (appCodes.isEmpty()) {
            return;
        }
        LambdaQueryWrapper<AcSubsystem> wrapper = acSubsystemService.getQueryWrapper();
        wrapper.select(AcSubsystem::getAppCode, AcSubsystem::getName);
        wrapper.in(AcSubsystem::getAppCode, appCodes);
        Map<String, String> nameByAppCode = acSubsystemService.select(wrapper).stream()
                .collect(Collectors.toMap(AcSubsystem::getAppCode, AcSubsystem::getName, (a, b) -> a));
        for (QueryTenantAppRes row : rows) {
            row.setAppName(nameByAppCode.get(row.getAppCode()));
        }
    }
}
