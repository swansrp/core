package com.bidr.authorization.dao.repository;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.bidr.authorization.dao.entity.AcTenant;
import com.bidr.authorization.dao.entity.AcTenantApp;
import com.bidr.authorization.dao.mapper.AcTenantAppMapper;
import com.bidr.authorization.vo.tenantapp.QueryTenantAppReq;
import com.bidr.kernel.constant.CommonConst;
import com.bidr.kernel.constant.err.ErrCode;
import com.bidr.kernel.mybatis.repository.BaseSqlRepo;
import com.bidr.kernel.utils.FuncUtil;
import com.bidr.kernel.validate.Validator;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;

import static com.bidr.authorization.constants.err.AccountErrCode.AC_TENANT_NOT_AVAILABLE;

/**
 * Title: AcTenantAppService
 * Description: 租户级应用开通表仓储（方案B 棒1）。逻辑主键＝UK(tenant_id, app_code)，写面三件：
 * bind（守卫＋幂等 upsert，撞 UK 复活软删行而非新插）、unbind（软删置 valid='0'，静默幂等）、
 * {@link #activatedFor}（开通判定谓词，单一真相——业务仓门户/open-api 复用同一方法，不得两处各写一份）。
 * <p>tenant_id/app_code 均为弱引用无 FK（M-6/M-8）：引用完整性在 {@link #validateBindable} 兜底
 * （app_code∈ac_subsystem、tenant_id∈ac_tenant，照 {@link AcSubsystemService#validatePartnerBound} 模式）。</p>
 * <p>🔴 写点不做 AuthzVersionHolder.bump（M-7：不进 /authz 判定面，消费侧实时查）。</p>
 *
 * @author Sharp
 * @since 2026-11-07
 */
@Service
@RequiredArgsConstructor
public class AcTenantAppService extends BaseSqlRepo<AcTenantAppMapper, AcTenantApp> {

    /** status 列字面量：已开通（开通判定谓词的组成条件之一） */
    public static final String STATUS_ENABLED = "ENABLED";

    /** status 列字面量：已停用 */
    public static final String STATUS_DISABLED = "DISABLED";

    private final AcSubsystemService acSubsystemService;
    private final AcTenantService acTenantService;

    /**
     * 绑定守卫：app_code 必须注册于 ac_subsystem、tenant_id 必须<b>存在且有效</b>（valid='1'——
     * 停用租户不可 bind，2026-11-08 用户拍板关闭 review M-1 挂账；1039 人话「不存在或已停用」
     * 一码覆盖两态，离线层同走 existed=false）
     * （弱引用无 FK，引用完整性在仓储层兜；app 先查，未注册即拒、不触租户查询）。
     */
    public void validateBindable(Long tenantId, String appCode) {
        Validator.assertTrue(acSubsystemService.existedById(appCode), AcTenantAppErr.AC_APP_NOT_EXISTED);
        LambdaQueryWrapper<AcTenant> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(AcTenant::getId, tenantId).eq(AcTenant::getValid, CommonConst.YES);
        Validator.assertTrue(acTenantService.existed(wrapper), AC_TENANT_NOT_AVAILABLE);
    }

    /**
     * 开通（幂等 upsert）：守卫过 ⇒ 同 (tenant_id, app_code) 行——含 valid='0' 软删行，
     * UK 保证物理唯一——复活/更新该行（status=ENABLED、expireAt、valid='1'，expire_at 显式重写：
     * null 覆盖旧到期＝改长期）；无 ⇒ 新插（start_at 空＝立即生效）。
     *
     * @param expireAt 到期时间（null=长期有效）
     */
    public void bind(Long tenantId, String appCode, Date expireAt) {
        validateBindable(tenantId, appCode);
        AcTenantApp existing = getByTenantApp(tenantId, appCode);
        if (existing == null) {
            AcTenantApp entity = new AcTenantApp();
            entity.setTenantId(tenantId);
            entity.setAppCode(appCode);
            entity.setStatus(STATUS_ENABLED);
            entity.setExpireAt(expireAt);
            entity.setValid(CommonConst.YES);
            insert(entity);
            return;
        }
        LambdaUpdateWrapper<AcTenantApp> wrapper = super.getUpdateWrapper();
        wrapper.eq(AcTenantApp::getId, existing.getId());
        wrapper.set(AcTenantApp::getStatus, STATUS_ENABLED);
        wrapper.set(AcTenantApp::getExpireAt, expireAt);
        wrapper.set(AcTenantApp::getValid, CommonConst.YES);
        update(wrapper);
    }

    /**
     * 取消开通（软删）：按 (tenantId, appCode) 置 valid='0'；行不存在 ⇒ 0 行受影响，静默幂等不报错。
     */
    public void unbind(Long tenantId, String appCode) {
        LambdaUpdateWrapper<AcTenantApp> wrapper = super.getUpdateWrapper();
        wrapper.eq(AcTenantApp::getTenantId, tenantId);
        wrapper.eq(AcTenantApp::getAppCode, appCode);
        wrapper.set(AcTenantApp::getValid, CommonConst.NO);
        update(wrapper);
    }

    /**
     * 🔴 开通判定谓词（单一真相，业务仓复用本方法、不得另写判定）：
     * status=ENABLED 且 valid='1' 且（expire_at IS NULL OR expire_at&gt;now）
     * 且（start_at IS NULL OR start_at&lt;=now）。
     * <p>tenantId/appCode 空 ⇒ false（null 安全，不触库）。</p>
     *
     * @param now 判定时点（调用方给时钟，便于测试与未来缓存）
     * @return 该租户该应用当前是否开通
     */
    public boolean activatedFor(Long tenantId, String appCode, Date now) {
        if (tenantId == null || FuncUtil.isEmpty(appCode)) {
            return false;
        }
        return existed(activatedWrapper(tenantId, appCode, now));
    }

    /**
     * 谓词判据的共享构造（判据只此一处）。包内可见＝给同包离线测试直接断言 wrapper 形态
     * （同 {@link AcSubsystemService#partnerIdWrapper} 纪律），不为测试拓宽公开 API。
     */
    LambdaQueryWrapper<AcTenantApp> activatedWrapper(Long tenantId, String appCode, Date now) {
        LambdaQueryWrapper<AcTenantApp> wrapper = super.getQueryWrapper();
        wrapper.eq(AcTenantApp::getTenantId, tenantId);
        wrapper.eq(AcTenantApp::getAppCode, appCode);
        activatedConds(wrapper, now);
        return wrapper;
    }

    /**
     * 开通判定四条件的私有共享段（status/valid 等值＋到期/生效两时间窗）——
     * {@link #activatedWrapper} 与 {@link #listActivatedTenants}/{@link #listActivatedAppCodes}
     * 批量面同源，时间窗判据仍只此一处。
     */
    private void activatedConds(LambdaQueryWrapper<AcTenantApp> wrapper, Date now) {
        wrapper.eq(AcTenantApp::getStatus, STATUS_ENABLED);
        wrapper.eq(AcTenantApp::getValid, CommonConst.YES);
        wrapper.and(w -> w.isNull(AcTenantApp::getExpireAt).or().gt(AcTenantApp::getExpireAt, now));
        wrapper.and(w -> w.isNull(AcTenantApp::getStartAt).or().le(AcTenantApp::getStartAt, now));
    }

    /**
     * 按应用列当前开通租户集（批量面，方案B 棒2 契约增量）：谓词与 {@link #activatedFor} 同源
     * （同走 {@link #activatedConds} 四条件），业务仓 open-api 可见集解析一次取回、
     * 免逐租户 N 次谓词查询。appCode 空 ⇒ 空集（null 安全，不触库）。
     *
     * @param now 判定时点（调用方给时钟）
     * @return 开通租户 id 集合（去重，库序）
     */
    public List<Long> listActivatedTenants(String appCode, Date now) {
        if (FuncUtil.isEmpty(appCode)) {
            return Collections.emptyList();
        }
        LambdaQueryWrapper<AcTenantApp> wrapper = super.getQueryWrapper();
        wrapper.eq(AcTenantApp::getAppCode, appCode);
        activatedConds(wrapper, now);
        List<Long> tenantIds = new ArrayList<>();
        for (AcTenantApp row : select(wrapper)) {
            if (row.getTenantId() != null && !tenantIds.contains(row.getTenantId())) {
                tenantIds.add(row.getTenantId());
            }
        }
        return tenantIds;
    }

    /**
     * 按租户列当前开通应用集（批量面，方案B 棒2 契约增量）：谓词与 {@link #activatedFor} 同源，
     * 业务仓门户 visible() 一次取回本租户开通面、免逐应用 N 次谓词查询。tenantId 空 ⇒ 空集
     * （null 安全，不触库）。
     *
     * @param now 判定时点（调用方给时钟）
     * @return 开通应用编码集合（去重，库序）
     */
    public List<String> listActivatedAppCodes(Long tenantId, Date now) {
        if (tenantId == null) {
            return Collections.emptyList();
        }
        LambdaQueryWrapper<AcTenantApp> wrapper = super.getQueryWrapper();
        wrapper.eq(AcTenantApp::getTenantId, tenantId);
        activatedConds(wrapper, now);
        List<String> appCodes = new ArrayList<>();
        for (AcTenantApp row : select(wrapper)) {
            if (FuncUtil.isNotEmpty(row.getAppCode()) && !appCodes.contains(row.getAppCode())) {
                appCodes.add(row.getAppCode());
            }
        }
        return appCodes;
    }

    /**
     * 按逻辑主键取行（UK 保证至多一行；<b>不过滤 valid</b>——bind 的撞键复活判定需要看见软删行）。
     *
     * @return 命中行（含软删行）；无 ⇒ null
     */
    public AcTenantApp getByTenantApp(Long tenantId, String appCode) {
        LambdaQueryWrapper<AcTenantApp> wrapper = super.getQueryWrapper();
        wrapper.eq(AcTenantApp::getTenantId, tenantId);
        wrapper.eq(AcTenantApp::getAppCode, appCode);
        return selectOne(wrapper);
    }

    /**
     * 管理面分页查询：tenantId 等值（条件空则不下发）。
     */
    public Page<AcTenantApp> queryTenantApp(QueryTenantAppReq req) {
        LambdaQueryWrapper<AcTenantApp> wrapper = super.getQueryWrapper();
        wrapper.eq(req.getTenantId() != null, AcTenantApp::getTenantId, req.getTenantId());
        return select(wrapper, req);
    }

    /**
     * 绑定守卫错误码（本棒局部注册）：{@code AccountErrCode} 属 authorization-api 模块、
     * 不在本棒点名文件内，故收在仓储类内嵌枚举；编号取 1042（紧接注册表现有最大 1041），
     * 后续若扩注册表可整段平移。租户侧沿用既有 {@code AC_TENANT_NOT_AVAILABLE}。
     */
    @Getter
    @AllArgsConstructor
    enum AcTenantAppErr implements ErrCode {
        /** app_code 未注册于 ac_subsystem */
        AC_APP_NOT_EXISTED(1042, "该应用未注册，不能开通");

        private final Integer errCode;
        private final String errMsg;
    }
}
