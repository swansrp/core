package com.bidr.authorization.service.tenant;

import com.bidr.authorization.bo.role.RoleInfo;
import com.bidr.authorization.constants.param.AccountParam;
import com.bidr.authorization.dao.entity.AcTenant;
import com.bidr.authorization.dao.entity.AcUserTenant;
import com.bidr.authorization.dao.repository.AcTenantService;
import com.bidr.authorization.dao.repository.AcUserTenantService;
import com.bidr.authorization.service.permit.PermitService;
import com.bidr.authorization.tenant.TenantResolver;
import com.bidr.kernel.constant.err.ErrCodeSys;
import com.bidr.kernel.exception.ServiceException;
import com.bidr.kernel.utils.FuncUtil;
import com.bidr.kernel.validate.Validator;
import com.bidr.platform.service.cache.SysConfigCacheService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Title: TenantBindOnLoginService
 * Description: 登录补绑钩子（T1.1 棒3）——存量无绑定老号登录时补籍一次的平台档豁免版。
 * <p>调用点唯一：{@code LoginServiceImpl#fillToken} 内、{@code buildTenantIds} 烤 token 之前
 * （fillToken 是全部登录链——密码/SSO/企微/短信 loginOrReg/refreshLogin/ghost/扫码——的
 * 公共装配点，一处挂接全链生效，补绑结果随即被同请求的 buildTenantIds 现查烤进 token）。</p>
 *
 * <p>裁定（全部满足才补绑：零绑定 ∧ 非平台档 ∧ 租户模式开）：</p>
 * <ul>
 *   <li>租户模式关（{@code my.tenant.line-enabled} 默认 false）⇒ 钩子整体惰性，不绑不拒；</li>
 *   <li>已有绑定 ⇒ 跳过（补绑只做一次，不重复）；</li>
 *   <li>平台档（超管）⇒ 不绑不拒——种子超管零绑定旁路"要看谁就代言谁"定稿；
 *       🔴 判据用登录时点将烤入 token 的角色集（DB 侧现查）×
 *       {@link PermitService#containsAdminRole}（唯一比对纯函数），<b>不能调
 *       {@code PermitService.isAdmin()}</b>：isAdmin 走 AccountContext/token ROLE_MAP，
 *       登录时点新 token 尚未装配、上下文未建，判出来恒 false，超管首登会被误绑上租户、
 *       破坏零绑定旁路定稿；</li>
 *   <li>resolver 空 ⇒ 抛 {@link ServiceException} 拒登（fail-closed，信息带 username）；
 *       resolver 命中但 ac_tenant 无有效行（配置漂移）⇒ 同拒——放行即零绑定旁路形态。</li>
 * </ul>
 * Copyright: Copyright (c) 2026 Company: Bidr Ltd.
 *
 * @author Sharp
 * @since 2026-11-01
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TenantBindOnLoginService {

    private final TenantResolver tenantResolver;
    private final AcTenantService acTenantService;
    private final AcUserTenantService acUserTenantService;
    private final SysConfigCacheService sysConfigCacheService;

    /**
     * 租户行级隔离开关（M-2，与 TenantLineIsolationConfiguration 同键）：
     * false＝非租户交付形态，登录链零感知（本钩子不绑不拒）。
     */
    @Value("${my.tenant.line-enabled:false}")
    private boolean tenantLineEnabled;

    /** 测试注入用（离线单测不起 Spring）；包可见不构成对外入口，风格同 LoginServiceImpl#setRegisterModeYml。 */
    void setTenantLineEnabled(boolean tenantLineEnabled) {
        this.tenantLineEnabled = tenantLineEnabled;
    }

    /**
     * 登录补绑/拒登钩子。幂等（{@link AcUserTenantService#insertIfAbsent}），is_admin 恒 false。
     *
     * @param userId   登录用户 id
     * @param userName 登录名（resolver 解析键，也是拒登文案定位线索）
     * @param roleMap  登录时点将烤入 token 的角色映射（DB 侧现查，见类注超管判据裁定）
     */
    public void bindOnLogin(Long userId, String userName, Map<Long, RoleInfo> roleMap) {
        if (!tenantLineEnabled) {
            return;
        }
        List<AcUserTenant> existing = acUserTenantService.listByUserId(userId);
        if (FuncUtil.isNotEmpty(existing)) {
            return;
        }
        if (containsAdminRole(roleMap)) {
            // 平台档零绑定＝系统管理级旁路（TenantMyService.elevated 的 && 判据前腿），不得绑死
            log.info("[tenant] 平台档账号[{}]零绑定登录，保持旁路不补绑", userName);
            return;
        }
        Optional<String> code = tenantResolver.resolveTenantCode(userName);
        if (!code.isPresent()) {
            Validator.assertException(ErrCodeSys.SYS_ERR_MSG, "租户模式已开启，账号[" + userName
                    + "]无租户归属且无法自动补绑，登录被拒绝，请联系管理员处理");
        }
        AcTenant tenant = acTenantService.getByCode(code.get());
        if (tenant == null) {
            Validator.assertException(ErrCodeSys.SYS_ERR_MSG, "租户编码[" + code.get()
                    + "]在系统无有效租户（配置漂移），账号[" + userName + "]登录被拒绝，请联系管理员处理");
        }
        boolean bound = acUserTenantService.insertIfAbsent(userId, tenant.getId(), Boolean.FALSE);
        if (bound) {
            log.info("[tenant] 存量账号[{}]登录补绑租户 {}({})", userName, tenant.getName(), tenant.getId());
        }
    }

    /**
     * 平台档比对：与 {@link PermitService#isAdmin()} 同参数源（sys_config ACCOUNT_ADMIN_ROLE_ID），
     * 唯一差异是角色集来自调用方传入的登录时点 DB 现查集而非 token（登录时点 token 不存在）。
     */
    private boolean containsAdminRole(Map<Long, RoleInfo> roleMap) {
        Long adminRoleId = sysConfigCacheService.getParamLong(AccountParam.ACCOUNT_ADMIN_ROLE_ID);
        return PermitService.containsAdminRole(roleMap, adminRoleId);
    }
}
