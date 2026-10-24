package com.bidr.audit;

/**
 * 审计落库 SPI（core 定义、业务装实现——{@code LoginFillTokenInf}/{@code
 * TenantExemptIdentityFill} 同族先例）。
 *
 * <p><b>消费点</b>：{@link AuditTrailAspect} 在注解方法成功返回后收集全部本接口实现并逐个
 * {@link #write}；core 侧以 {@code ObjectProvider} 可选收集——宿主无实现＝审计行丢弃＋
 * log.error＋运维告警（不抛、不影响业务）。ERP 侧由 erp-common 装实现
 * （写 biz_erp_audit_log，跨租户/无租户豁免内聚在该实现里——core 零 biz_erp_* 依赖）。</p>
 *
 * <p><b>实现方契约</b>：本方法内抛出的任何异常都会被切面吞并（log.error＋告警、业务不受影响），
 * 但会丢审计行——实现方应自行兜好落库故障；本方法在业务方法返回后的同线程同步段执行，
 * 勿做重活/阻塞，勿读取 ThreadLocal 之外的请求态假设。</p>
 *
 * @author coder (棒2, 租户豁免与审计框架治理 2026-10-24)
 */
public interface AuditTrailSink {

    /**
     * 写一条审计行。同一方法多个实现时按 Spring @Order 顺序逐个调用，
     * 单个实现失败不影响其余实现继续写。
     *
     * @param record 审计行（不可变）
     */
    void write(AuditRecord record);
}
