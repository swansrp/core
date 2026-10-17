package com.bidr.authorization.controller.login;

import com.bidr.authorization.service.login.LoginService;
import io.swagger.annotations.Api;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;

/**
 * Title: LogoffController
 * Description: Copyright: Copyright (c) 2023
 *
 * @author Sharp
 * @since 2023/08/25 14:53
 */
@Api(tags = "系统基础 - 登录操作")
@RestController("LogoffController")
@RequestMapping(value = "/web/logoff")
@RequiredArgsConstructor
public class LogoffController {

    private final LoginService loginService;

    @RequestMapping(value = "", method = RequestMethod.POST)
    public void logoff() {
        loginService.logoff();
    }
}
