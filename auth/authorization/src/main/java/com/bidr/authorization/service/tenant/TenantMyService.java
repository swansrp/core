package com.bidr.authorization.service.tenant;

import com.bidr.authorization.annotation.auth.AuthAdmin;
import com.bidr.authorization.bo.account.AccountInfo;
import com.bidr.authorization.constants.err.AccountErrCode;
import com.bidr.authorization.constants.param.AccountParam;
import com.bidr.authorization.constants.token.TokenItem;
import com.bidr.authorization.dao.entity.AcTenant;
import com.bidr.authorization.dao.entity.AcUserTenant;
import com.bidr.authorization.dao.repository.AcTenantService;
import com.bidr.authorization.dao.repository.AcUserTenantService;
import com.bidr.authorization.holder.AccountContext;
import com.bidr.authorization.holder.TenantContext;
import com.bidr.authorization.service.token.TokenService;
import com.bidr.authorization.vo.login.TenantMyVO;
import com.bidr.kernel.constant.err.ErrCodeSys;
import com.bidr.kernel.utils.FuncUtil;
import com.bidr.kernel.validate.Validator;
import com.bidr.platform.service.cache.SysConfigCacheService;
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
 * 可切集判定（{@link #elevated}）：
 * <ol>
 *   <li><b>平台档</b>＝{@link AuthAdmin#matchesAdminRole}（sys_config {@link AccountParam#ACCOUNT_ADMIN_ROLE_ID}
 *       × 角色比对，R-C M-2 收口谓词；🔴 不读 {@code TenantContext.isPlatform()}——那只由
 *       {@code @Auth(AuthAdmin)} 切面置位，本端点挂 {@code AuthLogin} 档下恒 false，10-20 撤回的正是这条死路）；</li>
 *   <li><b>系统管理级</b>＝登录烤的租户集为空（{@code TenantContext.getTenantIds()} null/空
 *       ＝ D3 既有「无租户集＝系统管理级，可见全量数据」语义的自然延伸——数据面既已旁路，
 *       切换面同样全量）。</li>
 * </ol>
 * 两档命中 ⇒ 清单＝全部有效租户；切换 ⇒ 目标租户存在且 valid 即可，且 🔴 同步把 token 的
 * {@code TENANT_IDS} 改写为目标租户单元素集——数据闸（ac_user 锚点 EXISTS 按 TENANT_IDS）
 * 与业务 TenantLine（按 TENANT_ID）才能一齐跟到目标租户；下次登录 {@code buildTenantIds}
 * 按真实关系重烤，系统级自动落回旁路语义。
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
    private final SysConfigCacheService sysConfigCacheService;
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
     * 判定与改写口径见类注；🔴 成员路径不动 TENANT_IDS（零位移），提权路径两键同改（数据闸跟切）。
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
        if (elevated) {
            tokenService.putItem(TokenItem.TENANT_IDS.name(), Collections.singletonList(tenantId));
        }
        TenantContext.setCurrentTenantId(tenantId);
        log.info("[租户切换]工号: {}, 目标租户: {} ({}), 提权档: {}", userId(), tenant.getName(), tenant.getId(), elevated);
    }

    /**
     * 提权档＝平台档 ∨ 系统管理级（判定口径见类注）。private：测试经构造注入后反射/子类化皆不需要，
     * 判定纯函数 {@link AuthAdmin#matchesAdminRole} 自身已被守卫覆盖。
     */
    private boolean elevated() {
        if (FuncUtil.isEmpty(TenantContext.getTenantIds())) {
            return true;
        }
        AccountInfo account = AccountContext.get();
        return AuthAdmin.matchesAdminRole(account, sysConfigCacheService.getParamLong(AccountParam.ACCOUNT_ADMIN_ROLE_ID));
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
