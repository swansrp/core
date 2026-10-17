package com.bidr.authorization.service.login;

import com.bidr.authorization.vo.login.LoginRes;

/**
 * Title: LoginFillTokenInf
 * Description: Copyright: Copyright (c) 2023
 *
 * @author Sharp
 * @since 2023/05/17 16:09
 */
public interface LoginFillTokenInf {
    /**
     * 登录后需要存入token数据
     *
     * @param token
     */
    void fillToken(LoginRes token);
}
