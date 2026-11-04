package com.bidr.authorization.seed;

import com.bidr.authorization.config.TenantProperties;
import com.bidr.authorization.dao.entity.AcTenant;
import com.bidr.authorization.dao.repository.AcTenantService;
import org.junit.Assert;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

/**
 * Title: TenantSeederTest
 * Description: 租户种子三态离线腿（T1.0 棒1）——mock AcTenantService，不连库；
 * 覆盖「code 有值+auto-create=true 建」「code 有值+auto-create=false 缺行即拒」「code 未配 skip」三态。
 * DB 真值腿交真机（起服务核 ac_tenant，重启第二次零新增）。
 *
 * @author Sharp
 * @since 2026-11-01
 */
public class TenantSeederTest {

    private TenantSeeder newSeeder(TenantProperties properties, AcTenantService service) {
        return new TenantSeeder(properties, service);
    }

    /** 三态一：code 有值 + auto-create=true + 库里无该 code → 自动建行（code/name/valid 形态钉住）。 */
    @Test
    public void codeWithAutoCreateInsertsTenantWhenMissing() throws Exception {
        TenantProperties properties = new TenantProperties();
        properties.setCode("T1");
        properties.setName("租户一");
        properties.setAutoCreate(true);
        AcTenantService service = Mockito.mock(AcTenantService.class);
        Mockito.when(service.getByCode("T1")).thenReturn(null);

        newSeeder(properties, service).run(null);

        ArgumentCaptor<AcTenant> captor = ArgumentCaptor.forClass(AcTenant.class);
        Mockito.verify(service, Mockito.times(1)).insert(captor.capture());
        AcTenant inserted = captor.getValue();
        Assert.assertEquals("T1", inserted.getCode());
        Assert.assertEquals("租户一", inserted.getName());
        Assert.assertEquals("1", inserted.getValid());
    }

    /** 三态二：code 有值 + auto-create=false + 库里无该 code → 抛异常拒启动（半截启动比不启动糟）。 */
    @Test
    public void codeWithoutAutoCreateFailsWhenMissing() {
        TenantProperties properties = new TenantProperties();
        properties.setCode("T2");
        properties.setAutoCreate(false);
        AcTenantService service = Mockito.mock(AcTenantService.class);
        Mockito.when(service.getByCode("T2")).thenReturn(null);

        try {
            newSeeder(properties, service).run(null);
            Assert.fail("auto-create=false 且库无该 code 必须抛 IllegalStateException");
        } catch (IllegalStateException expected) {
            Assert.assertTrue(expected.getMessage().contains("T2"));
        } catch (Exception wrong) {
            Assert.fail("应抛 IllegalStateException，实际 " + wrong);
        }
        Mockito.verify(service, Mockito.never()).insert(Mockito.any(AcTenant.class));
    }

    /** 三态三：code 未配置（空白）→ skip，零交互（非租户形态语义）。 */
    @Test
    public void blankCodeSkipsEntirely() throws Exception {
        TenantProperties properties = new TenantProperties();
        properties.setCode("   ");
        AcTenantService service = Mockito.mock(AcTenantService.class);

        newSeeder(properties, service).run(null);

        Mockito.verifyNoInteractions(service);
    }
}
