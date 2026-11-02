package com.bidr.authorization.annotation.auth;

import com.bidr.authorization.authz.AuthzChecker;
import com.bidr.authorization.authz.PlatformRoles;
import com.bidr.authorization.bo.account.AccountInfo;
import com.bidr.authorization.constants.err.AccountErrCode;
import com.bidr.authorization.holder.TenantContext;
import com.bidr.kernel.validate.Validator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
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

    /**
     * admin 门槛判定 SPI（T2.3c 上提改 ObjectProvider 可选注入）：server 形态装配
     * {@code LocalAuthzChecker}（委托 {@code PermitService.isAdmin()}，行为与上提前同一实现）；
     * 远端形态留棒4（RemoteAuthzChecker 走 /authz 端点）。SPI 缺失时 fail-closed 拒绝并
     * log（宁可全拒不可漏判——admin 端点没有"跳过判定放行"的合法形态）。
     */
    private final ObjectProvider<AuthzChecker> authzChecker;
    private final AuthLogin authLogin;

    @Override
    public void validate(HttpServletRequest request, String... args) {
        authLogin.validate(request, args);
        // 平台档判定收口（2026-11-01 统一）——上提后经 api AuthzChecker SPI：
        // server 宿主＝PermitService.isAdmin 同一实现（AccountContext→token ROLE_MAP 回落，
        // × sys_config ACCOUNT_ADMIN_ROLE_ID），此前 AuthAdmin 与 PermitService 各写一份的
        // 空上下文行为不一致问题维持已收口形态（见 PermitService 类注）。
        AuthzChecker checker = authzChecker.getIfAvailable();
        if (checker == null) {
            log.error("[AuthAdmin]AuthzChecker SPI 未装配（server 宿主应由 LocalAuthzChecker 提供）——fail-closed 拒绝");
            throw Validator.<RuntimeException>assertException(AccountErrCode.AC_IS_NOT_ADMIN);
        }
        Validator.assertTrue(checker.isAdmin(), AccountErrCode.AC_IS_NOT_ADMIN);
        // 平台档置位（棒2 加法，不动上方既有校验语义）：只有管理员断言通过才可达，
        // 非管理员抛错时绝不会标记；闸侧据此放行全量视图（TenantDataGate 判序③）。
        TenantContext.markPlatform();
    }

    /**
     * 兼容委托：平台档判定已统一到 {@link PlatformRoles#containsAdminRole}（唯一比对实现，
     * T2.3c 随类型面上提至 api）。保留本静态签名供既有调用方/守卫复用，实现不再自带第二份逻辑。
     */
    @Deprecated
    public static boolean matchesAdminRole(AccountInfo accountInfo, Long adminRoleId) {
        return PlatformRoles.containsAdminRole(
                accountInfo == null ? null : accountInfo.getRoleInfoMap(), adminRoleId);
    }
}
