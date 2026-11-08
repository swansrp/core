package com.bidr.authorization.client.oauth2;

import com.bidr.authorization.constants.common.RequestConst;
import com.bidr.kernel.utils.Base64Util;
import org.junit.Test;
import org.springframework.http.HttpHeaders;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;

/**
 * Title: PartnerCredentialProviderTest
 * Description: 开放口机器凭据腿的线口径钉（方案B 棒4）——
 * {@code x-api-key: Base64(appKey:appSecret)}（Base64Util urlSafe，与 provider 侧 AuthPartner
 * 验签 round-trip 同口径），<b>不发 Bearer</b>（机器凭据腿≠用户身份腿，Authorization 头不碰）。
 *
 * @author coder (棒4, 方案B 开放口三层沉淀 2026-11-07)
 * @since 2026/11/07
 */
public class PartnerCredentialProviderTest {

    /** 腿①：头值＝Base64(appKey:secret)（urlSafe 编码口径） */
    @Test
    public void apply写入xApiKey_值等于Base64凭据对() {
        OAuth2ClientProperties properties = new OAuth2ClientProperties();
        properties.setClientId("epc-ai");
        properties.setClientSecret("partner-secret-1");
        HttpHeaders headers = new HttpHeaders();

        new PartnerCredentialProvider(properties).apply(headers);

        assertEquals(Base64Util.encode("epc-ai:partner-secret-1"),
                headers.getFirst(RequestConst.X_API_KEY));
        // round-trip 自证：provider 侧 Base64Util.decode 双解码兜底应还原凭据对
        assertEquals("epc-ai:partner-secret-1",
                Base64Util.decode(headers.getFirst(RequestConst.X_API_KEY)));
    }

    /** 腿②：不发 Bearer——机器凭据腿不产 access_token、不碰 Authorization 头 */
    @Test
    public void apply不写Authorization头_不发Bearer() {
        OAuth2ClientProperties properties = new OAuth2ClientProperties();
        properties.setClientId("epc-ai");
        properties.setClientSecret("partner-secret-1");
        HttpHeaders headers = new HttpHeaders();

        new PartnerCredentialProvider(properties).apply(headers);

        assertFalse("机器凭据腿不得携带 Authorization（Bearer JWT 是另一条腿）",
                headers.containsKey(HttpHeaders.AUTHORIZATION));
        assertFalse("机器凭据腿不得产出 access_token 头",
                headers.containsKey("access_token"));
        assertNull(headers.getFirst(HttpHeaders.AUTHORIZATION));
    }

    /** 腿③：只 set 自己的头——既有头不清除（OpenApiAuthProvider「不动既有头」约定） */
    @Test
    public void apply不动既有头() {
        OAuth2ClientProperties properties = new OAuth2ClientProperties();
        properties.setClientId("epc-ai");
        properties.setClientSecret("partner-secret-1");
        HttpHeaders headers = new HttpHeaders();
        headers.set("X-Custom", "keep-me");

        new PartnerCredentialProvider(properties).apply(headers);

        assertEquals("keep-me", headers.getFirst("X-Custom"));
    }
}
