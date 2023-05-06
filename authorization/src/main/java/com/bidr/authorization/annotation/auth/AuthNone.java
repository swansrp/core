package com.bidr.authorization.annotation.auth;

import org.springframework.stereotype.Component;

import javax.servlet.http.HttpServletRequest;

/**
 * Title: AuthNone
 * Description: Copyright: Copyright (c) 2019 Company: BHFAE
 *
 * @author Sharp
 * @date 2022/6/16 16:25
 */
@Component
public class AuthNone implements AuthRole {
    @Override
    public void validate(HttpServletRequest request, String... args) {

    }
}
