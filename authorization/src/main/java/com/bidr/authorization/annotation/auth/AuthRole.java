package com.bidr.authorization.annotation.auth;

import org.springframework.stereotype.Component;

import javax.servlet.http.HttpServletRequest;

/**
 * Title: AuthRole
 * Description: Copyright: Copyright (c) 2019 Company: BHFAE
 *
 * @author Sharp
 * @since 2021/8/17 18:56
 */
public interface AuthRole {
    /**
     * 鉴权
     *
     * @param request 请求
     */
    void validate(HttpServletRequest request, String... args);


}
