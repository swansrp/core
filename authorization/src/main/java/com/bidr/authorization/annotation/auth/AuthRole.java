package com.bidr.authorization.annotation.auth;

import org.springframework.web.servlet.ModelAndView;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

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

    /**
     * 结束
     *
     * @param request  请求
     * @param response 返回
     */
    default void completion(HttpServletRequest request, HttpServletResponse response) {
    }


}
