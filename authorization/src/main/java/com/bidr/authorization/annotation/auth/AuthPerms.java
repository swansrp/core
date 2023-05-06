package com.bidr.authorization.annotation.auth;

import com.bidr.authorization.bo.account.AccountInfo;
import com.bidr.authorization.constants.err.AccountErrCode;
import com.bidr.authorization.holder.AccountContext;
import com.bidr.kernel.validate.Validator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import javax.servlet.http.HttpServletRequest;

/**
 * Title: AuthPerms
 * Description: Copyright: Copyright (c) 2022 Company: Sharp Ltd.
 *
 * @author Sharp
 * @date 2023/04/27 11:30
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AuthPerms implements AuthRole {

    private final AuthLogin authLogin;

    @Override
    public void validate(HttpServletRequest request, String... args) {
        authLogin.validate(request);
        AccountInfo accountInfo = AccountContext.get();
        Validator.assertNotNull(accountInfo.getPermitPermsMap().get(args[0]), AccountErrCode.AC_DONT_HAVE_PERMIT);
    }
}
