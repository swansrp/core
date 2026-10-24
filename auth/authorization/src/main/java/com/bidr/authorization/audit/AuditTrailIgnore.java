package com.bidr.authorization.audit;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 审计豁免注解：只作用于<b>默认拦截模式</b>（{@code bidr.audit-trail.auto.enabled=true} 时
 * {@code AuditTrailAutoAspect} 的拦截面）——标注的方法（及类）不产审计行。
 * 方法级优先压类级。<b>对显式 {@link AuditTrail @AuditTrail} 注解方法无效</b>（显式标注即明确意图，
 * 不受豁免约束）。
 *
 * <p>默认拦截模式的"豁免"与显式标注模式的"不标不记"互补：auto 模式下宿主声明拦截面后，
 * 读写混合、高频无语义（进度查询/心跳）等端点用本注解逐个摘除。</p>
 *
 * @author sharp (audit 框架增强：默认拦截模式 2026-10-24)
 */
@Documented
@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
public @interface AuditTrailIgnore {
}
