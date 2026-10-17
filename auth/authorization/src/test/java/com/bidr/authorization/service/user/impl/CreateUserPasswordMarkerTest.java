package com.bidr.authorization.service.user.impl;

import com.bidr.authorization.dao.entity.AcUser;
import com.bidr.authorization.dao.repository.AcUserDeptService;
import com.bidr.authorization.dao.repository.AcUserService;
import com.bidr.authorization.service.login.RoleBindService;
import com.bidr.kernel.utils.FuncUtil;
import com.bidr.platform.service.cache.SysConfigCacheService;
import org.junit.Assert;
import org.junit.Test;
import org.mockito.Mockito;

/**
 * 建号链「未设过密码」标记契约（2026-10-09，erp F-4）。
 * <p>无密码账号必须落 {@code passwordLastTime=epoch(0)}：LoginServiceImpl 无密码分支凭它抛
 * {@code AC_PASSWORD_NOT_EXISTED}，登录页才翻「首次登录-设置密码」面板；置 now 会让面板永不可达。
 * 带密码注册链必须落 now，否则 PASSWORD_EXPIRED(365) 会把首登直接判成过期。</p>
 */
public class CreateUserPasswordMarkerTest {

    private CreateUserServiceImpl newService() {
        return new CreateUserServiceImpl(
                Mockito.mock(AcUserService.class),
                Mockito.mock(SysConfigCacheService.class),
                Mockito.mock(RoleBindService.class),
                Mockito.mock(AcUserDeptService.class));
    }

    @Test
    public void passwordLessUserGetsEpochMarker() {
        CreateUserServiceImpl service = newService();
        AcUser user = service.createUser("13900000001", null, "张三", "13900000001", null, null);
        Assert.assertEquals("", user.getPassword());
        Assert.assertNotNull(user.getPasswordLastTime());
        Assert.assertEquals(0L, user.getPasswordLastTime().getTime());
    }

    @Test
    public void passwordUserGetsCurrentTime() {
        CreateUserServiceImpl service = newService();
        long before = System.currentTimeMillis();
        AcUser user = service.createUser("regUser", "abc123", "李四", null, null, null);
        Assert.assertTrue(FuncUtil.isNotEmpty(user.getPassword()));
        Assert.assertNotNull(user.getPasswordLastTime());
        Assert.assertTrue(user.getPasswordLastTime().getTime() >= before);
    }
}
