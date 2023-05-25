package com.bidr.authorization.service.account;

import com.bidr.authorization.dao.entity.AcAccount;
import com.bidr.authorization.dao.entity.AcUser;
import com.bidr.authorization.dao.repository.AcAccountService;
import com.bidr.authorization.dao.repository.AcUserService;
import com.bidr.authorization.service.user.CreateUserService;
import com.bidr.kernel.utils.FuncUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Title: AccountInitService
 * Description: Copyright: Copyright (c) 2023
 *
 * @author Sharp
 * @since 2023/05/25 13:38
 */
@Service
@RequiredArgsConstructor
public class AccountInitService {

    private final AcAccountService accountService;
    private final AcUserService acUserService;
    private final CreateUserService createUserService;

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
