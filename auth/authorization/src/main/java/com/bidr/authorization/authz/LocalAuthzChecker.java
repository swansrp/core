package com.bidr.authorization.authz;

import com.bidr.authorization.service.permit.PermitService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Title: LocalAuthzChecker
 * Description: {@link AuthzChecker} SPI 的 server 本地实现（T2.3c 棒1 D 节）——
 * 委托 {@code PermitService.isAdmin()}（bean 名 {@code permitService} 不得改，kernel 反射按名调用），
 * 判定行为与上提前 {@code AuthAdmin} 直连 PermitService 时完全同一实现：
 * AccountContext 优先→token ROLE_MAP 回落 × sys_config ACCOUNT_ADMIN_ROLE_ID。
 *
 * <p>消费方：client 侧 {@code AuthAdmin} 经 {@code ObjectProvider<AuthzChecker>} 取本 bean
 * （本 bean 在场 ⇒ SPI 不缺失，fail-closed 分支不触发）。远端形态（epc 侧）不装配本类，
 * 由棒4 RemoteAuthzChecker 走 /authz 端点。</p>
 *
 * @author coder (棒1, T2.3c 上提 2026-11-03)
 * @since 2026-11-03
 */
@Component
@RequiredArgsConstructor
public class LocalAuthzChecker implements AuthzChecker {

    private final PermitService permitService;

    @Override
    public boolean isAdmin() {
        return permitService.isAdmin();
    }
}
