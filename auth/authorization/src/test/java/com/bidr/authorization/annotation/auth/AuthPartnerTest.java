package com.bidr.authorization.annotation.auth;

import com.bidr.authorization.constants.err.AccountErrCode;
import com.bidr.authorization.constants.token.TokenItem;
import com.bidr.authorization.dao.entity.AcPartner;
import com.bidr.authorization.dao.entity.AcPartnerHistory;
import com.bidr.authorization.dao.repository.AcPartnerHistoryService;
import com.bidr.authorization.dao.repository.AcPartnerService;
import com.bidr.kernel.constant.dict.common.ActiveStatusDict;
import com.bidr.kernel.exception.ServiceException;
import com.bidr.kernel.utils.Base64Util;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

/**
 * {@link AuthPartner} 机器凭证解析的健壮性守卫（T3.3a/T3.1 链接线面）。
 *
 * <p>钉四件事：①缺分隔符（`appKey` 无冒号）不得抛 {@code ArrayIndexOutOfBoundsException} 变成 500，
 * 必须给 {@code AC_PARTNER_SECRET_INVALID} 业务错误；②空秘钥（`appKey:`）同样按秘钥不匹配拒；
 * ③鉴权在 validate 阶段就拒时，{@code completion} 取不到 history 必须安全早退（不得 NPE）；
 * ④status 闸（方案B）：停用 partner 必须以 {@code AC_PARTNER_NOT_AVAILABLE} fail-closed 拒，
 * 且 inline 补写审计（validate 抛异常后 completion 不会跑）；启用 partner 校验通过后
 * extraData 必须烤入 PARTNER_ID（开放口控制器按它解析子系统归属）。</p>
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
        AcPartner partner = activePartner();
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

    @Test
    public void validateSuccessMarksPlatformTier() {
        // 6.60 真机抓到的缺口：机器凭证上下文不置平台档 ⇒ TenantDataGate 判「已登录无租户非平台」，
        // ac_user 锚点查询被注入 1=0 ⇒ open-api 成员按登录名回填恒空。校验全过后必须 markPlatform
        // （对齐 AuthAdmin：拒绝路径不得标记）。
        AcPartner partner = activePartner();
        org.mockito.Mockito.when(acPartnerService.getByAppKey("epc-ai")).thenReturn(partner);

        Assert.assertFalse("前置：上下文未建立时不得是平台档", com.bidr.authorization.holder.TenantContext.isPlatform());
        authPartner.validate(request("epc-ai:s3cret"));
        Assert.assertTrue("机器凭证校验通过后必须置平台档（TenantDataGate 旁路判据）",
                com.bidr.authorization.holder.TenantContext.isPlatform());
    }

    @Test
    public void validateFailureMustNotMarkPlatform() {
        // 与上一条互为红绿两态：拒绝路径绝不落平台档标记（防线程池残留假平台档）。
        ServiceException ex = Assert.assertThrows(ServiceException.class,
                () -> authPartner.validate(request("epc-ai")));
        Assert.assertSame(AccountErrCode.AC_PARTNER_SECRET_INVALID, ex.getErrCode());
        Assert.assertFalse("拒绝路径不得置平台档", com.bidr.authorization.holder.TenantContext.isPlatform());
    }

    @Test
    public void deactivatedPartnerRejectedFailClosedWithInlineAudit() {
        // 方案B status 闸：停用 partner（DEACTIVATE=0）即使秘钥比对会通过也必须拒，
        // 且拒绝先于秘钥比对发生——以 1027 直陈「渠道不可用」，不用 1030 掩盖停用事实。
        AcPartner partner = activePartner();
        partner.setStatus(ActiveStatusDict.DEACTIVATE.getValue());
        org.mockito.Mockito.when(acPartnerService.getByAppKey("epc-ai")).thenReturn(partner);

        ServiceException ex = Assert.assertThrows(ServiceException.class,
                () -> authPartner.validate(request("epc-ai:s3cret")));

        Assert.assertSame("停用 partner 必须以渠道不可用拒（fail-closed）",
                AccountErrCode.AC_PARTNER_NOT_AVAILABLE, ex.getErrCode());
        // validate 抛异常后 completion 不会执行（history 属性未挂、判空早退）——
        // 停用拒绝若不 inline 落账，这一枪就无痕：此处钉「审计行已 insert」。
        ArgumentCaptor<AcPartnerHistory> captor = ArgumentCaptor.forClass(AcPartnerHistory.class);
        verify(historyService).insert(captor.capture());
        AcPartnerHistory audit = captor.getValue();
        Assert.assertEquals("审计行须带 partner 的 appKey", "epc-ai", audit.getAppKey());
        Assert.assertEquals("审计行 status 填拒绝语义（ServiceException → HTTP 200）",
                Integer.valueOf(200), audit.getStatus());
        Assert.assertNotNull("审计行 responseAt 必须落拒绝时间", audit.getResponseAt());
        Assert.assertNotNull("审计行 requestAt 必须落请求时间", audit.getRequestAt());
        Assert.assertTrue("审计行 message 须含拒绝错误码（1027）: " + audit.getMessage(),
                audit.getMessage() != null && audit.getMessage().contains("1027"));
        // 停用拒绝同样不得置平台档（与 validateFailureMustNotMarkPlatform 同口径）
        Assert.assertFalse("停用拒绝路径不得置平台档",
                com.bidr.authorization.holder.TenantContext.isPlatform());
    }

    @Test
    public void nullStatusPartnerAlsoRejectedFailClosed() {
        // 脏数据防线：status 为 null（理论不该有，DDL NOT NULL）按非启用处理，
        // 闸是 fail-closed 的——「查不到启用标记」绝不放行。
        AcPartner partner = activePartner();
        partner.setStatus(null);
        org.mockito.Mockito.when(acPartnerService.getByAppKey("epc-ai")).thenReturn(partner);

        ServiceException ex = Assert.assertThrows(ServiceException.class,
                () -> authPartner.validate(request("epc-ai:s3cret")));

        Assert.assertSame(AccountErrCode.AC_PARTNER_NOT_AVAILABLE, ex.getErrCode());
        verify(historyService).insert(org.mockito.ArgumentMatchers.any(AcPartnerHistory.class));
    }

    @Test
    public void activePartnerBakesPartnerIdIntoExtraData() {
        // 方案B：PARTNER_ID 烤入上下文——开放口控制器按 ac_subsystem.partner_id 解析
        // 菜单归属应用（app_code），不烤就得每请求再查一次 ac_partner。
        AcPartner partner = activePartner();
        org.mockito.Mockito.when(acPartnerService.getByAppKey("epc-ai")).thenReturn(partner);

        authPartner.validate(request("epc-ai:s3cret"));

        java.util.Map<String, Object> extra = com.bidr.authorization.holder.AccountContext.getExtraData();
        Assert.assertEquals("PARTNER_ID 必须烤入 extraData（ac_partner.id）",
                Long.valueOf(7L), extra.get(TokenItem.PARTNER_ID.name()));
        Assert.assertEquals("PLATFORM 烤入不得回退", "epc", extra.get(TokenItem.PLATFORM.name()));
        Assert.assertEquals("APP_KEY 烤入不得回退", "epc-ai", extra.get(TokenItem.APP_KEY.name()));
    }

    @org.junit.After
    public void clearThreadContext() {
        // 线程池串档防线（TenantContextTest 同款口径）：测试自己清自己。
        com.bidr.authorization.holder.TenantContext.remove();
        com.bidr.authorization.holder.AccountContext.remove();
    }

    /** 启用态 partner 样本：id/appKey/appSecret/platform 齐备，status=ACTIVATE */
    private static AcPartner activePartner() {
        AcPartner partner = new AcPartner();
        partner.setId(7L);
        partner.setAppKey("epc-ai");
        partner.setAppSecret("s3cret");
        partner.setPlatform("epc");
        partner.setStatus(ActiveStatusDict.ACTIVATE.getValue());
        return partner;
    }

    private static MockHttpServletRequest request(String apiKey) {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("x-api-key", Base64Util.encode(apiKey));
        return request;
    }
}