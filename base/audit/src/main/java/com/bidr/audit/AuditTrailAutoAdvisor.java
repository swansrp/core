package com.bidr.audit;

import lombok.extern.slf4j.Slf4j;
import org.aopalliance.aop.Advice;
import org.aopalliance.intercept.MethodInterceptor;
import org.aopalliance.intercept.MethodInvocation;
import org.springframework.aop.Pointcut;
import org.springframework.aop.PointcutAdvisor;
import org.springframework.aop.support.AbstractPointcutAdvisor;
import org.springframework.aop.aspectj.AspectJExpressionPointcut;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.expression.spel.support.StandardEvaluationContext;

import com.bidr.authorization.holder.AccountContext;
import com.bidr.authorization.holder.TenantContext;

import javax.servlet.http.HttpServletRequest;
import java.lang.reflect.Method;
import java.util.Date;
import java.util.List;
import java.util.Map;

/**
 * 审计默认拦截切面（"默认拦截＋显式豁免"模式）：{@code bidr.audit-trail.auto.enabled=true}
 * 时经 {@link AuditTrailAutoConfiguration} 装配为 {@link PointcutAdvisor}，拦截面＝
 * {@code within(业务包..*) || execution(* 框架基类+.*(..))} 两腿并集（表达式按配置拼装——
 * 包名宿主各异，注解切点写不了常量；沿 inkhub 真库实测定案，<b>勿改 {@code @target}/
 * {@code @within} 单条</b>：Spring 代理层 classFilter 会全宽松、给全应用每个 bean 建代理）。
 *
 * <p><b>拦截面内是否记</b>（{@link #shouldRecord}）：写动词 ∧ 非读白名单尾段 ∧ 非方法级
 * {@code @AuditTrail}（显式注解切面全权负责，防双记）∧ 未 {@link AuditTrailIgnore} 豁免
 * ∧ 框架继承面须类级 {@code @AuditTrail} 放行。无注解也组装行（bizType 为 null，动作按
 * 路径/方法名推导）——宿主 sink 定夺丢弃；"漏注解也有行"正是本模式的防静默漏记价值。</p>
 *
 * <p><b>成功口径</b>：与显式注解模式共用 {@link AuditTrailSuccessPolicy}（框架
 * {@code Resp.notice} 族异常成功通道由宿主注册策略计成功，落行后原样 rethrow）。
 * <b>绝不阻断业务</b>：判定＋组装＋落行全段吞并；finally 清 {@link AuditContext}
 * （防线程池泄漏）。嵌套口径沿 {@link AuditTrailAspect}：最内层收尾即清空。</p>
 *
 * <p><b>与显式注解切面的分工</b>：本切面拦 web 写端点（有请求面：endpoint/绑定入参/
 * 路径变量全可提取），显式切面拦注解方法（SpEL 全变量面）。同一请求两者只走其一
 * （方法级注解端点被本切面跳过）。</p>
 *
 * @author sharp (audit 框架增强：默认拦截模式 2026-10-24)
 */
@Slf4j
public class AuditTrailAutoAdvisor extends AbstractPointcutAdvisor implements MethodInterceptor {

    private final transient AspectJExpressionPointcut pointcut;
    private final transient AuditTrailProperties.Auto props;
    private final transient AuditTrailWriter writer;
    private final transient ObjectProvider<AuditTrailSuccessPolicy> successPolicies;
    private final transient ObjectProvider<AuditTrailGroupResolver> groupResolvers;

    public AuditTrailAutoAdvisor(AuditTrailProperties.Auto props, AuditTrailWriter writer,
                                 ObjectProvider<AuditTrailSuccessPolicy> successPolicies,
                                 ObjectProvider<AuditTrailGroupResolver> groupResolvers) {
        this.props = props;
        this.writer = writer;
        this.successPolicies = successPolicies;
        this.groupResolvers = groupResolvers;
        String expression = pointcutExpression(props);
        if (expression.isEmpty()) {
            // fail-fast：开关开了但两腿全空＝配置错误，静默空转比启动失败更难排查
            throw new IllegalStateException(
                    "bidr.audit-trail.auto.enabled=true 但 controllerPackages/frameworkBaseClasses 全空——至少配置一腿");
        }
        this.pointcut = new AspectJExpressionPointcut();
        this.pointcut.setExpression(expression);
    }

    @Override
    public Pointcut getPointcut() {
        return pointcut;
    }

    @Override
    public Advice getAdvice() {
        return this;
    }

    /**
     * 切点表达式拼装（两腿并集，静态纯函数供守卫测试钉死）：
     * {@code within(p1..*) || ... || execution(* base1+.*(..)) || ...}。
     * 包名已含尾部通配的照拼不误判（AspectJ 按前缀段匹配）。
     */
    public static String pointcutExpression(AuditTrailProperties.Auto props) {
        StringBuilder sb = new StringBuilder();
        for (String pkg : props.getControllerPackages()) {
            if (pkg == null || pkg.trim().isEmpty()) {
                continue;
            }
            appendOr(sb);
            sb.append("within(").append(pkg.trim()).append("..*)");
        }
        for (String base : props.getFrameworkBaseClasses()) {
            if (base == null || base.trim().isEmpty()) {
                continue;
            }
            appendOr(sb);
            sb.append("execution(* ").append(base.trim()).append("+.*(..))");
        }
        return sb.toString();
    }

    private static void appendOr(StringBuilder sb) {
        if (sb.length() > 0) {
            sb.append(" || ");
        }
    }

    // ==================== 拦截 ====================

    @Override
    public Object invoke(MethodInvocation invocation) throws Throwable {
        HttpServletRequest request = AuditWebSupport.currentRequest();
        if (request == null) {
            // 非 web 线程：本切面只服务 web 写端点，直接放行（不激活上下文）
            return invocation.proceed();
        }
        Method method = invocation.getMethod();
        Object target = invocation.getThis();
        Class<?> targetClass = target == null ? method.getDeclaringClass() : target.getClass();
        if (!shouldRecord(request.getMethod(), AuditWebSupport.requestPath(request),
                method, targetClass, props)) {
            return invocation.proceed();
        }
        try {
            Object result = null;
            Throwable successSignal = null;
            try {
                result = invocation.proceed();
            } catch (Throwable thrown) {
                if (!countsAsSuccess(thrown)) {
                    throw thrown;
                }
                successSignal = thrown;
            }
            try {
                writer.writeQuietly(buildRecord(request, method, targetClass,
                        invocation.getArguments(), result));
            } catch (Exception e) {
                // auto 是"拦一切"模式，组装失败静默丢行＝运维无感知——与显式注解模式同纪律（error＋告警）
                String headline = "endpoint=" + AuditWebSupport.endpointOf(request)
                        + " bizType=" + annotationBizType(method, targetClass)
                        + " operator=" + AccountContext.getOperator();
                log.error("审计默认拦截组装/求值失败，审计行丢弃（不回滚业务） {}", headline, e);
                writer.alert(headline + " cause=" + e);
            }
            if (successSignal != null) {
                throw successSignal;
            }
            return result;
        } finally {
            AuditContext.clear();
        }
    }

    /** 组装失败定位用 bizType（与 {@link #buildRecord} 的注解解析同源：方法级＞类级，无注解＝null） */
    private String annotationBizType(Method method, Class<?> targetClass) {
        AuditTrail anno = effectiveAuditTrail(method, targetClass);
        return anno == null ? null : anno.bizType();
    }

    // ==================== 判定（静态可测） ====================

    /**
     * 拦截面内是否记：写动词 ∧ 非读白名单 ∧ 非方法级注解（防双记）∧ 未豁免
     * ∧ 框架继承面须类级注解放行。任一不满足→放行不记。
     */
    public static boolean shouldRecord(String httpMethod, String path, Method method,
                                Class<?> targetClass, AuditTrailProperties.Auto props) {
        if (!AuditWebSupport.isWriteMethod(httpMethod, props.getWriteMethods())) {
            return false;
        }
        if (AuditWebSupport.isReadPath(path, props.getReadPathTails())) {
            return false;
        }
        if (method.getAnnotation(AuditTrail.class) != null) {
            // 显式注解端点：语义（SpEL 全变量面）由显式切面全权负责，本切面跳过防双记
            return false;
        }
        if (method.getAnnotation(AuditTrailIgnore.class) != null
                || AnnotatedElementUtils.findMergedAnnotation(targetClass, AuditTrailIgnore.class) != null) {
            return false;
        }
        return isBusinessDeclaredOrClassAnnotated(method, targetClass, props.getControllerPackages());
    }

    /**
     * 框架继承面闸：方法声明类在业务包内＝业务声明方法，放行；声明在框架基类
     * （execution 腿按目标类求值不分包，非业务域的框架继承控制器也会进链）的，
     * 须目标类带类级 {@code @AuditTrail} 才放行——框架会话/平台端点不记。
     */
    public static boolean isBusinessDeclaredOrClassAnnotated(Method method, Class<?> targetClass,
                                                      List<String> controllerPackages) {
        String declaring = method.getDeclaringClass().getName();
        for (String pkg : controllerPackages) {
            if (pkg != null && !pkg.trim().isEmpty() && declaring.startsWith(pkg.trim())) {
                return true;
            }
        }
        return AnnotatedElementUtils.findMergedAnnotation(targetClass, AuditTrail.class) != null;
    }

    // ==================== 组装 ====================

    private AuditRecord buildRecord(HttpServletRequest request, Method method, Class<?> targetClass,
                                    Object[] args, Object result) {
        AuditTrail anno = effectiveAuditTrail(method, targetClass);
        Map<String, Object> ctx = AuditContext.snapshot();
        String path = AuditWebSupport.requestPath(request);
        Map<String, String> namedArgs = AuditWebSupport.bindNamedArgs(method, args);
        Map<String, String> pathVars = AuditWebSupport.extractPathVars(request);

        StandardEvaluationContext eval = anno != null
                ? AuditTrailSpel.evaluationContext(method, args, result, ctx) : null;

        // 动作：ctx 覆写 ＞ 注解显式 ＞ 路径/方法名推导（无注解端点的兜底腿）
        String action = stringOf(ctx.get(AuditContext.ACTION_OVERRIDE));
        if (action == null) {
            action = anno != null && !anno.action().isEmpty()
                    ? anno.action() : AuditWebSupport.deriveAction(path, method.getName());
        }

        // 资源标识：ctx 覆写 ＞ 注解 SpEL（严格，行级失败纪律）＞ 三级提取（web 面三腿全走）
        String bizId = stringOf(ctx.get(AuditContext.RESOURCE_ID_OVERRIDE));
        if (bizId == null && anno != null) {
            bizId = AuditTrailSpel.evaluate(anno.bizId(), eval, false);
        }
        if (bizId == null) {
            String idParam = anno != null ? anno.idParam() : "id";
            bizId = AuditWebSupport.resolveId(idParam, namedArgs, pathVars, args);
        }

        return AuditRecord.builder()
                .bizType(anno != null ? anno.bizType() : null)
                .action(action)
                .field(anno == null ? null : nullIfEmpty(anno.field()))
                .bizId(bizId)
                .oldValue(stringOf(ctx.get(AuditContext.OLD_VALUE)))
                .newValue(stringOf(ctx.get(AuditContext.NEW_VALUE)))
                .reason(anno == null ? null
                        : nullIfEmpty(AuditTrailSpel.evaluate(anno.reason(), eval, true)))
                .operatorUserId(AccountContext.getOperator())
                .operatorIdentity(anno == null ? null : nullIfEmpty(anno.operatorIdentity()))
                .occurredAt(new Date())
                .tenantId(TenantContext.currentTenantId())
                .groupKey(resolveGroup(anno, eval, ctx, namedArgs, pathVars))
                .resourceLabel(resolveLabel(anno, eval, ctx))
                .detail(resolveDetail(anno, eval, ctx))
                .visibility(anno == null ? null : nullIfEmpty(anno.visibility()))
                .endpoint(AuditWebSupport.endpointOf(request))
                .build();
    }

    /** 方法级注解优先，缺则类级（类级＝共享映射面：bizType/visibility 等） */
    private AuditTrail effectiveAuditTrail(Method method, Class<?> targetClass) {
        AuditTrail methodLevel = method.getAnnotation(AuditTrail.class);
        if (methodLevel != null) {
            return methodLevel;
        }
        return AnnotatedElementUtils.findMergedAnnotation(targetClass, AuditTrail.class);
    }

    /**
     * 分组键：ctx 覆写 ＞ 注解 SpEL（降级）＞ 宿主 resolver ＞ 绑定入参/路径变量
     * 按 {@code groupParam}（缺省 spaceId）兜底——多空间应用的最常见参数名，非业务常量。
     */
    private String resolveGroup(AuditTrail anno, StandardEvaluationContext eval,
                                Map<String, Object> ctx, Map<String, String> namedArgs,
                                Map<String, String> pathVars) {
        String override = stringOf(ctx.get(AuditContext.GROUP_OVERRIDE));
        if (override != null) {
            return override;
        }
        if (anno != null) {
            String spel = AuditTrailSpel.tryEvaluate(anno.group(), eval, false, "group");
            if (spel != null) {
                return spel;
            }
        }
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
        String param = props.getGroupParam();
        return AuditWebSupport.resolveId(param, namedArgs, pathVars, null);
    }

    private String resolveLabel(AuditTrail anno, StandardEvaluationContext eval, Map<String, Object> ctx) {
        String ctxValue = stringOf(ctx.get(AuditContext.LABEL));
        if (ctxValue != null) {
            return ctxValue;
        }
        return anno == null ? null
                : AuditTrailSpel.tryEvaluate(anno.resourceLabel(), eval, true, "resourceLabel");
    }

    private String resolveDetail(AuditTrail anno, StandardEvaluationContext eval, Map<String, Object> ctx) {
        String ctxValue = stringOf(ctx.get(AuditContext.DETAIL));
        if (ctxValue != null) {
            return ctxValue;
        }
        return anno == null ? null
                : AuditTrailSpel.tryEvaluate(anno.detail(), eval, true, "detail");
    }

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

    private String stringOf(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    private String nullIfEmpty(String value) {
        return value == null || value.isEmpty() ? null : value;
    }
}
