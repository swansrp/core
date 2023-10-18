package com.bidr.authorization.annotation.auth;

import com.bidr.authorization.bo.account.AccountInfo;
import com.bidr.authorization.bo.role.RoleInfo;
import com.bidr.authorization.constants.err.AccountErrCode;
import com.bidr.authorization.constants.param.AccountParam;
import com.bidr.authorization.holder.AccountContext;
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
        AccountInfo accountInfo = AccountContext.get();
        boolean isAdmin = false;
        for (RoleInfo roleInfo : accountInfo.getRoleInfoMap().values()) {
            if (FuncUtil.equals(roleInfo.getRoleId(), adminRoleId)) {
                isAdmin = true;
                break;
            }
        }
        Validator.assertTrue(isAdmin, AccountErrCode.AC_IS_NOT_ADMIN);
    }
}
