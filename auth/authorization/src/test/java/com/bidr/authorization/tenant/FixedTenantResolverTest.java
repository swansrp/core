package com.bidr.authorization.tenant;

import com.bidr.authorization.config.TenantProperties;
import org.junit.Assert;
import org.junit.Test;

import java.util.Optional;

/**
 * Title: FixedTenantResolverTest
 * Description: TenantResolver 默认实现离线腿（T1.0 棒1）——配置空白 ⇒ Optional.empty()（非租户形态语义）、
 * 配置有值 ⇒ 去空白返回。不连库、不起容器。
 *
 * @author Sharp
 * @since 2026-11-01
 */
public class FixedTenantResolverTest {

    /** 空/纯空白/未配置 ⇒ Optional.empty()＝非租户形态，不是错误。 */
    @Test
    public void blankConfigYieldsEmpty() {
        FixedTenantResolver resolver = new FixedTenantResolver(new TenantProperties());
        Assert.assertEquals(Optional.empty(), resolver.resolveTenantCode("anyone"));

        TenantProperties whitespace = new TenantProperties();
        whitespace.setCode("   ");
        Assert.assertEquals(Optional.empty(), new FixedTenantResolver(whitespace).resolveTenantCode("anyone"));

        TenantProperties nullCode = new TenantProperties();
        nullCode.setCode(null);
        Assert.assertEquals(Optional.empty(), new FixedTenantResolver(nullCode).resolveTenantCode(null));
    }

    /** 配置有值 ⇒ 去首尾空白返回（code 口径＝ac_tenant.code）。 */
    @Test
    public void configuredCodeYieldsTrimmedValue() {
        TenantProperties properties = new TenantProperties();
        properties.setCode("  T-CODE-1  ");
        Optional<String> resolved = new FixedTenantResolver(properties).resolveTenantCode("zhangsan");
        Assert.assertTrue(resolved.isPresent());
        Assert.assertEquals("T-CODE-1", resolved.get());
    }
}
