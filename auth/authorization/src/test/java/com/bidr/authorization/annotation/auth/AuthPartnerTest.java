package com.bidr.authorization.annotation.auth;

import com.bidr.authorization.constants.err.AccountErrCode;
import com.bidr.authorization.dao.entity.AcPartner;
import com.bidr.authorization.dao.repository.AcPartnerHistoryService;
import com.bidr.authorization.dao.repository.AcPartnerService;
import com.bidr.kernel.exception.ServiceException;
import com.bidr.kernel.utils.Base64Util;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Date;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

/**
 * {@link AuthPartner} 机器凭证解析的健壮性守卫（T3.3a/T3.1 链接线面）。
 *
 * <p>钉三件事：①缺分隔符（`appKey` 无冒号）不得抛 {@code ArrayIndexOutOfBoundsException} 变成 500，
 * 必须给 {@code AC_PARTNER_SECRET_INVALID} 业务错误；②空秘钥（`appKey:`）同样按秘钥不匹配拒；
 * ③鉴权在 validate 阶段就拒时，{@code completion} 取不到 history 必须安全早退（不得 NPE）。</p>
 *
 * @author sharp
 * @since 2026-11-04
 */
public class AuthPartnerTest {

    private AcPartnerService acPartnerService;
    private AcPartnerHistoryService historyService;
    private AuthPartner authPartner;

    @Before
    public void setUp() {
        acPartnerService = mock(AcPartnerService.class);
        historyService = mock(AcPartnerHistoryService.class);
        authPartner = new AuthPartner(acPartnerService, historyService);
    }

    @Test
    public void missingSeparatorRejectedWithoutTouchingRepo() {
        ServiceException ex = Assert.assertThrows(ServiceException.class,
                () -> authPartner.validate(request("epc-ai")));

        Assert.assertSame("缺冒号必须是业务错误，不是 AIOOBE→500",
                AccountErrCode.AC_PARTNER_SECRET_INVALID, ex.getErrCode());
        verifyNoInteractions(acPartnerService);
    }

    @Test
    public void emptySecretRejectedAsSecretInvalid() {
        AcPartner partner = new AcPartner();
        partner.setAppKey("epc-ai");
        partner.setAppSecret("s3cret");
        partner.setPlatform("epc");
        org.mockito.Mockito.when(acPartnerService.getByAppKey("epc-ai")).thenReturn(partner);

        ServiceException ex = Assert.assertThrows(ServiceException.class,
                () -> authPartner.validate(request("epc-ai:")));

        Assert.assertSame("空秘钥必须按秘钥不匹配拒",
                AccountErrCode.AC_PARTNER_SECRET_INVALID, ex.getErrCode());
        verify(acPartnerService).getByAppKey("epc-ai");
    }

    @Test
    public void completionWithoutHistoryDoesNotThrow() {
        authPartner.completion(new MockHttpServletRequest(), new MockHttpServletResponse());
        verifyNoInteractions(historyService);
    }

    private static MockHttpServletRequest request(String apiKey) {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("x-api-key", Base64Util.encode(apiKey));
        return request;
    }
}