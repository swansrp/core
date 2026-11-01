package com.bidr.authorization.seed;

import com.bidr.authorization.config.AdminProperties;
import com.bidr.authorization.dao.entity.AcUser;
import com.bidr.authorization.dao.repository.AcRoleService;
import com.bidr.authorization.dao.repository.AcUserService;
import com.bidr.authorization.dao.repository.AcUserRoleService;
import com.bidr.authorization.dao.schema.AcUserSchema;
import com.bidr.kernel.utils.Md5Util;
import com.bidr.platform.service.cache.SysConfigCacheService;
import org.junit.Assert;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

/**
 * Title: AdminUserSeederTest
 * Description: 管理员种子密码策略离线腿（T1.0 棒1）——mock 仓库层，不连库；
 * 覆盖 pristine（工厂态 hash 按配置重置）/ 非 pristine（一律不覆盖）/ 新建（随机密码）三条腿。
 *
 * @author Sharp
 * @since 2026-11-01
 */
public class AdminUserSeederTest {

    private AdminUserSeeder newSeeder(AdminProperties properties, AcUserService userService) {
        return new AdminUserSeeder(properties, userService,
                Mockito.mock(AcUserRoleService.class),
                Mockito.mock(AcRoleService.class),
                Mockito.mock(SysConfigCacheService.class));
    }

    private AcUser userWithPassword(String passwordHash) {
        AcUser user = new AcUser();
        user.setUserId(1L);
        user.setUserName("BidrAdmin");
        user.setPassword(passwordHash);
        return user;
    }

    /** 腿一：存量账号密码＝工厂态 hash（pristine）＋ initial-password 有值 → 按配置明文重置落库。 */
    @Test
    public void pristineExistingResetsPasswordFromConfig() throws Exception {
        AdminProperties properties = new AdminProperties();
        properties.setUsername("BidrAdmin");
        properties.setInitialPassword("Init#12345");
        AcUserService userService = Mockito.mock(AcUserService.class);
        Mockito.when(userService.getUserByUserName("BidrAdmin"))
                .thenReturn(userWithPassword(AcUserSchema.FACTORY_ADMIN_PASSWORD_HASH));

        newSeeder(properties, userService).run(null);

        ArgumentCaptor<AcUser> captor = ArgumentCaptor.forClass(AcUser.class);
        Mockito.verify(userService, Mockito.times(1)).updateById(captor.capture());
        AcUser updated = captor.getValue();
        Assert.assertNotEquals(AcUserSchema.FACTORY_ADMIN_PASSWORD_HASH, updated.getPassword());
        Assert.assertTrue("pristine 重置后必须能用配置明文验过",
                Md5Util.verify("Init#12345", updated.getPassword()));
    }

    /** 腿二：非 pristine（人工已改，hash ≠ 工厂态）→ 一律不覆盖（连配置了 initial-password 也不动）。 */
    @Test
    public void nonPristinePasswordIsNeverOverwritten() throws Exception {
        AdminProperties properties = new AdminProperties();
        properties.setUsername("BidrAdmin");
        properties.setInitialPassword("Init#12345");
        AcUserService userService = Mockito.mock(AcUserService.class);
        String humanHash = Md5Util.generate("human-set-password");
        Mockito.when(userService.getUserByUserName("BidrAdmin"))
                .thenReturn(userWithPassword(humanHash));

        newSeeder(properties, userService).run(null);

        Mockito.verify(userService, Mockito.never()).updateById(Mockito.any(AcUser.class));
        Mockito.verify(userService, Mockito.never()).insert(Mockito.any(AcUser.class));
    }

    /** 腿三：username 有值＋库里无号＋initial-password 未配置 → 新建号＋随机 16 位密码（工厂态之外的 48 位盐化 hash）。 */
    @Test
    public void newUserGetsRandomPasswordWhenNoInitialConfigured() throws Exception {
        AdminProperties properties = new AdminProperties();
        properties.setUsername("FreshAdmin");
        properties.setInitialPassword("");
        AcUserService userService = Mockito.mock(AcUserService.class);
        Mockito.when(userService.getUserByUserName("FreshAdmin")).thenReturn(null);

        newSeeder(properties, userService).run(null);

        ArgumentCaptor<AcUser> captor = ArgumentCaptor.forClass(AcUser.class);
        Mockito.verify(userService, Mockito.times(1)).insert(captor.capture());
        Mockito.verify(userService, Mockito.never()).updateById(Mockito.any(AcUser.class));
        AcUser created = captor.getValue();
        Assert.assertEquals("FreshAdmin", created.getUserName());
        Assert.assertEquals("00", created.getUserType());
        String hash = created.getPassword();
        Assert.assertTrue("随机密码 hash 必须非空", hash != null && !hash.isEmpty());
        Assert.assertNotEquals(AcUserSchema.FACTORY_ADMIN_PASSWORD_HASH, hash);
        Assert.assertEquals("Md5Util.generate 形态＝48 位盐化 MD5", 48, hash.length());
        Assert.assertNotNull("新号已设密码，passwordLastTime 不得留 epoch(0) 标记", created.getPasswordLastTime());
        Assert.assertTrue(created.getPasswordLastTime().getTime() > 0L);
    }
}
