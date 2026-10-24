package com.bidr.authorization.audit;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 审计注解：标注在业务方法上，方法成功返回后由 {@link AuditTrailAspect} 组装
 * {@link AuditRecord} 并写入 {@link AuditTrailSink}（SPI——core 定义、业务装实现）。
 * <p>审计失败（sink 缺失/写入抛错/SpEL 求值失败）一律吞并：不回滚业务、不改变返回值，
 * 走 log.error 高等级＋运维告警事件（{@code ServiceExceptionEvent}，ERROR 级）——裁定 2。</p>
 *
 * <p><b>SpEL 可用变量</b>（{@code bizId} 整体是表达式；{@code reason} 是 {@code #{...}} 模板）：
 * <ul>
 *   <li>{@code #参数名}——方法入参按名引用（编译带 debug 信息时经参数名发现器解析；
 *       解析不到时可用 {@code #a0}..{@code #aN} 按位兜底）；</li>
 *   <li>{@code #result}——方法返回值（@Around proceed 之后求值，非 null 时可用）；</li>
 *   <li>{@code #args}——入参数组（按位访问：{@code #args[0]}）；</li>
 *   <li>{@code #ctx}——{@link AuditContext} 快照（Map）：方法体内 put 的运行时上下文，
 *       如 {@code #ctx.get('orderNo')}。</li>
 * </ul>
 * 示例：
 * <pre>{@code
 * @AuditTrail(bizType = "merchant_profile", action = "change", field = "grade",
 *             bizId = "#profileId",
 *             reason = "调整等级 #{#profileId}：#{#ctx.get('oldValue')} → #{#result}")
 * public String changeGrade(Long profileId) { ... }
 * }</pre>
 *
 * <p><b>old_value / new_value 列</b>：注解不设属性——方法体内用
 * {@link AuditContext#OLD_VALUE}/{@link AuditContext#NEW_VALUE} 两个约定键
 * {@link AuditContext#put} 进上下文，切面自动并入审计行（查库才知道旧值的场景由此覆盖）。</p>
 *
 * <p>审计只记"成功发生的动作"：方法抛异常则本次不产审计行（业务异常原样上抛，审计链路不介入）。</p>
 *
 * @author coder (棒2, 租户豁免与审计框架治理 2026-10-24)
 */
@Documented
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface AuditTrail {

    /**
     * 业务域裸码（M-8 正源值域：merchant_profile / publish_catalog / base_price /
     * merchant_apply / 各单据——biz_erp_audit_log.biz_type 列）。
     */
    String bizType();

    /**
     * 动作（biz_erp_audit_log.action 列值域：create / disable / enable / change / transition）。
     */
    String action();

    /**
     * 改的是哪一列（biz_erp_audit_log.field_name 列；无列语义的动作留空）。
     */
    String field() default "";

    /**
     * 业务对象标识（biz_erp_audit_log.biz_id 列；单据用 doc_no，主体用 id）。
     * <b>整体是 SpEL 表达式</b>（可用变量见类 javadoc），求值结果转 String；
     * 求值失败＝本次审计行丢弃＋log.error＋告警（不炸业务）。
     */
    String bizId() default "";

    /**
     * 原因（biz_erp_audit_log.reason 列）。<b>{@code #{...}} 模板＋SpEL 插值</b>
     * （Spring TemplateParserContext，无表达式的纯文本原样落列）。
     */
    String reason() default "";

    /**
     * 操作身份字面量（biz_erp_audit_log.operator_identity 列值域：staff / merchant / platform；
     * 操作人主键取 {@code AccountContext.getOperator()}，注解不设属性）。
     */
    String operatorIdentity() default "";
}
