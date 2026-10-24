package com.bidr.audit;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 默认拦截模式装配：{@code bidr.audit-trail.auto.enabled=true} 才装配
 * {@link AuditTrailAutoAdvisor}（PointcutAdvisor——宿主既有 AOP 自动代理基建
 * {@code AbstractAdvisorAutoProxyCreator} 按切点 classFilter 精确匹配建代理）。
 * 默认 false＝零 bean 装配、宿主零行为变化（ERP 等纯显式注解宿主不受影响）。
 *
 * <p>注册形态沿 core 既有约定：{@code @Configuration} 落
 * {@code com.bidr.audit}，由宿主 {@code BaseApplication} 的
 * {@code @ComponentScan("com.bidr")} 扫到，无需 spring.factories。</p>
 *
 * @author sharp (audit 框架增强：默认拦截模式 2026-10-24)
 */
@Configuration
public class AuditTrailAutoConfiguration {

    @Bean
    @ConditionalOnProperty(prefix = "bidr.audit-trail.auto", name = "enabled", havingValue = "true")
    public AuditTrailAutoAdvisor auditTrailAutoAdvisor(
            AuditTrailProperties properties,
            ObjectProvider<AuditTrailSink> sinkProvider,
            ApplicationEventPublisher eventPublisher,
            ObjectProvider<AuditTrailSuccessPolicy> successPolicies,
            ObjectProvider<AuditTrailGroupResolver> groupResolvers) {
        return new AuditTrailAutoAdvisor(properties.getAuto(),
                new AuditTrailWriter(sinkProvider, eventPublisher), successPolicies, groupResolvers);
    }
}
