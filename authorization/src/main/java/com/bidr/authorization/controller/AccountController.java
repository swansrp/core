package com.bidr.authorization.controller;

import com.bidr.authorization.service.account.AuthAccountService;
import com.bidr.authorization.vo.account.AccountReq;
import com.bidr.authorization.vo.account.AccountRes;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Title: AccountController
 * Description: Copyright: Copyright (c) 2022 Company: Sharp Ltd.
 *
 * @author Sharp
 * @since 2023/04/23 15:09
 */
@Api(tags = "系统基础 - 人事信息")
@RestController("AccountController")
@RequestMapping(value = "/web/account")
@RequiredArgsConstructor
public class AccountController {

    private final AuthAccountService accountService;

    @ApiOperation(value = "获取用户")
    @RequestMapping(value = "", method = RequestMethod.POST)
    public List<AccountRes> getAccount(@RequestBody @Validated AccountReq req) {
        return accountService.getAccount(req);
    }

    /**
     * 通过用户姓名/组织名称/用户id获取用户信息
     *
     * @param names 用户姓名/组织名称/用户id列表
     * @return 用户信息
     */
    @ApiOperation(value = "获取用户信息")
    @RequestMapping(value = "/search", method = RequestMethod.POST)
    public List<AccountRes> getAccountByUserNameOrDeptName(@RequestBody List<String> names,
                                                           @RequestParam(name = "active", required = false,
                                                                         defaultValue = "true")
                                                           boolean active) {
        return accountService.getAccountByUserNameOrDeptNameOrAccountId(names, active);
    }


}
