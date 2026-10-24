package com.bidr.authorization.audit;

/**
 * 审计分组键解析器 SPI（core 定义、宿主装实现——默认无实现＝group 为 null）。
 *
 * <p><b>动机</b>：活动流水类审计（如空间内发生的事）需要一个"归属分组"维度（space_id/
 * project_id 类列），而该值通常住在线程上下文（如请求头的 {@code X-Space-Id} 解析结果）——
 * core 不知道宿主的上下文载体，由宿主注册本实现从自家 ThreadLocal 取。切面解析优先级：
 * {@link AuditContext#GROUP_OVERRIDE} 运行时覆写 ＞ {@code @AuditTrail#group} SpEL ＞
 * 本解析器 ＞（默认拦截模式）绑定入参/路径模板变量兜底。</p>
 *
 * <p><b>实现纪律</b>：实现会在业务方法返回后的同线程同步段被调用，勿做重活；取不到返回
 * null（切面继续走下一腿），自身抛错按 null 处理（吞并，不影响业务）。</p>
 *
 * @author sharp (audit 框架增强：默认拦截模式 2026-10-24)
 */
public interface AuditTrailGroupResolver {

    /**
     * 解析当前线程的审计分组键。
     *
     * @return 分组键（如空间 id 的字符串形态）；无分组上下文返回 null
     */
    String resolveGroup();
}
