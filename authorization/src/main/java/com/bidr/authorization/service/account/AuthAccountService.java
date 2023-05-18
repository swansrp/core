package com.bidr.authorization.service.account;

import com.bidr.authorization.dao.entity.AcAccount;
import com.bidr.authorization.dao.entity.AcUser;
import com.bidr.authorization.dao.repository.AcAccountService;
import com.bidr.authorization.dao.repository.AcUserService;
import com.bidr.authorization.service.user.CreateUserService;
import com.bidr.authorization.vo.account.AccountReq;
import com.bidr.authorization.vo.account.AccountRes;
import com.bidr.kernel.config.response.Resp;
import com.bidr.kernel.utils.FuncUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Title: AuthAccountService
 * Description: Copyright: Copyright (c) 2022 Company: Sharp Ltd.
 *
 * @author Sharp
 * @since 2023/04/23 15:10
 */
@Service
@RequiredArgsConstructor
public class AuthAccountService {

    private final AcAccountService accountService;
    private final AcUserService acUserService;
    private final CreateUserService createUserService;


    public List<AccountRes> getAccount(AccountReq req) {
        List<AcUser> res = acUserService.getUserByDeptAndName(req.getDeptIdList(), req.getName());
        return Resp.convert(res, AccountRes.class);
    }

    public AcAccount getAccountByUserName(String userName) {
        return accountService.getAccountByUserName(userName);
    }

    @Transactional(rollbackFor = Exception.class)
    public void accountSyncUser() {
        List<AcAccount> acAccountList = accountService.selectActive();
        accountSyncUser(acAccountList);
    }

    @Transactional(rollbackFor = Exception.class)
    public void accountSyncUser(List<AcAccount> acAccountList) {
        if (FuncUtil.isNotEmpty(acAccountList)) {
            for (AcAccount account : acAccountList) {
                AcUser user = acUserService.getByCustomerNumber(account.getId());
                if (FuncUtil.isEmpty(user)) {
                    createUserService.createUser(account);
                }
            }
        }
    }
}
