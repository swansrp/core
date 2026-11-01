package com.bidr.authorization.tenant;

import com.bidr.authorization.config.TenantProperties;
import com.bidr.hub.spi.TenantResolver;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Optional;

/**
 * Title: FixedTenantResolver
 * Description: {@link TenantResolver} 的内核默认实现——静态读 {@code identity.tenant.code}
 * （{@link TenantProperties}），全库单一租户形态（"配置即种子"模型）下的租户归属正源。
 *
 * <p>语义（SPI 契约钉死）：</p>
 * <ul>
 *   <li>配置空白（null/空串/纯空白）⇒ {@link Optional#empty()} ＝ 非租户形态/未配置，
 *       身份创建点据此不赋租户——不是错误；</li>
 *   <li>配置有值 ⇒ 返回去空白后的 code；code 是否真实在库由 TenantSeeder 保证（本类不查库）。</li>
 * </ul>
 *
 * <p>装配形态：{@code @Component}——本默认实现恒注册。🔴 棒7 启动证据（2026-11-02 02:05 boot
 * APPLICATION FAILED：CreateUserServiceImpl 注入 TenantResolver 无候选）：原
 * {@code @ConditionalOnMissingBean(TenantResolver.class)} 在组件扫描面（非 auto-configuration 面）
 * 未能把本默认实现注册进容器——一旦 7fff12b2 让身份创建点真正注入该类型即显形。仓内亦无第二实现，
 * 客户桥（pack-bidr / pack-plsintec）如未来自带租户推导实现，须走显式声明（配置类）并在该处
 * 收编默认实现（机制登记，防静默双实现）。</p>
 *
 * @author Sharp
 * @since 2026-11-01
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class FixedTenantResolver implements TenantResolver {

    private final TenantProperties tenantProperties;

    @Override
    public Optional<String> resolveTenantCode(String username) {
        String code = tenantProperties.getCode();
        if (code == null || code.trim().isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(code.trim());
    }
}
