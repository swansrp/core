package com.bidr.authorization.controller.admin;

import com.bidr.authorization.annotation.auth.Auth;
import com.bidr.authorization.annotation.auth.AuthAdmin;
import com.bidr.authorization.controller.login.LoginController;
import com.bidr.authorization.service.login.LoginFillTokenInf;
import com.bidr.authorization.service.login.LoginService;
import com.bidr.authorization.vo.login.LoginRes;
import com.bidr.kernel.utils.FuncUtil;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;

import javax.annotation.Resource;
import java.util.List;

/**
 * Title: AdminController
 * Description: Copyright: Copyright (c) 2023
 *
 * @author Sharp
 * @since 2023/10/17 15:41
 */
@Auth(AuthAdmin.class)
@Api(tags = "系统管理 - 超管功能")
@RestController("AdminController")
@RequestMapping(value = "/web/admin")
public class AdminController extends LoginController {

    @ApiOperation(value = "幽灵登录", notes = "超管使用指定人员账号幽灵登录")
    @RequestMapping(value = "/ghost/login", method = RequestMethod.POST)
    public LoginRes login(String customerNumber) {
        LoginRes res = loginService.ghostLogin(customerNumber);
        afterLogin(res);
        return res;
    }
}
