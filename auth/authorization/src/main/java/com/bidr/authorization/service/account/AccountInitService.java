package com.bidr.authorization.service.account;

import com.bidr.authorization.dao.entity.AcAccount;
import com.bidr.authorization.dao.entity.AcUser;
import com.bidr.authorization.dao.repository.AcAccountService;
import com.bidr.authorization.dao.repository.AcUserService;
import com.bidr.authorization.service.user.CreateUserService;
import com.bidr.authorization.service.user.UserInfoService;
import com.bidr.kernel.utils.FuncUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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
@Slf4j
@Service
@RequiredArgsConstructor
public class AccountInitService {

    private final AcAccountService accountService;
    private final AcUserService acUserService;
    private final CreateUserService createUserService;
    private final UserInfoService userInfoService;

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
                    // T1.1 棒3：租户模式下解析不到归属的身份跳过建号（fail-closed），批内其余继续。
                    // 🔴 用预检而非 per-user try/catch：本方法与 createUser 同事务（REQUIRED 汇合），
                    // 漏斗异常一旦穿出代理即标记 rollback-only，catch 住也会在提交时整批炸
                    // UnexpectedRollbackException——预检让注定被拒的身份根本不进漏斗。
                    if (!createUserService.isTenantAssignable(account.getUserName())) {
                        log.warn("[tenant] MDM 同步跳过身份[{}]（customerNumber={}）：租户模式开启但解析不到有效租户归属，拒绝建号",
                                account.getUserName(), account.getId());
                        continue;
                    }
                    createUserService.createUser(account);
                } else {
                    if (!FuncUtil.equals(account.getDepartment(), user.getDeptId())) {
                        userInfoService.updateDept(account, user);
                        user.setDeptId(account.getDepartment());
                        acUserService.updateById(user);
                    }
                }
            }
        }
    }
}
