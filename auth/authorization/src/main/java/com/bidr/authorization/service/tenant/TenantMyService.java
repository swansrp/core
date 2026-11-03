package com.bidr.authorization.service.tenant;

import com.bidr.authorization.bo.account.AccountInfo;
import com.bidr.authorization.constants.err.AccountErrCode;
import com.bidr.authorization.constants.token.TokenItem;
import com.bidr.authorization.dao.entity.AcTenant;
import com.bidr.authorization.dao.entity.AcUserTenant;
import com.bidr.authorization.dao.repository.AcTenantService;
import com.bidr.authorization.dao.repository.AcUserTenantService;
import com.bidr.authorization.holder.AccountContext;
import com.bidr.authorization.holder.TenantContext;
import com.bidr.authorization.service.permit.PermitService;
import com.bidr.authorization.service.token.TokenService;
import com.bidr.authorization.vo.login.TenantMyVO;
import com.bidr.kernel.constant.err.ErrCodeSys;
import com.bidr.kernel.utils.FuncUtil;
import com.bidr.kernel.validate.Validator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Title: TenantMyService
 * Description: 框架内置「我的租户」清单与切换（2026-10-21 用户拍板：多租户系统内置顶栏齿轮切换面，
 * 系统管理员可切所有租户）。
 * <p>
 * 可切集判定（{@link #elevated}）——🔴 <b>两个条件同时成立</b>（2026-11-01 用户裁定，原 ∨ 改 &&）：
 * <ol>
 *   <li><b>系统管理级</b>＝登录烤的租户集为空（{@code TenantContext.getTenantIds()} null/空）；</li>
 *   <li><b>平台管理员</b>＝{@link PermitService#isAdmin()}（2026-11-01 统一后全平台唯一 isAdmin 实现；
 *       🔴 不读 {@code TenantContext.isPlatform()}——那只由
 *       {@code @Auth(AuthAdmin)} 切面置位，本端点挂 {@code AuthLogin} 档下恒 false，10-20 撤回的正是这条死路）。</li>
 * </ol>
 * ⇒ 只有"既没绑租户、又是超管"的平台账号（种子 bidrAdmin 形态）可代言任意租户；
 * JIT 新号（无绑定但非超管）与绑了租户的超管一律不可。
 * <p>命中 ⇒ 清单＝全部有效租户；切换 ⇒ 目标租户存在且 valid 即可，只改写 token 的 TENANT_ID。
 * 🔴 提权档<b>不改写 TENANT_IDS</b>（2026-11-02）：平台档自 2026-11-01 起是每请求置位的账号属性
 * （{@code AuthLogin.buildContext} 按 ACCOUNT_ADMIN_ROLE_ID 判），数据闸与行级隔离都按平台档旁路，
 * TENANT_IDS 不再承担"跟切"职责；写了反而推翻 {@link #elevated()} 的"登录烤集为空"条件，
 * 使切换一次后清单变空、齿轮消失（切换资格自毁）。
 * </p>
 * <p>
 * 普通成员（有租户集且非平台档）：清单＝成员关系 × 有效租户目录对齐（停用租户的关系行不展示，
 * 与 epc prog 同口径）；切换闸＝成员关系现查（{@code getRelation}，M-11）＋租户 valid，
 * 🔴 只改 TENANT_ID 不动 TENANT_IDS——成员的租户集语义是"可见面"，切换只是"当前在哪格"，
 * 与 epc prog 版逐字节同行为。
 * </p>
 * Copyright: Copyright (c) 2026 Company: Bidr Ltd.
 *
 * @author Sharp
 * @since 2026-10-21
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TenantMyService {

    private final AcTenantService acTenantService;
    private final AcUserTenantService acUserTenantService;
    private final PermitService permitService;
    private final TokenService tokenService;

    /**
     * 我的租户清单（现查，不消费登录烤键；M-11 时效纪律——成员/管理员/租户启停变更即时可见）。
     */
    public List<TenantMyVO> listMy() {
        List<AcTenant> validTenants = acTenantService.listValid();
        Long currentTenantId = TenantContext.currentTenantId();
        if (elevated()) {
            Map<Long, AcUserTenant> relations = userId() == null ? Collections.emptyMap()
                    : acUserTenantService.listByUserId(userId()).stream()
                            .collect(Collectors.toMap(AcUserTenant::getTenantId, Function.identity(), (a, b) -> a));
            List<TenantMyVO> rows = new ArrayList<>(validTenants.size());
            for (AcTenant tenant : validTenants) {
                rows.add(assemble(tenant, relations.get(tenant.getId()), currentTenantId));
            }
            return rows;
        }
        // 成员模式：关系行 × 有效租户目录对齐；指向已停用租户的关系行不展示
        Map<Long, AcTenant> tenantById = validTenants.stream()
                .collect(Collectors.toMap(AcTenant::getId, Function.identity(), (a, b) -> a));
        List<TenantMyVO> rows = new ArrayList<>();
        for (AcUserTenant relation : acUserTenantService.listByUserId(userId())) {
            AcTenant tenant = tenantById.get(relation.getTenantId());
            if (tenant == null) {
                continue;
            }
            rows.add(assemble(tenant, relation, currentTenantId));
        }
        return rows;
    }

    /**
     * 切换当前租户（改写 token TENANT_ID 键，免重登录；下一请求经 AuthLogin 重建线程态）。
     * 判定与改写口径见类注；🔴 两条路径都<b>只动 TENANT_ID</b>、不动 TENANT_IDS——
     * 成员档的租户集是"可见面"，提权档的租户集是"提权判据"（写窄即自毁切换资格）。
     */
    public void switchTenant(Long tenantId) {
        Validator.assertNotNull(tenantId, ErrCodeSys.PA_PARAM_FORMAT, "tenantId");
        AcTenant tenant = acTenantService.getById(tenantId);
        AcTenantPortalService.assertValidTenant(tenant);
        boolean elevated = elevated();
        if (!elevated) {
            AcUserTenant relation = acUserTenantService.getRelation(userId(), tenantId);
            Validator.assertNotNull(relation, AccountErrCode.AC_TENANT_NOT_MEMBER);
        }
        tokenService.putItem(TokenItem.TENANT_ID.name(), tenantId);
        TenantContext.setCurrentTenantId(tenantId);
        log.info("[租户切换]工号: {}, 目标租户: {} ({}), 提权档: {}", userId(), tenant.getName(), tenant.getId(), elevated);
    }

    /**
     * 提权档＝「系统管理级（登录烤的租户集为空）」<b>且</b>「平台管理员角色」——2026-11-01 用户裁定：
     * 两个条件必须同时成立（原为 ∨，用户评「OR 不够严肃」）。
     * <p>为什么必须 &&：单靠"没绑租户"不够格——JIT 新建的 SSO 账号默认无绑定，
     * 若按 ∨ 判定它一登录就获得代言任意租户的能力（fail-open 特权）；反之绑了租户的超管也不该再代言。
     * 只有"既没绑、又是超管"＝真正的平台账号（种子 bidrAdmin 形态）才放行。</p>
     */
    private boolean elevated() {
        return FuncUtil.isEmpty(TenantContext.getTenantIds()) && permitService.isAdmin();
    }

    private Long userId() {
        AccountInfo account = AccountContext.get();
        return account == null ? null : account.getUserId();
    }

    private static TenantMyVO assemble(AcTenant tenant, AcUserTenant relation, Long currentTenantId) {
        TenantMyVO vo = new TenantMyVO();
        vo.setTenantId(tenant.getId());
        vo.setName(tenant.getName());
        vo.setCode(tenant.getCode());
        vo.setIsAdmin(relation != null && Boolean.TRUE.equals(relation.getIsAdmin()));
        vo.setCurrent(tenant.getId().equals(currentTenantId));
        return vo;
    }
}
