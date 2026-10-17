package com.bidr.authorization.annotation.auth;

import com.bidr.authorization.bo.token.TokenInfo;
import com.bidr.authorization.holder.TokenHolder;
import com.bidr.authorization.utils.token.AuthTokenUtil;
import com.bidr.kernel.constant.err.ErrCodeSys;
import com.bidr.kernel.validate.Validator;
import org.springframework.stereotype.Component;

import javax.servlet.http.HttpServletRequest;

/**
 * Title: AuthToken
 * Description: Copyright: Copyright (c) 2023
 *
 * @author Sharp
 * @since 2023/10/11 11:36
 */
@Component
public class AuthToken implements AuthRole {
    @Override
    public void validate(HttpServletRequest request, String... args) {
        TokenInfo token = AuthTokenUtil.extractToken(request);
        Validator.assertNotNull(token, ErrCodeSys.PA_DATA_NOT_EXIST, "安全令牌");
        TokenHolder.set(token);
    }
}
