package com.bidr.gotenberg;

import org.springframework.context.annotation.Condition;
import org.springframework.context.annotation.ConditionContext;
import org.springframework.core.type.AnnotatedTypeMetadata;

/**
 * Title: GotenbergBaseUrlCondition
 * Description: 装配条件＝{@code tool.gotenberg.base-url} 有非空值（D10：无 provider 选型键，
 * 一把 base-url；留空＝不装配＝office 原件直送引用降级无页）。@ConditionalOnProperty
 * 无法表达「非空即真」（空串也会命中属性存在性判定），故自写条件。
 *
 * @author Sharp
 * @since 2026/10/20
 */
public class GotenbergBaseUrlCondition implements Condition {

    @Override
    public boolean matches(ConditionContext context, AnnotatedTypeMetadata metadata) {
        String baseUrl = context.getEnvironment().getProperty("tool.gotenberg.base-url");
        return baseUrl != null && !baseUrl.trim().isEmpty();
    }
}
