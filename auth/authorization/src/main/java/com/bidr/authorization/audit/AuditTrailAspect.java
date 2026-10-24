package com.bidr.authorization.audit;

import com.bidr.authorization.holder.AccountContext;
import com.bidr.authorization.holder.TenantContext;
import com.bidr.kernel.constant.err.ErrCode;
import com.bidr.kernel.constant.err.ErrCodeLevel;
import com.bidr.kernel.event.ServiceExceptionEvent;
import com.bidr.kernel.exception.ServiceException;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.core.DefaultParameterNameDiscoverer;
import org.springframework.core.ParameterNameDiscoverer;
import org.springframework.expression.common.TemplateParserContext;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.expression.spel.support.StandardEvaluationContext;
import org.springframework.stereotype.Component;

import java.lang.reflect.Method;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 审计切面：拦截 {@link AuditTrail} 注解方法，成功返回后 SpEL 求值组装
 * {@link AuditRecord} 并写入 {@link AuditTrailSink}（core 侧 ObjectProvider 可选收集，
 * 宿主无实现＝行丢弃＋高等级告警——ERP 侧由 erp-common 装实现）。
 *
 * <p><b>异常语义（裁定 2，本切面最高纪律）</b>：审计链路的任何失败——sink 缺失、
 * sink 写入抛错、SpEL 求值失败——一律 catch-all 吞并：绝不影响业务返回值、绝不 rethrow；
 * 同时 log.error 高等级留痕（含 bizType/action/operator 定位信息）并发布
 * {@link ServiceExceptionEvent}（模块本地 {@link AuditAlertCode}，ERROR 级）接入既有运维
 * 通知链（{@code ServiceExceptionEmailListener} @Async 邮件族——切面吞并后异常不再外抛，
 * 不会自然进全局异常处理器的发布点，故显式 publish，零新依赖）。</p>
 *
 * <p><b>求值时机（M-6）</b>：@Around proceed 返回后构造 {@link StandardEvaluationContext}——
 * {@code #result}（返回值）、{@code #参数名}（参数名发现器解析，解析不到时 {@code #a0..aN}
 * 按位兜底）、{@code #args}（入参数组）、{@code #ctx}（{@link AuditContext} 快照）。
 * 方法抛业务异常则不产审计行（异常原样上抛，审计侧不介入、不告警——全局异常处理器的职责）。</p>
 *
 * <p><b>上下文收尾</b>：finally 清 {@link AuditContext}（方法级主清理；请求线程侧另有
 * AuthInterceptor afterCompletion 兜底）。pointcut 只挂 @annotation——注解本体为
 * METHOD 级，@within 形态无匹配面。</p>
 *
 * @author coder (棒2, 租户豁免与审计框架治理 2026-10-24)
 */
@Slf4j
@Aspect
@Component
@RequiredArgsConstructor
public class AuditTrailAspect {

    private static final SpelExpressionParser EXPRESSION_PARSER = new SpelExpressionParser();
    /** reason 属性的模板口径：#{...} 插值（Spring TemplateParserContext 默认定界符） */
    private static final TemplateParserContext TEMPLATE_CONTEXT = new TemplateParserContext();
    private static final ParameterNameDiscoverer PARAMETER_NAMES = new DefaultParameterNameDiscoverer();

    private final ObjectProvider<AuditTrailSink> sinkProvider;
    private final ApplicationEventPublisher eventPublisher;

    @Around("@annotation(auditTrail)")
    public Object around(ProceedingJoinPoint pjp, AuditTrail auditTrail) throws Throwable {
        try {
            Object result = pjp.proceed();
            writeAuditQuietly(pjp, auditTrail, result);
            return result;
        } finally {
            AuditContext.clear();
        }
    }

    /**
     * 审计写入全段（求值＋组装＋落 sink）——任何异常吞并：log.error＋告警，业务不受影响。
     */
    private void writeAuditQuietly(ProceedingJoinPoint pjp, AuditTrail anno, Object result) {
        String headline = headline(anno);
        try {
            List<AuditTrailSink> sinks = sinkProvider.orderedStream().collect(Collectors.toList());
            if (sinks.isEmpty()) {
                log.error("@AuditTrail 无 AuditTrailSink 装配，审计行丢弃（宿主需装实现） {}", headline);
                publishAlert(headline + " cause=missing-sink");
                return;
            }
            Map<String, Object> ctxSnapshot = AuditContext.snapshot();
            StandardEvaluationContext eval = buildEvaluationContext(pjp, result, ctxSnapshot);
            AuditRecord record = buildRecord(anno, eval, ctxSnapshot);
            boolean anySinkFailed = false;
            for (AuditTrailSink sink : sinks) {
                try {
                    sink.write(record);
                } catch (Exception ex) {
                    anySinkFailed = true;
                    log.error("@AuditTrail sink 写入失败（不回滚业务） sink={} {}",
                            sink.getClass().getName(), headline, ex);
                }
            }
            if (anySinkFailed) {
                publishAlert(headline);
            }
        } catch (Exception ex) {
            log.error("@AuditTrail 审计组装/求值失败，审计行丢弃（不回滚业务） {}", headline, ex);
            publishAlert(headline + " cause=" + ex);
        }
    }

    private StandardEvaluationContext buildEvaluationContext(ProceedingJoinPoint pjp, Object result,
                                                             Map<String, Object> ctxSnapshot) {
        StandardEvaluationContext eval = new StandardEvaluationContext();
        eval.setVariable("result", result);
        Object[] args = pjp.getArgs();
        eval.setVariable("args", args);
        eval.setVariable("ctx", ctxSnapshot);
        Method method = ((MethodSignature) pjp.getSignature()).getMethod();
        String[] names = PARAMETER_NAMES.getParameterNames(method);
        if (names != null) {
            for (int i = 0; i < names.length && i < args.length; i++) {
                eval.setVariable(names[i], args[i]);
            }
        }
        for (int i = 0; i < args.length; i++) {
            eval.setVariable("a" + i, args[i]);
        }
        return eval;
    }

    private AuditRecord buildRecord(AuditTrail anno, StandardEvaluationContext eval,
                                    Map<String, Object> ctxSnapshot) {
        return AuditRecord.builder()
                .bizType(anno.bizType())
                .action(anno.action())
                .field(nullIfEmpty(anno.field()))
                .bizId(nullIfEmpty(evaluate(anno.bizId(), eval, false)))
                .oldValue(fromConventionKey(ctxSnapshot, AuditContext.OLD_VALUE))
                .newValue(fromConventionKey(ctxSnapshot, AuditContext.NEW_VALUE))
                .reason(nullIfEmpty(evaluate(anno.reason(), eval, true)))
                .operatorUserId(AccountContext.getOperator())
                .operatorIdentity(nullIfEmpty(anno.operatorIdentity()))
                .occurredAt(new Date())
                .tenantId(TenantContext.currentTenantId())
                .build();
    }

    /**
     * SpEL 求值：template=true 走 {@code #{...}} 模板插值（无表达式原样返回），
     * false 走整体表达式。表达式返回非 String 时经 SpEL 转换服务转 String。
     * 空表达式返回 null（不触发解析）。
     */
    private String evaluate(String expression, StandardEvaluationContext eval, boolean template) {
        if (StringUtils.isEmpty(expression)) {
            return null;
        }
        Object value = template
                ? EXPRESSION_PARSER.parseExpression(expression, TEMPLATE_CONTEXT).getValue(eval)
                : EXPRESSION_PARSER.parseExpression(expression).getValue(eval);
        return value == null ? null : String.valueOf(value);
    }

    private String fromConventionKey(Map<String, Object> ctxSnapshot, String key) {
        Object value = ctxSnapshot.get(key);
        return value == null ? null : String.valueOf(value);
    }

    private String nullIfEmpty(String value) {
        return StringUtils.isEmpty(value) ? null : value;
    }

    private String headline(AuditTrail anno) {
        return "bizType=" + anno.bizType() + " action=" + anno.action()
                + " operator=" + AccountContext.getOperator();
    }

    /**
     * 接入运维通知链（M-9）：显式发布 {@link ServiceExceptionEvent}——
     * 监听面为 {@code ServiceExceptionEmailListener}（@Async @EventListener，默认最低
     * 告警级别 ERROR）。用模块本地 {@link AuditAlertCode} 而非 kernel 既有码的原因：
     * SYS_ERR 无占位符（丢失定位明细）、SYS_ERR_MSG 是 WARN 级（默认阈值下发不出邮件）。
     * 发布本身也可能失败（无监听器的宿主/容器异常）——同样吞并，只 log.error。
     */
    private void publishAlert(String detail) {
        try {
            eventPublisher.publishEvent(new ServiceExceptionEvent(
                    new ServiceException(AuditAlertCode.AUDIT_WRITE_FAILED, detail), null));
        } catch (Exception ex) {
            log.error("@AuditTrail 发布审计告警事件失败（原审计失败明细：{}）", detail, ex);
        }
    }

    /**
     * 审计失败告警码（模块本地、kernel 零改动——ErrCode 是公开接口，authorization 内
     * 新增实现属加法）。编号取 901 独立段：本异常只进告警事件、永不到客户端
     * （切面吞并，不 rethrow），无响应码冲突面。
     */
    @Getter
    @AllArgsConstructor
    private enum AuditAlertCode implements ErrCode {

        /** 审计记录写入失败（业务未受影响）——占位符承载 bizType/operator/cause 定位明细 */
        AUDIT_WRITE_FAILED(901, "审计记录写入失败（业务未受影响）：%s", ErrCodeLevel.ERROR);

        private final Integer errCode;
        private final String errMsg;
        private final ErrCodeLevel errLevel;
    }
}
