package com.bidr.authorization.controller;

import com.bidr.authorization.service.account.AuthAccountService;
import com.bidr.authorization.vo.account.AccountReq;
import com.bidr.authorization.vo.account.AccountRes;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Title: AccountController
 * Description: Copyright: Copyright (c) 2022 Company: Sharp Ltd.
 *
 * @author Sharp
 * @since 2023/04/23 15:09
 */
@Api(value = "人事信息", tags = "人事信息")
@RestController("AccountController")
@RequestMapping(value = "/web/account")
@RequiredArgsConstructor
public class AccountController {

    private final AuthAccountService accountService;

    @ApiOperation(value = "获取用户")
    @RequestMapping(value = "", method = RequestMethod.POST)
    public List<AccountRes> getAccount(@RequestBody AccountReq req) {
        return accountService.getAccount(req);
    }


}
