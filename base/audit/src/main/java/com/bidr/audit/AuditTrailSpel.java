package com.bidr.audit;

import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.core.DefaultParameterNameDiscoverer;
import org.springframework.core.ParameterNameDiscoverer;
import org.springframework.expression.common.TemplateParserContext;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.expression.spel.support.StandardEvaluationContext;

import java.lang.reflect.Method;
import java.util.Map;

/**
 * 审计 SpEL 求值共享段（包私有）：求值上下文构造＋表达式求值——显式注解切面与默认拦截切面
 * 共用同一套变量口径（{@code #参数名/#a0..#aN/#result/#args/#ctx}）。
 *
 * <p><b>求值纪律分级</b>（沿 {@link AuditTrail} javadoc）：定位关键字段（bizId）走
 * {@link #evaluate} 严格版——求值失败异常上抛，由切面按"整行丢弃＋告警"处理；装饰性字段
 * （group/resourceLabel/detail）走 {@link #tryEvaluate}——失败置 null＋log.error，行照发。</p>
 *
 * @author sharp (audit 框架增强：默认拦截模式 2026-10-24)
 */
@Slf4j
final class AuditTrailSpel {

    private static final SpelExpressionParser EXPRESSION_PARSER = new SpelExpressionParser();
    /** 模板属性的口径：#{...} 插值（Spring TemplateParserContext 默认定界符） */
    private static final TemplateParserContext TEMPLATE_CONTEXT = new TemplateParserContext();
    private static final ParameterNameDiscoverer PARAMETER_NAMES = new DefaultParameterNameDiscoverer();

    private AuditTrailSpel() {
    }

    /**
     * 构造求值上下文：{@code #result}（返回值，可为 null）、{@code #args}（入参数组）、
     * {@code #ctx}（{@link AuditContext} 快照）、{@code #参数名}（参数名发现器解析）＋
     * {@code #a0..#aN} 按位兜底。
     *
     * @param method      被拦截方法（注解切面经 {@link MethodSignature#getMethod()} 取）
     * @param args        入参数组
     * @param result      方法返回值（无返回值场景传 null）
     * @param ctxSnapshot {@link AuditContext#snapshot()} 快照
     */
    static StandardEvaluationContext evaluationContext(Method method, Object[] args, Object result,
                                                       Map<String, Object> ctxSnapshot) {
        StandardEvaluationContext eval = new StandardEvaluationContext();
        eval.setVariable("result", result);
        eval.setVariable("args", args);
        eval.setVariable("ctx", ctxSnapshot);
        String[] names = PARAMETER_NAMES.getParameterNames(method);
        if (names != null) {
            for (int i = 0; i < names.length && i < args.length; i++) {
                eval.setVariable(names[i], args[i]);
            }
        }
        if (args != null) {
            for (int i = 0; i < args.length; i++) {
                eval.setVariable("a" + i, args[i]);
            }
        }
        return eval;
    }

    /**
     * 严格求值（定位关键字段用）：表达式返回非 String 时经 SpEL 转换服务转 String；
     * 空表达式返回 null（不触发解析）。求值失败异常上抛——调用方按整行丢弃处理。
     *
     * @param template true＝{@code #{...}} 模板插值（无表达式原样返回）；false＝整体表达式
     */
    static String evaluate(String expression, StandardEvaluationContext eval, boolean template) {
        if (StringUtils.isEmpty(expression)) {
            return null;
        }
        Object value = template
                ? EXPRESSION_PARSER.parseExpression(expression, TEMPLATE_CONTEXT).getValue(eval)
                : EXPRESSION_PARSER.parseExpression(expression).getValue(eval);
        return value == null ? null : String.valueOf(value);
    }

    /**
     * 降级求值（装饰性字段用）：求值失败置 null＋log.error（含字段名定位），行照发。
     */
    static String tryEvaluate(String expression, StandardEvaluationContext eval, boolean template, String fieldName) {
        if (StringUtils.isEmpty(expression)) {
            return null;
        }
        try {
            return evaluate(expression, eval, template);
        } catch (Exception ex) {
            log.error("审计装饰字段 SpEL 求值失败（该字段置 null，审计行照发） field={} expr={}",
                    fieldName, expression, ex);
            return null;
        }
    }
}
