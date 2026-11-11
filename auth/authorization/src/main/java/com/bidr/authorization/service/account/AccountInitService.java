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
                    // 2026-11-11：已存在用户的身份字段跟随主数据（此前只更部门，姓名/头像只在
                    // 首次建号 mergeAccount 时写一次 ⇒ 身份源改名换头像后，重登仍读旧 ac_user）。
                    boolean changed = false;
                    if (!FuncUtil.equals(account.getDepartment(), user.getDeptId())) {
                        userInfoService.updateDept(account, user);
                        user.setDeptId(account.getDepartment());
                        changed = true;
                    }
                    // 身份源为主数据：MDM 的 name / 飞书的 displayName 落到 ac_user.name；
                    // 空值不覆盖（半拉子快照不擦掉已有姓名），同值不写（减少无意义 update）。
                    if (FuncUtil.isNotEmpty(account.getName())
                            && !FuncUtil.equals(account.getName(), user.getName())) {
                        user.setName(account.getName());
                        changed = true;
                    }
                    // MDM pictureLink / 飞书 avatar 落到 ac_user.avatar（同上：非空且不同才写）
                    if (FuncUtil.isNotEmpty(account.getPictureLink())
                            && !FuncUtil.equals(account.getPictureLink(), user.getAvatar())) {
                        user.setAvatar(account.getPictureLink());
                        changed = true;
                    }
                    if (changed) {
                        acUserService.updateById(user);
                    }
                }
            }
        }
    }
}
