package com.bidr.authorization.client.oauth2;

import org.junit.Assert;
import org.junit.Test;

import java.security.KeyFactory;
import java.security.PublicKey;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;

/**
 * {@link IdpBearerProperties} 内置公钥默认值守卫（2026-11-04 对账单 #16 方案 A 落地）。
 *
 * <p>动机：接入方此前要在自己档 yml 手抄一整串 392 字符公钥（epc 三档各一份），
 * 抄错一个字符＝验签全挂，且报错完全不指向公钥（表现为 token 验签失败/401）。改为在
 * starter 里内置墨枢标准公钥（公开信息，JWKS 本就公开发布），接入方引模块即零配置。</p>
 *
 * <p>本守卫钉三件事：①默认值可用（非空/长度 392/可被 JDK KeyFactory 解析为 RSA 公钥）；
 * ②显式配置可覆盖（多平台/轮换场景）；③**enabled 仍默认 false**——内置了公钥不等于自动开腿，
 * 宿主必须显式 {@code my.idp.bearer.enabled=true} 才装配（否则任何引入本模块的宿主都会
 * 悄悄多一条验签入口，属安全面变化）。</p>
 *
 * <p>与 {@code sso-key/pub-oneline.txt} 的逐字比对在 InkHub 仓的
 * {@code SsoConfigSurfaceConventionTest}（跨仓可移植性考虑：本模块会在 epc 等消费方仓内编译，
 * 那些仓没有 sso-key 目录，故此处只验"可解析"，值一致性在正源仓验）。</p>
 *
 * @author sharp
 * @since 2026-11-04
 */
public class IdpBearerPropertiesTest {

    @Test
    public void defaultPublicKeyIsUsableRsaPublicKey() throws Exception {
        String key = new IdpBearerProperties().getPublicKeyBase64();

        Assert.assertNotNull("默认值不得为 null", key);
        Assert.assertEquals("默认值长度（X.509 DER base64）应为 392", 392, key.length());
        Assert.assertTrue("必须以标准 SPKI 头起始（MIIBIjANBgkq…）", key.startsWith("MIIBIjANBgkq"));

        PublicKey publicKey = KeyFactory.getInstance("RSA")
                .generatePublic(new X509EncodedKeySpec(Base64.getDecoder().decode(key)));
        Assert.assertEquals("RSA", publicKey.getAlgorithm());
    }

    @Test
    public void explicitConfigurationOverridesDefault() {
        IdpBearerProperties properties = new IdpBearerProperties();
        properties.setPublicKeyBase64("custom-key-override");
        Assert.assertEquals("custom-key-override", properties.getPublicKeyBase64());
    }

    @Test
    public void enabledStillDefaultsToFalseEvenWithBuiltInKey() {
        IdpBearerProperties properties = new IdpBearerProperties();
        Assert.assertFalse("内置公钥不等于自动开腿：宿主必须显式 my.idp.bearer.enabled=true 才装配"
                        + "（门在 IdpBearerAutoConfiguration 的 @ConditionalOnProperty）",
                properties.isEnabled());
        // 注：IdpBearerVerifier#enabled() 的语义是「公钥就绪」，不是装配开关——bean 只在
        // @ConditionalOnProperty(enabled=true) 通过后才存在，故这里恒 true 是预期；
        // 装配门槛由上面那条断言（properties.isEnabled()）与装配类共同保证。
        Assert.assertTrue("公钥就绪判据：内置默认值使验签器认为可验（真正的门是装配开关）",
                new IdpBearerVerifier(properties).enabled());
    }
}