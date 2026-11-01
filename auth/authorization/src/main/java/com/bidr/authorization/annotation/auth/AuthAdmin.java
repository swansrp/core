package com.bidr.authorization.annotation.auth;

import com.bidr.authorization.bo.account.AccountInfo;
import com.bidr.authorization.constants.err.AccountErrCode;
import com.bidr.authorization.holder.TenantContext;
import com.bidr.authorization.service.permit.PermitService;
import com.bidr.kernel.validate.Validator;
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

    private final PermitService permitService;
    private final AuthLogin authLogin;

    @Override
    public void validate(HttpServletRequest request, String... args) {
        authLogin.validate(request, args);
        // 平台档判定收口到 PermitService.isAdmin（2026-11-01 统一）——全平台唯一实现；
        // 此前 AuthAdmin 与 PermitService 各写一份，空上下文时行为不一致（见 PermitService 类注）。
        Validator.assertTrue(permitService.isAdmin(), AccountErrCode.AC_IS_NOT_ADMIN);
        // 平台档置位（棒2 加法，不动上方既有校验语义）：只有管理员断言通过才可达，
        // 非管理员抛错时绝不会标记；闸侧据此放行全量视图（TenantDataGate 判序③）。
        TenantContext.markPlatform();
    }

    /**
     * 兼容委托：平台档判定已统一到 {@link PermitService#containsAdminRole}（唯一比对实现）。
     * 保留本静态签名供既有调用方/守卫复用，实现不再自带第二份逻辑。
     */
    @Deprecated
    public static boolean matchesAdminRole(AccountInfo accountInfo, Long adminRoleId) {
        return PermitService.containsAdminRole(
                accountInfo == null ? null : accountInfo.getRoleInfoMap(), adminRoleId);
    }
}
