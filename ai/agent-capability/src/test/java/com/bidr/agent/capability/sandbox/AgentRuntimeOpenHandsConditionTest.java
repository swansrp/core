package com.bidr.agent.capability.sandbox;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.ConditionContext;
import org.springframework.core.type.AnnotatedTypeMetadata;
import org.springframework.mock.env.MockEnvironment;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

/**
 * Title: AgentRuntimeOpenHandsConditionTest
 * Description: 沙箱腿属性条件的直测钉桩（review #9）：装配层 ApplicationContextRunner 的
 * withPropertyValues 会对值做 trim，「带空格的 true」形态到不了 Condition，故 no-trim 语义
 * 只能在本类用 {@link MockEnvironment}（原样存取，不 trim）钉住——字面 true（equalsIgnoreCase）
 * 放行、trim 形态与 relaxed 值（on/yes/1）一律不放行，与 {@code @ConditionalOnProperty(havingValue="true")}
 * 原生语义同口径；provider 缺省/空/openhands（大小写不敏感）放行、其余值不放行；
 * {@code my.agent.sandbox.provider=cube}（棒1 Cube 沙箱腿互斥键）一律不放行（确定性退避，
 * 大小写不敏感），其余值（含缺席/openhands）不触发退避、原口径不变。
 *
 * @author Sharp
 * @since 2026/11/14
 */
class AgentRuntimeOpenHandsConditionTest {

    private final AgentRuntimeOpenHandsCondition condition = new AgentRuntimeOpenHandsCondition();
    private final ConditionContext context = mock(ConditionContext.class);
    private final AnnotatedTypeMetadata metadata = mock(AnnotatedTypeMetadata.class);

    private boolean matches(String enabled) {
        MockEnvironment env = new MockEnvironment();
        if (enabled != null) {
            env.setProperty("my.agent.runtime.enabled", enabled);
        }
        org.mockito.Mockito.when(context.getEnvironment()).thenReturn(env);
        return condition.matches(context, metadata);
    }

    @Test
    @DisplayName("enabled 字面 true：精确值与大小写变体放行（equalsIgnoreCase，同 @ConditionalOnProperty）")
    void literalTruePasses() {
        assertTrue(matches("true"));
        assertTrue(matches("TRUE"));
        assertTrue(matches("True"));
    }

    @Test
    @DisplayName("enabled no-trim（review #9）：带空格的 true 不再被放行，relaxed 值（on/yes/1）不放行")
    void paddedOrRelaxedValuesRejected() {
        assertFalse(matches("true "), "尾随空格：不 trim 后不放行");
        assertFalse(matches(" true"), "前导空格：不 trim 后不放行");
        assertFalse(matches("on"), "boolean relaxed 转换曾接受 on——Condition 口径不收");
        assertFalse(matches("yes"));
        assertFalse(matches("1"));
    }

    @Test
    @DisplayName("enabled 缺席/非 true 一律不放行（无 matchIfMissing）")
    void missingOrFalsyRejected() {
        assertFalse(matches(null), "属性缺席不放行");
        assertFalse(matches("false"));
        assertFalse(matches(""));
    }

    @Test
    @DisplayName("provider 维度：缺省/空串/openhands（大小写不敏感）放行，legacy 腿名不放行")
    void providerDimension() {
        assertTrue(matchesWithProvider("true", null), "provider 缺省放行");
        assertTrue(matchesWithProvider("true", ""), "provider 空串放行");
        assertTrue(matchesWithProvider("true", "openhands"));
        assertTrue(matchesWithProvider("true", "OpenHands"));
        assertFalse(matchesWithProvider("true", "agent-system"), "legacy 腿不放行");
        assertFalse(matchesWithProvider("true", " openhands"), "provider 同式不 trim");
    }

    @Test
    @DisplayName("Cube 沙箱腿互斥（棒1）：sandbox.provider=cube ⇒ 确定性退避不放行（enabled=true 也让位）")
    void cubeSandboxProviderTriggersRetreat() {
        assertFalse(matchesWithSandboxProvider("true", "cube"), "provider=cube ⇒ OpenHands 腿退避");
        assertFalse(matchesWithSandboxProvider("true", "Cube"), "equalsIgnoreCase 字面匹配，大小写变体同退避");
        assertFalse(matchesWithSandboxProvider("true", "CUBE"));
    }

    @Test
    @DisplayName("Cube 互斥回归腿：sandbox.provider=openhands/空串/缺席 ⇒ 原口径不变照常放行")
    void retreatOnlyTriggersForCubeValue() {
        assertTrue(matchesWithSandboxProvider("true", "openhands"), "非 cube 值不触发退避");
        assertTrue(matchesWithSandboxProvider("true", ""), "空串不触发退避");
        assertTrue(matchesWithSandboxProvider("true", "agent-system"),
                "退避只认 cube：其余值不触发（runtime.provider 缺省按 openhands 原口径放行）");
        assertTrue(matchesWithProvider("true", "openhands"), "sandbox.provider 整键缺席 ⇒ 原口径不变");
    }

    private boolean matchesWithProvider(String enabled, String provider) {
        MockEnvironment env = new MockEnvironment();
        if (enabled != null) {
            env.setProperty("my.agent.runtime.enabled", enabled);
        }
        if (provider != null) {
            env.setProperty("my.agent.runtime.provider", provider);
        }
        org.mockito.Mockito.when(context.getEnvironment()).thenReturn(env);
        return condition.matches(context, metadata);
    }

    private boolean matchesWithSandboxProvider(String enabled, String sandboxProvider) {
        MockEnvironment env = new MockEnvironment();
        if (enabled != null) {
            env.setProperty("my.agent.runtime.enabled", enabled);
        }
        if (sandboxProvider != null) {
            env.setProperty("my.agent.sandbox.provider", sandboxProvider);
        }
        org.mockito.Mockito.when(context.getEnvironment()).thenReturn(env);
        return condition.matches(context, metadata);
    }
}
