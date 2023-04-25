package com.bidr.authorization.service.account;

import com.bidr.authorization.dao.entity.AcAccount;
import com.bidr.authorization.dao.repository.AcAccountService;
import com.bidr.authorization.vo.account.AccountReq;
import com.bidr.authorization.vo.account.AccountRes;
import com.bidr.kernel.config.response.Resp;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Title: AuthAccountService
 * Description: Copyright: Copyright (c) 2022 Company: Sharp Ltd.
 *
 * @author Sharp
 * @date 2023/04/23 15:10
 */
@Service
@RequiredArgsConstructor
public class AuthAccountService {

    private final AcAccountService accountService;


    public List<AccountRes> getAccount(AccountReq req) {
        List<AcAccount> res = accountService.getAccountByDeptAndName(req.getDeptIdList(), req.getName());
        return Resp.convert(res, AccountRes.class);
    }
}
