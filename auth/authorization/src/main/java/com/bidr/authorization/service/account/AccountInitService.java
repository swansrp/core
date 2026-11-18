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
                    // 🔴 2026-11-11 并账腿（pre 部署实测）：SSO JIT/历史账号可能已用同一 user_name 建号，
                    // 但 customer_number 是另一套值（如 SSO 建号时生成的），于是按 customerNumber 查不到、
                    // 走 insert 撞 ac_user.user_name 唯一键（pre 3197 账号同步在 sha_rp 上炸整批）。
                    // user_name 是登录唯一键 ⇒ 同一 user_name 视为同一身份：改绑 customer_number 并同步
                    // 资料/部门，绝不 insert。
                    AcUser byName = FuncUtil.isEmpty(account.getUserName())
                            ? null : acUserService.getUserByUserName(account.getUserName());
                    if (byName != null) {
                        log.info("[组织同步] user_name 已存在（customerNumber {} -> {}），按同一身份并账: {}",
                                byName.getCustomerNumber(), account.getId(), account.getUserName());
                        syncExistingUser(account, byName);
                        continue;
                    }
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
                    syncExistingUser(account, user);
                }
            }
        }
    }

    /**
     * 已存在用户的身份字段跟随主数据（2026-11-11）：customer_number 对齐主数据 id（SSO JIT 建号时
     * 是另一套值，token/审计的 operator 正源），部门/姓名/头像非空且不同才写；一次 updateById。
     * 空值不覆盖（半拉子快照不擦掉已有资料），同值不写（减少无意义 update）。
     */
    private void syncExistingUser(AcAccount account, AcUser user) {
        boolean changed = false;
        if (!FuncUtil.equals(account.getId(), user.getCustomerNumber())) {
            user.setCustomerNumber(account.getId());
            changed = true;
        }
        if (!FuncUtil.equals(account.getDepartment(), user.getDeptId())) {
            userInfoService.updateDept(account, user);
            user.setDeptId(account.getDepartment());
            changed = true;
        }
        // 身份源为主数据：MDM 的 name / 飞书的 displayName 落到 ac_user.name
        if (FuncUtil.isNotEmpty(account.getName())
                && !FuncUtil.equals(account.getName(), user.getName())) {
            user.setName(account.getName());
            changed = true;
        }
        // MDM pictureLink / 飞书 avatar 落到 ac_user.avatar
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
