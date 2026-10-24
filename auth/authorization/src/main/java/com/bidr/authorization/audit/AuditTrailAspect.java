package com.bidr.authorization.audit;

import com.bidr.authorization.holder.AccountContext;
import com.bidr.authorization.holder.TenantContext;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.expression.spel.support.StandardEvaluationContext;
import org.springframework.stereotype.Component;

import java.lang.reflect.Method;
import java.util.Date;
import java.util.Map;

/**
 * 审计切面（显式注解模式）：拦截 {@link AuditTrail} 注解方法，成功返回后 SpEL 求值组装
 * {@link AuditRecord} 并写入 {@link AuditTrailSink}（core 侧 ObjectProvider 可选收集，
 * 宿主无实现＝行丢弃＋高等级告警——ERP 侧由 erp-common 装实现）。写入段与默认拦截模式
 * （{@link AuditTrailAutoAdvisor}）共用 {@link AuditTrailWriter}。
 *
 * <p><b>异常语义（裁定 2，本切面最高纪律）</b>：审计链路的任何失败——sink 缺失、
 * sink 写入抛错、SpEL 求值失败——一律 catch-all 吞并：绝不影响业务返回值、绝不 rethrow；
 * 同时 log.error 高等级留痕（含 bizType/action/operator 定位信息）并发布
 * {@code ServiceExceptionEvent}（ERROR 级）接入既有运维通知链。</p>
 *
 * <p><b>成功口径（框架增强）</b>：默认只"正常返回"算成功；宿主注册
 * {@link AuditTrailSuccessPolicy} 后，被策略计为成功的异常通道（如 {@code Resp.notice}
 * 族 NoticeException）照常落审计行后<b>原样 rethrow</b>（MVC 异常处理器依赖它产出响应）。
 * 策略未注册/策略抛错＝一律按失败处理（保守：宁可漏记成功行，不误记失败行——失败操作
 * 没有"变更事实"，且校验失败 message 可能含用户输入）。</p>
 *
 * <p><b>求值时机（M-6）</b>：@Around proceed 返回后构造 {@link StandardEvaluationContext}——
 * {@code #result}（返回值）、{@code #参数名}（参数名发现器解析，解析不到时 {@code #a0..#aN}
 * 按位兜底）、{@code #args}（入参数组）、{@code #ctx}（{@link AuditContext} 快照）。
 * 方法抛真业务异常则不产审计行（异常原样上抛，审计侧不介入、不告警——全局异常处理器的职责）。</p>
 *
 * <p><b>上下文收尾</b>：finally 清 {@link AuditContext}（方法级主清理；请求线程侧另有
 * AuthInterceptor afterCompletion 兜底）。pointcut 只挂 @annotation——注解本体为
 * METHOD 级，@within 形态无匹配面；类级 {@code @AuditTrail} 是共享映射面，由默认拦截
 * 模式消费，本切面进链时注解必为方法级。</p>
 *
 * <p><b>嵌套口径（实现取简，沿原口径）</b>：嵌套审计方法共享同一线程上下文，最内层收尾即
 * 整表清空——外层<b>此前</b> put 的值随之丢失。外层若需自有上下文值参与求值，有效时机只有
 * 「最后一次内层调用返回之后、外层返回之前」这一段（内层清空后重新 put）；需要更复杂分层
 * 的场景走 sink 直调（programmatic）而非注解。</p>
 *
 * @author coder (棒2, 租户豁免与审计框架治理 2026-10-24)
 * @author sharp (audit 框架增强：成功策略/活动流水维度列 2026-10-24)
 */
@Slf4j
@Aspect
@Component
public class AuditTrailAspect {

    private final ObjectProvider<AuditTrailSuccessPolicy> successPolicies;
    private final ObjectProvider<AuditTrailGroupResolver> groupResolvers;
    private final AuditTrailWriter writer;

    public AuditTrailAspect(ObjectProvider<AuditTrailSink> sinkProvider,
                            ApplicationEventPublisher eventPublisher,
                            ObjectProvider<AuditTrailSuccessPolicy> successPolicies,
                            ObjectProvider<AuditTrailGroupResolver> groupResolvers) {
        this.successPolicies = successPolicies;
        this.groupResolvers = groupResolvers;
        this.writer = new AuditTrailWriter(sinkProvider, eventPublisher);
    }

    @Around("@annotation(auditTrail)")
    public Object around(ProceedingJoinPoint pjp, AuditTrail auditTrail) throws Throwable {
        try {
            Object result = null;
            Throwable successSignal = null;
            try {
                result = pjp.proceed();
            } catch (Throwable thrown) {
                if (!countsAsSuccess(thrown)) {
                    throw thrown;
                }
                successSignal = thrown;
            }
            writeAuditQuietly(pjp, auditTrail, result);
            if (successSignal != null) {
                throw successSignal;
            }
            return result;
        } finally {
            AuditContext.clear();
        }
    }

    /**
     * 成功通道判定：任一注册策略计为成功即成功；无策略/策略抛错按失败（保守）。
     */
    private boolean countsAsSuccess(Throwable thrown) {
        for (AuditTrailSuccessPolicy policy : successPolicies.orderedStream()
                .toArray(AuditTrailSuccessPolicy[]::new)) {
            try {
                if (policy.countsAsSuccess(thrown)) {
                    return true;
                }
            } catch (Exception ex) {
                log.warn("审计成功判定策略抛错（按失败处理） policy={}", policy.getClass().getName(), ex);
            }
        }
        return false;
    }

    /**
     * 审计写入全段（求值＋组装＋落 sink）——任何异常吞并：log.error＋告警，业务不受影响。
     */
    private void writeAuditQuietly(ProceedingJoinPoint pjp, AuditTrail anno, Object result) {
        String headline = "bizType=" + anno.bizType() + " action=" + anno.action()
                + " operator=" + AccountContext.getOperator();
        try {
            Map<String, Object> ctxSnapshot = AuditContext.snapshot();
            Method method = ((MethodSignature) pjp.getSignature()).getMethod();
            Object[] args = pjp.getArgs();
            StandardEvaluationContext eval =
                    AuditTrailSpel.evaluationContext(method, args, result, ctxSnapshot);

            String ctxBizId = stringOf(ctxSnapshot.get(AuditContext.RESOURCE_ID_OVERRIDE));
            // 显式注解模式无反射兜底：bizId 属性空/求值空＝null（行为面与旧版一致、可预测）；
            // 三级提取（含 getId 反射）只属于默认拦截模式（有请求面，web 三腿全走）
            String bizId = ctxBizId != null ? ctxBizId
                    : nullIfEmpty(AuditTrailSpel.evaluate(anno.bizId(), eval, false));

            writer.writeQuietly(AuditRecord.builder()
                    .bizType(anno.bizType())
                    .action(resolveAction(anno, ctxSnapshot))
                    .field(nullIfEmpty(anno.field()))
                    .bizId(bizId)
                    .oldValue(stringOf(ctxSnapshot.get(AuditContext.OLD_VALUE)))
                    .newValue(stringOf(ctxSnapshot.get(AuditContext.NEW_VALUE)))
                    .reason(nullIfEmpty(AuditTrailSpel.evaluate(anno.reason(), eval, true)))
                    .operatorUserId(AccountContext.getOperator())
                    .operatorIdentity(nullIfEmpty(anno.operatorIdentity()))
                    .occurredAt(new Date())
                    .tenantId(TenantContext.currentTenantId())
                    .groupKey(resolveGroup(anno, eval, ctxSnapshot))
                    .resourceLabel(resolveLabel(anno, eval, ctxSnapshot))
                    .detail(resolveDetail(anno, eval, ctxSnapshot))
                    .visibility(nullIfEmpty(anno.visibility()))
                    .endpoint(AuditWebSupport.endpointOf(AuditWebSupport.currentRequest()))
                    .build());
        } catch (Exception ex) {
            log.error("@AuditTrail 审计组装/求值失败，审计行丢弃（不回滚业务） {}", headline, ex);
            writer.alert(headline + " cause=" + ex);
        }
    }

    /** 动作：运行时覆写（{@link AuditContext#ACTION_OVERRIDE}）优先于注解属性 */
    private String resolveAction(AuditTrail anno, Map<String, Object> ctxSnapshot) {
        String override = stringOf(ctxSnapshot.get(AuditContext.ACTION_OVERRIDE));
        return override != null ? override : nullIfEmpty(anno.action());
    }

    /** 分组键：ctx 覆写 ＞ 注解 SpEL（降级求值）＞ 宿主 resolver */
    private String resolveGroup(AuditTrail anno, StandardEvaluationContext eval,
                                Map<String, Object> ctxSnapshot) {
        String override = stringOf(ctxSnapshot.get(AuditContext.GROUP_OVERRIDE));
        if (override != null) {
            return override;
        }
        String spel = AuditTrailSpel.tryEvaluate(anno.group(), eval, false, "group");
        if (spel != null) {
            return spel;
        }
        return resolveGroupFromResolvers();
    }

    /** 资源简述：ctx 值优先于注解模板（均降级求值纪律） */
    private String resolveLabel(AuditTrail anno, StandardEvaluationContext eval,
                                Map<String, Object> ctxSnapshot) {
        String ctxValue = stringOf(ctxSnapshot.get(AuditContext.LABEL));
        return ctxValue != null ? ctxValue
                : AuditTrailSpel.tryEvaluate(anno.resourceLabel(), eval, true, "resourceLabel");
    }

    /** 业务详情：ctx 值优先于注解模板（均降级求值纪律） */
    private String resolveDetail(AuditTrail anno, StandardEvaluationContext eval,
                                 Map<String, Object> ctxSnapshot) {
        String ctxValue = stringOf(ctxSnapshot.get(AuditContext.DETAIL));
        return ctxValue != null ? ctxValue
                : AuditTrailSpel.tryEvaluate(anno.detail(), eval, true, "detail");
    }

    /** resolver 链：取第一个非空；单实现抛错按 null 跳过（吞并，不影响业务） */
    private String resolveGroupFromResolvers() {
        for (AuditTrailGroupResolver resolver : groupResolvers.orderedStream()
                .toArray(AuditTrailGroupResolver[]::new)) {
            try {
                String group = resolver.resolveGroup();
                if (group != null && !group.trim().isEmpty()) {
                    return group.trim();
                }
            } catch (Exception ex) {
                log.warn("审计分组解析器抛错（按无分组处理） resolver={}", resolver.getClass().getName(), ex);
            }
        }
        return null;
    }

    private String stringOf(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    private String nullIfEmpty(String value) {
        return value == null || value.isEmpty() ? null : value;
    }
}
