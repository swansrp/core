package com.bidr.authorization.annotation.auth;

import com.bidr.authorization.bo.token.TokenInfo;
import com.bidr.authorization.holder.TokenHolder;
import com.bidr.authorization.utils.token.AuthTokenUtil;
import com.bidr.kernel.constant.err.ErrCodeSys;
import com.bidr.kernel.validate.Validator;
import com.diboot.core.util.V;
import org.springframework.stereotype.Component;

import javax.servlet.http.HttpServletRequest;

/**
 * Title: AuthNone
 * Description: Copyright: Copyright (c) 2019 Company: BHFAE
 *
 * @author Sharp
 * @since 2022/6/16 16:25
 */
@Component
public class AuthNone implements AuthRole {
    @Override
    public void validate(HttpServletRequest request, String... args) {
        TokenInfo token = AuthTokenUtil.extractToken(request);
        Validator.assertNotNull(token, ErrCodeSys.PA_DATA_NOT_EXIST, "验证码");
        TokenHolder.set(token);
    }
}
