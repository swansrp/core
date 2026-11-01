package com.bidr.authorization.tenant;

import com.bidr.authorization.config.TenantProperties;
import com.bidr.hub.spi.TenantResolver;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
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
 * <p>装配形态：{@code @Component + @ConditionalOnMissingBean(TenantResolver.class)}——
 * 客户桥（pack-bidr / pack-plsintec，棒3 接线）如自带租户推导实现，同容器注册即顶替本默认实现；
 * 注意该条件注解在组件扫描面属"注册序敏感"形态（非 auto-configuration 面），桥实现须走
 * 显式声明（配置类）而非组件扫描，以确保默认实现先评估（机制登记，防静默双实现）。</p>
 *
 * @author Sharp
 * @since 2026-11-01
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnMissingBean(TenantResolver.class)
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
