package com.bidr.authorization.service.account;

import com.bidr.authorization.dao.entity.AcAccount;
import com.bidr.authorization.dao.entity.AcUser;
import com.bidr.authorization.dao.repository.AcAccountService;
import com.bidr.authorization.dao.repository.AcUserService;
import com.bidr.authorization.service.user.CreateUserService;
import com.bidr.authorization.service.user.UserInfoService;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

import java.util.Arrays;
import java.util.Collections;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;

/**
 * Title: AccountInitServiceTest
 * Description: 组织同步落库到 ac_user 的身份字段跟随（2026-11-11 用户批「重新登录后名字/头像不换」）：
 * 已存在用户的 name/avatar 必须随 ac_account 主数据更新；空值/同值不写，部门变化仍走 updateDept。
 * Copyright: Copyright (c) 2026 Company: Bidr Ltd.
 *
 * @author Sharp
 * @since 2026-11-11
 */
public class AccountInitServiceTest {

    private final AcAccountService accountService = Mockito.mock(AcAccountService.class);
    private final AcUserService acUserService = Mockito.mock(AcUserService.class);
    private final CreateUserService createUserService = Mockito.mock(CreateUserService.class);
    private final UserInfoService userInfoService = Mockito.mock(UserInfoService.class);

    private final AccountInitService service =
            new AccountInitService(accountService, acUserService, createUserService, userInfoService);

    @Before
    public void setUp() {
        Mockito.when(accountService.selectActive()).thenReturn(Collections.emptyList());
    }

    /** 身份源改名/换头像 ⇒ 已存在 ac_user 的 name/avatar 一次写回（部门未变不碰 updateDept） */
    @Test
    public void syncsNameAndAvatarForExistingUser() {
        AcAccount account = account("A1", "张三", "http://a/1.png", "D1");
        AcUser user = user("A1", "张三旧", "http://a/old.png", "D1");
        Mockito.when(acUserService.getByCustomerNumber("A1")).thenReturn(user);

        service.accountSyncUser(Arrays.asList(account));

        ArgumentCaptor<AcUser> captor = ArgumentCaptor.forClass(AcUser.class);
        Mockito.verify(acUserService, Mockito.times(1)).updateById(captor.capture());
        assertEquals("张三", captor.getValue().getName());
        assertEquals("http://a/1.png", captor.getValue().getAvatar());
        Mockito.verify(userInfoService, Mockito.never())
                .updateDept(Mockito.any(AcAccount.class), Mockito.any(AcUser.class));
    }

    /** 同值不写：身份字段没变 ⇒ 不产生 updateById（避免每次同步全量刷 update_at） */
    @Test
    public void skipsUpdateWhenProfileUnchanged() {
        AcAccount account = account("A1", "张三", "http://a/1.png", "D1");
        AcUser user = user("A1", "张三", "http://a/1.png", "D1");
        Mockito.when(acUserService.getByCustomerNumber("A1")).thenReturn(user);

        service.accountSyncUser(Arrays.asList(account));

        Mockito.verify(acUserService, Mockito.never()).updateById(Mockito.any(AcUser.class));
    }

    /** 身份源半拉子快照（name/pictureLink 为空）不覆盖已有姓名/头像 */
    @Test
    public void blankProfileDoesNotOverwrite() {
        AcAccount account = account("A1", null, "", "D1");
        AcUser user = user("A1", "张三", "http://a/old.png", "D1");
        Mockito.when(acUserService.getByCustomerNumber("A1")).thenReturn(user);

        service.accountSyncUser(Arrays.asList(account));

        Mockito.verify(acUserService, Mockito.never()).updateById(Mockito.any(AcUser.class));
        assertEquals("张三", user.getName());
        assertEquals("http://a/old.png", user.getAvatar());
    }

    /**
     * 并账腿（2026-11-11 pre 部署实测）：customerNumber 查不到，但 user_name 已有另一套
     * customerNumber 的账号（SSO JIT 建号）——必须按同一身份改绑并同步，绝不能 insert
     * （否则撞 ac_user.user_name 唯一键，pre 3197 账号同步在 sha_rp 上整批回滚）。
     */
    @Test
    public void mergesExistingUserByUserNameInsteadOfInsert() {
        AcAccount account = account("MDM-1", "张三", "http://a/1.png", "D2");
        account.setUserName("sha_rp");
        AcUser existing = user("OLD-CUST", "旧名", "", "D1");
        existing.setUserName("sha_rp");
        Mockito.when(acUserService.getByCustomerNumber("MDM-1")).thenReturn(null);
        Mockito.when(acUserService.getUserByUserName("sha_rp")).thenReturn(existing);

        service.accountSyncUser(Arrays.asList(account));

        Mockito.verify(createUserService, Mockito.never()).createUser(Mockito.any(AcAccount.class));
        ArgumentCaptor<AcUser> captor = ArgumentCaptor.forClass(AcUser.class);
        Mockito.verify(acUserService, Mockito.times(1)).updateById(captor.capture());
        assertEquals("MDM-1", captor.getValue().getCustomerNumber());
        assertEquals("张三", captor.getValue().getName());
        assertEquals("http://a/1.png", captor.getValue().getAvatar());
        assertEquals("D2", captor.getValue().getDeptId());
    }

    /** 部门变化仍沿用原口径：updateDept + 同一次 updateById 带上新部门与身份字段 */
    @Test
    public void departmentChangeStillSyncsAndKeepsProfile() {
        AcAccount account = account("A1", "张三", "http://a/1.png", "D2");
        AcUser user = user("A1", "张三", "http://a/1.png", "D1");
        Mockito.when(acUserService.getByCustomerNumber("A1")).thenReturn(user);

        service.accountSyncUser(Arrays.asList(account));

        Mockito.verify(userInfoService, Mockito.times(1)).updateDept(account, user);
        ArgumentCaptor<AcUser> captor = ArgumentCaptor.forClass(AcUser.class);
        Mockito.verify(acUserService, Mockito.times(1)).updateById(captor.capture());
        assertEquals("D2", captor.getValue().getDeptId());
        assertSame(user, captor.getValue());
    }

    // ==================== 夹具 ====================

    private static AcAccount account(String id, String name, String pictureLink, String dept) {
        AcAccount account = new AcAccount();
        account.setId(id);
        account.setName(name);
        account.setPictureLink(pictureLink);
        account.setDepartment(dept);
        return account;
    }

    private static AcUser user(String customerNumber, String name, String avatar, String dept) {
        AcUser user = new AcUser();
        user.setUserId(1L);
        user.setCustomerNumber(customerNumber);
        user.setName(name);
        user.setAvatar(avatar);
        user.setDeptId(dept);
        return user;
    }
}