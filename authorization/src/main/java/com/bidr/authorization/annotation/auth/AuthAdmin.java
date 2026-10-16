package com.bidr.authorization.annotation.auth;

import com.bidr.authorization.bo.account.AccountInfo;
import com.bidr.authorization.bo.role.RoleInfo;
import com.bidr.authorization.constants.err.AccountErrCode;
import com.bidr.authorization.constants.param.AccountParam;
import com.bidr.authorization.holder.AccountContext;
import com.bidr.authorization.holder.TenantContext;
import com.bidr.kernel.utils.FuncUtil;
import com.bidr.kernel.validate.Validator;
import com.bidr.platform.service.cache.SysConfigCacheService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import javax.servlet.http.HttpServletRequest;

/**
 * Title: AuthAdmin
 * Description: Copyright: Copyright (c) 2019 Company: BHFAE
 *
 * @author Sharp
 * @since 2022/6/15 13:22
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AuthAdmin implements AuthRole {

    private final SysConfigCacheService sysConfigCacheService;
    private final AuthLogin authLogin;

    @Override
    public void validate(HttpServletRequest request, String... args) {
        authLogin.validate(request, args);
        Long adminRoleId = sysConfigCacheService.getParamLong(AccountParam.ACCOUNT_ADMIN_ROLE_ID);
        // 平台档判据收口单一静态谓词（R-C M-2）：AcTenantPortalService.isPlatformTier 消费同一函数，
        // 两处判据不许各写一份；缺上下文/空角色映射按"非平台档"处理（拒绝而非 NPE）
        Validator.assertTrue(matchesAdminRole(AccountContext.get(), adminRoleId), AccountErrCode.AC_IS_NOT_ADMIN);
        // 平台档置位（棒2 加法，不动上方既有校验语义）：只有管理员断言通过才可达，
        // 非管理员抛错时绝不会标记；闸侧据此放行全量视图（TenantDataGate 判序③）。
        TenantContext.markPlatform();
    }

    /**
     * 平台档唯一判定谓词：账号角色映射中命中 adminRoleId 即为平台档。
     * null 安全（无上下文/无角色 ⇒ false），public static 供 service 侧与守卫复用。
     */
    public static boolean matchesAdminRole(AccountInfo accountInfo, Long adminRoleId) {
        if (FuncUtil.isEmpty(accountInfo) || FuncUtil.isEmpty(accountInfo.getRoleInfoMap())) {
            return false;
        }
        for (RoleInfo roleInfo : accountInfo.getRoleInfoMap().values()) {
            if (FuncUtil.equals(roleInfo.getRoleId(), adminRoleId)) {
                return true;
            }
        }
        return false;
    }
}
