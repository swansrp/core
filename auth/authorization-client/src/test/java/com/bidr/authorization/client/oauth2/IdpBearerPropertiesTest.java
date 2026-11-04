package com.bidr.authorization.client.oauth2;

import org.junit.Assert;
import org.junit.Test;
import org.springframework.mock.env.MockEnvironment;

/**
 * {@link IdpBearerProperties} 配置语义守卫（2026-11-04 改：**starter 不再内置任何公钥**）。
 *
 * <p>背景：墨枢已改为按部署生成密钥对，公钥不可能是"标准值"。此前的内置默认公钥会让
 * 非标准部署**静默验签失败**（用户只看到 401，报错不指向公钥）。现在的契约是
 * 「接入方只配 {@code server-base-url}，公钥运行时从 JWKS 取」，因此：</p>
 * <ol>
 *   <li>默认全空且 {@code enabled=false}——宿主不配置零影响（不悄悄多出验签入口）；</li>
 *   <li>显式配置正常生效（{@code server-base-url} 或 {@code public-key-base64}）；</li>
 *   <li>{@code enabled=true} 但两个钥来源都缺 ⇒ **装配期 fail-fast**（不是运行期静默 401）。</li>
 * </ol>
 *
 * @author sharp
 * @since 2026-11-04
 */
public class IdpBearerPropertiesTest {

    @Test
    public void defaultsAreEmptyAndDisabled() {
        IdpBearerProperties properties = new IdpBearerProperties();
        Assert.assertFalse("默认不装配：宿主必须显式 enabled=true", properties.isEnabled());
        Assert.assertEquals("不得内置 server-base-url", "", properties.getServerBaseUrl());
        Assert.assertEquals("不得内置任何公钥（按部署生成密钥对）", "", properties.getPublicKeyBase64());
        Assert.assertEquals("不得内置期望接入方（由宿主配置或回退 OAuth2 client-id）", "",
                properties.getAppId());
        Assert.assertFalse("未配置时验签器应判不可用（fail-closed）",
                new IdpBearerVerifier(properties).enabled());
    }

    @Test
    public void explicitConfigurationWins() {
        IdpBearerProperties properties = new IdpBearerProperties();
        properties.setServerBaseUrl("http://inkhub.test/inkhub");
        properties.setPublicKeyBase64("custom-static-key");
        properties.setAppId("epc-ai");
        Assert.assertEquals("http://inkhub.test/inkhub", properties.getServerBaseUrl());
        Assert.assertEquals("custom-static-key", properties.getPublicKeyBase64());
        Assert.assertEquals("epc-ai", properties.getAppId());
        // 两种来源任一存在即视为可用（远端优先；静态作兜底）
        Assert.assertTrue(new IdpBearerVerifier(properties).enabled());
    }

    @Test
    public void explicitAppIdWinsOverOAuth2ClientId() {
        IdpBearerProperties properties = new IdpBearerProperties();
        properties.setAppId("  explicit-app  ");
        MockEnvironment environment = new MockEnvironment()
                .withProperty("my.oauth2.client.client-id", "client-from-oauth2");

        Assert.assertEquals("显式 app-id 优先（并去空白）", "explicit-app",
                IdpBearerAutoConfiguration.resolveExpectedAppId(properties, environment));
    }

    @Test
    public void expectedAppIdFallsBackToOAuth2ClientId() {
        IdpBearerProperties properties = new IdpBearerProperties();
        MockEnvironment environment = new MockEnvironment()
                .withProperty("my.oauth2.client.client-id", "epc-ai");

        Assert.assertEquals("未配 app-id ⇒ 自动采用同宿主的 OAuth2 client-id", "epc-ai",
                IdpBearerAutoConfiguration.resolveExpectedAppId(properties, environment));
        Assert.assertNull("两者都缺 ⇒ null（交给 fail-fast）",
                IdpBearerAutoConfiguration.resolveExpectedAppId(properties, new MockEnvironment()));

        properties.setAppId("   ");
        Assert.assertEquals("app-id 空白同样走回退", "epc-ai",
                IdpBearerAutoConfiguration.resolveExpectedAppId(properties, environment));
    }

    @Test
    public void failFastWhenAudienceNotResolvable() {
        try {
            IdpBearerAutoConfiguration.assertAudienceConfigured(null);
            Assert.fail("解析不出期望接入方 ⇒ 装配期必须失败（不允许静默跳过受众校验）");
        } catch (IllegalStateException expected) {
            Assert.assertTrue("错误信息必须给出两种正确配法",
                    expected.getMessage().contains("app-id")
                            && expected.getMessage().contains("my.oauth2.client.client-id"));
        }
        IdpBearerAutoConfiguration.assertAudienceConfigured("epc-ai"); // 不抛
    }

    @Test
    public void failFastWhenEnabledWithoutAnyKeySource() {
        IdpBearerProperties properties = new IdpBearerProperties();
        properties.setEnabled(true);
        try {
            IdpBearerAutoConfiguration.assertKeySourceConfigured(properties);
            Assert.fail("enabled=true 但既无 server-base-url 也无静态公钥 ⇒ 装配期必须失败");
        } catch (IllegalStateException expected) {
            Assert.assertTrue("错误信息必须给出两种正确配法",
                    expected.getMessage().contains("server-base-url")
                            && expected.getMessage().contains("public-key-base64"));
        }
    }

    @Test
    public void keySourceConfiguredPassesFailFast() {
        IdpBearerProperties withBaseUrl = new IdpBearerProperties();
        withBaseUrl.setServerBaseUrl("http://inkhub.test/inkhub");
        IdpBearerAutoConfiguration.assertKeySourceConfigured(withBaseUrl); // 不抛

        IdpBearerProperties withStatic = new IdpBearerProperties();
        withStatic.setPublicKeyBase64("custom-static-key");
        IdpBearerAutoConfiguration.assertKeySourceConfigured(withStatic); // 不抛
    }
}