package com.bidr.authorization.service.oauth2;

import com.bidr.authorization.dao.entity.AcPartner;
import com.bidr.authorization.dao.entity.AcSsoRedirect;
import com.bidr.authorization.dao.repository.AcSsoRedirectService;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;

import java.util.Collections;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.verifyNoInteractions;

/**
 * Title: OAuth2ClientServiceTest
 * Description: redirect 白名单闸判定矩阵（T1.2 棒 6 的本地/内网豁免腿，2026-11-04 用户拍板）：
 * 登记串仍全串精确命中；localhost／127.0.0.0/8／10.0.0.0/8／::1 且 http(s) ⇒ 免登记放行；
 * 套娃/变体（{@code localhost.evil.com}、{@code 10.evil.com}、十进制整数、八进制前导零、
 * 非 http(s) scheme）一律不豁免、照走白名单比对。
 *
 * <p>离线腿：仓储全 mock，不打库。</p>
 *
 * @author Sharp
 * @since 2026-11-04
 */
public class OAuth2ClientServiceTest {

    private static final long PARTNER_ID = 9L;
    private static final String PARTNER_APP_KEY = "demo-app";
    private static final String REGISTERED = "https://sub.example.com/oauth2/callback";

    private AcSsoRedirectService acSsoRedirectService;
    private OAuth2ClientService clientService;
    private AcPartner partner;

    @Before
    public void setUp() {
        AcSsoRedirect row = new AcSsoRedirect();
        row.setPartnerId(PARTNER_ID);
        row.setRedirectUri(REGISTERED);
        acSsoRedirectService = Mockito.mock(AcSsoRedirectService.class);
        Mockito.when(acSsoRedirectService.listValid(PARTNER_ID))
                .thenReturn(Collections.singletonList(row));
        clientService = new OAuth2ClientService(null, acSsoRedirectService);

        partner = new AcPartner();
        partner.setId(PARTNER_ID);
        partner.setAppKey(PARTNER_APP_KEY);
    }

    /** 闸门判定：通过 ⇒ true，拒绝（ServiceException）⇒ false */
    private boolean allows(String redirectUri) {
        try {
            clientService.checkRedirectUriRegistered(partner, redirectUri);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    // ==================== 登记串：全串精确 ====================

    @Test
    public void registeredExactStringPasses() {
        assertTrue(allows(REGISTERED));
        assertTrue("trim 后等值仍命中", allows("  " + REGISTERED + "  "));
    }

    @Test
    public void unregisteredPublicHostRejected() {
        assertFalse(allows("https://sub.example.com/oauth2/callback2"));
        assertFalse(allows("https://evil.example.com/oauth2/callback"));
        assertFalse(allows("http://192.168.1.5:8080/cb"));
    }

    // ==================== 本地/内网豁免 ====================

    @Test
    public void localhostAnyPortAndSchemePasses() {
        assertTrue(allows("http://localhost:5173"));
        assertTrue(allows("http://localhost:8083/callback"));
        assertTrue(allows("http://LOCALHOST:5174/oauth2/callback"));
        assertTrue(allows("https://localhost/cb"));
        verifyNoInteractions(acSsoRedirectService);
    }

    @Test
    public void loopbackRangePasses() {
        assertTrue(allows("http://127.0.0.1:5174"));
        assertTrue(allows("http://127.0.0.2:8080/cb"));
        assertTrue(allows("https://127.99.1.2/callback"));
        assertTrue(allows("http://127.0.0.1"));
    }

    @Test
    public void tenRangePasses() {
        assertTrue(allows("http://10.3.4.60:8081"));
        assertTrue(allows("https://10.0.0.1/callback"));
        assertTrue(allows("http://10.255.255.255:9999/oauth2/callback"));
    }

    @Test
    public void ipv6LoopbackPasses() {
        assertTrue(allows("http://[::1]:8080/cb"));
        assertTrue(allows("http://[0:0:0:0:0:0:0:1]/cb"));
    }

    // ==================== 变体不豁免（照走白名单 ⇒ 未登记即拒） ====================

    @Test
    public void hostSpoofVariantsRejected() {
        assertFalse(allows("http://localhost.evil.com/cb"));
        assertFalse(allows("http://127.0.0.1.evil.com/cb"));
        assertFalse(allows("http://10.evil.com/cb"));
        assertFalse(allows("http://10.1.2.3.evil.com/cb"));
    }

    @Test
    public void nonHttpSchemeRejected() {
        assertFalse(allows("ftp://10.1.2.3/cb"));
        assertFalse(allows("ftp://localhost/cb"));
        assertFalse(allows("file://127.0.0.1/cb"));
    }

    @Test
    public void malformedIpVariantsRejected() {
        assertFalse("越界八位组", allows("http://10.0.0.256/cb"));
        assertFalse("越界八位组", allows("http://127.0.0.999/cb"));
        assertFalse("十进制整数形态", allows("http://2130706433/cb"));
        assertFalse("八进制前导零形态", allows("http://010.1.2.3/cb"));
    }

    @Test
    public void blankOrMalformedRejected() {
        assertFalse(allows(null));
        assertFalse(allows("   "));
        assertFalse(allows("not-a-uri"));
    }
}
