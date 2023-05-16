package com.bidr.authorization.controller.login;

import com.bidr.authorization.service.login.LoginService;
import io.swagger.annotations.Api;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Title: LoginController
 * Description: Copyright: Copyright (c) 2022 Company: Sharp Ltd.
 *
 * @author Sharp
 * @since 2023/04/26 16:17
 */
@Api(tags = "系统基础 - 登录操作")
@RestController("LoginController")
@RequestMapping(value = "/web/login")
@RequiredArgsConstructor
public class LoginController {

    private final LoginService loginService;


}
