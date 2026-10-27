package com.bidr.authorization.controller.login;

import com.bidr.authorization.annotation.auth.Auth;
import com.bidr.authorization.annotation.auth.AuthNone;
import com.bidr.authorization.constants.SsoConfigKeys;
import com.bidr.authorization.service.sso.SsoIntrospectTicketRes;
import com.bidr.authorization.service.sso.SsoIntrospectTicketService;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;

import javax.servlet.http.HttpServletRequest;
import java.util.Map;

import org.apache.commons.lang3.StringUtils;

/**
 * M23 introspect-ticket 端点（core 纯加法，决策稿 §七）。
 *
 * <p>子系统拿 SSO JWT 票来换：用户身份 + 租户列表 + 应用功能集。
 * 入参 { id_token, app_code, tenant_code? }；公钥从 yml `sso.jwt.public-key` 读（仅子系统/墨枢验票侧声明）。</p>
 *
 * <p>🔴 独立 bean 名 + 独立路径 `/web/sso/introspect-ticket`，不与 {@code TokenIntrospectController} 的
 * `/web/sso/introspect` 路径重叠。</p>
 *
 * @author sharp
 * @since 2026-10-26
 */
@Api(tags = "M23 - introspect-ticket")
@RestController("IntrospectTicketController")
@RequestMapping(path = "/web/sso")
@RequiredArgsConstructor
public class IntrospectTicketController {

    private final SsoIntrospectTicketService introspectTicketService;

    @Value("${" + SsoConfigKeys.JWT_PUBLIC_KEY + ":}")
    private String jwtPublicKey;

    @Value("${" + SsoConfigKeys.JWT_PRIVATE_KEY + ":}")
    private String jwtPrivateKey;

    @Auth(AuthNone.class)
    @ApiOperation(value = "SSO票回查（验票→身份→租户→功能）",
            notes = "body: { id_token, app_code, tenant_code? }；返回身份明细+租户列表+功能集；"
                    + "error 字段非空=验票失败。签发方（墨枢自身）用 private-key 验票；"
                    + "子系统（验票方）用 public-key 验票。两者都不配则无法验票。")
    @PostMapping(value = "/introspect-ticket")
    public SsoIntrospectTicketRes introspectTicket(@RequestBody Map<String, String> body,
                                                    HttpServletRequest request) {
        String idToken = body != null ? body.get("id_token") : null;
        String appCode = body != null ? body.get("app_code") : null;
        String tenantCode = body != null ? body.get("tenant_code") : null;
        // 签发方（墨枢自身）只有 private-key ⇒ 现场派生公钥验签（🔴 不得声明 sso.jwt.public-key，
        // 那会误触发 SsoJwtUserHandler）；子系统形态用 public-key。
        boolean issuerSide = StringUtils.isNotBlank(jwtPrivateKey);
        String verifyKey = issuerSide ? jwtPrivateKey : jwtPublicKey;
        return introspectTicketService.introspect(idToken, verifyKey, issuerSide, appCode, tenantCode);
    }
}
