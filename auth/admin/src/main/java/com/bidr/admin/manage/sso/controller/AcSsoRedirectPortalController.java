package com.bidr.admin.manage.sso.controller;

import com.bidr.admin.manage.sso.service.AcSsoRedirectPortalService;
import com.bidr.admin.manage.sso.vo.AcSsoRedirectVO;
import com.bidr.authorization.annotation.auth.Auth;
import com.bidr.authorization.annotation.auth.AuthAdmin;
import com.bidr.authorization.dao.entity.AcSsoRedirect;
import com.bidr.kernel.controller.BaseAdminController;
import com.bidr.kernel.service.PortalCommonService;
import com.bidr.platform.config.portal.AdminPortal;
import io.swagger.annotations.Api;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Title: AcSsoRedirectPortalController
 * Description: SSO 回调白名单登记面（框架通用 Portal，add/query/delete 端点全由 BaseAdminController 提供）。
 * <p>🔴 类上必须挂 {@link Auth}{@code (AuthAdmin.class)}：{@code AuthInterceptor.validateAuth} 在方法/类都没有
 * {@code @Auth} 时回落 {@code AuthLogin}＝<b>只验登录不验角色</b>，而同族 admin Portal 控制器普遍零 {@code @Auth}。
 * 本表决定"SSO 票发往哪个地址"＝出票链的信任锚，写端点登录即达等于任何账号都能把自己家的票拐到外部地址，
 * 故比家族现状更严：只有系统管理员可读写（机制取证＝M-26）。</p>
 * Copyright: Copyright (c) 2026 Company: Bidr Ltd.
 *
 * @author Sharp
 * @since 2026-10-14
 */

@RestController
@RequiredArgsConstructor
@Api(tags = "系统管理 - SSO回调白名单")
@Auth(AuthAdmin.class)
@AdminPortal
@RequestMapping(value = "/web/admin/sso/redirect")
public class AcSsoRedirectPortalController extends BaseAdminController<AcSsoRedirect, AcSsoRedirectVO> {

    private final AcSsoRedirectPortalService acSsoRedirectPortalService;

    @Override
    public PortalCommonService<AcSsoRedirect, AcSsoRedirectVO> getPortalService() {
        return acSsoRedirectPortalService;
    }
}
