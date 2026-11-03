package com.bidr.authorization.controller.identity;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.bidr.authorization.dao.entity.AcTenant;
import com.bidr.authorization.dao.entity.AcUser;
import com.bidr.authorization.dao.entity.AcUserTenant;
import com.bidr.authorization.dao.repository.AcTenantService;
import com.bidr.authorization.dao.repository.AcUserTenantService;
import com.bidr.authorization.dao.repository.AcUserService;
import com.bidr.authorization.vo.identity.IdentitySnapshotRes;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;

import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * Title: IdentitySnapshotControllerTest
 * Description: 身份快照端点单测（T2.3d provider 侧，纯 JUnit4＋Mockito）——
 * mock 三个既有 repository，直调 {@code snapshot()} 断言装配映射：
 * <ul>
 *     <li>用户：userId/customerNumber/userName/name 四面直拷（取数经 valid='1' wrapper，装配不关心）；</li>
 *     <li>租户：code→tenantCode、name→tenantName 的字段改名映射；</li>
 *     <li>关系：userId/tenantId 两面直拷；</li>
 *     <li>watermark＞0；</li>
 *     <li>G9 边界（对偶消费侧 IdentityMirrorConventionTest）：响应 DTO 全部字段名
 *         （含嵌套条目类）零 role/menu/perm/function——快照永不携带授权语义。</li>
 * </ul>
 *
 * @author Sharp
 * @since 2026/11/03
 */
public class IdentitySnapshotControllerTest {

    private AcUserService acUserService;
    private AcTenantService acTenantService;
    private AcUserTenantService acUserTenantService;
    private IdentitySnapshotController controller;

    @Before
    public void setUp() {
        acUserService = Mockito.mock(AcUserService.class);
        acTenantService = Mockito.mock(AcTenantService.class);
        acUserTenantService = Mockito.mock(AcUserTenantService.class);
        controller = new IdentitySnapshotController(acUserService, acTenantService, acUserTenantService);

        AcUser user = new AcUser();
        user.setUserId(7L);
        user.setCustomerNumber("000007");
        user.setUserName("wb");
        user.setName("王五");
        Mockito.when(acUserService.getQueryWrapper()).thenReturn(new LambdaQueryWrapper<>());
        Mockito.when(acUserService.select(Mockito.any(Wrapper.class))).thenReturn(Collections.singletonList(user));

        AcTenant tenant = new AcTenant();
        tenant.setId(30L);
        tenant.setCode("T30");
        tenant.setName("三十号租户");
        Mockito.when(acTenantService.listValid()).thenReturn(Collections.singletonList(tenant));

        AcUserTenant relation = new AcUserTenant();
        relation.setUserId(7L);
        relation.setTenantId(30L);
        Mockito.when(acUserTenantService.select()).thenReturn(Collections.singletonList(relation));
    }

    @Test
    public void snapshotAssemblesIdentityProjection() {
        IdentitySnapshotRes res = controller.snapshot();

        Assert.assertTrue("watermark 必须是生成时刻（>0）", res.getWatermark() > 0);
        Assert.assertEquals(1, res.getUsers().size());
        IdentitySnapshotRes.UserItem user = res.getUsers().get(0);
        Assert.assertEquals(Long.valueOf(7L), user.getId());
        Assert.assertEquals("000007", user.getCustomerNumber());
        Assert.assertEquals("登录名（T2.4a 补料）直拷", "wb", user.getUserName());
        Assert.assertEquals("王五", user.getName());

        Assert.assertEquals(1, res.getTenants().size());
        IdentitySnapshotRes.TenantItem tenant = res.getTenants().get(0);
        Assert.assertEquals(Long.valueOf(30L), tenant.getId());
        Assert.assertEquals("租户业务编码改名映射 code→tenantCode", "T30", tenant.getTenantCode());
        Assert.assertEquals("三十号租户", tenant.getTenantName());

        Assert.assertEquals(1, res.getUserTenants().size());
        Assert.assertEquals(Long.valueOf(7L), res.getUserTenants().get(0).getUserId());
        Assert.assertEquals(Long.valueOf(30L), res.getUserTenants().get(0).getTenantId());
    }

    /** G9：响应 DTO（外层＋三个嵌套条目类）字段名零授权语义 */
    @Test
    public void snapshotDtoCarriesNoAuthorizationFields() {
        List<Class<?>> shapes = Arrays.asList(IdentitySnapshotRes.class,
                IdentitySnapshotRes.UserItem.class,
                IdentitySnapshotRes.TenantItem.class,
                IdentitySnapshotRes.UserTenantItem.class);
        for (Class<?> shape : shapes) {
            for (Field field : shape.getDeclaredFields()) {
                String name = field.getName().toLowerCase();
                for (String token : Arrays.asList("role", "menu", "perm", "function")) {
                    Assert.assertFalse("快照 DTO 出现授权语义字段（G9 边界）: "
                            + shape.getSimpleName() + "." + field.getName(), name.contains(token));
                }
            }
        }
    }
}
