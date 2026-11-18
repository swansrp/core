package com.bidr.agent.capability.sandbox;

import com.bidr.agent.runtime.provider.openhands.OpenHandsProvider;
import org.springframework.context.annotation.Condition;
import org.springframework.context.annotation.ConditionContext;
import org.springframework.core.type.AnnotatedTypeMetadata;

/**
 * Title: AgentRuntimeOpenHandsCondition
 * Description: 沙箱腿（OpenHandsSandboxProvider）的装配条件＝「OpenHands 运行时腿启用」：
 * {@code my.agent.runtime.enabled=true}（显式 true，与 {@code AgentRuntimeAutoConfiguration} 的
 * {@code @ConditionalOnProperty(havingValue="true")} 同口径、无 matchIfMissing）且
 * {@code my.agent.runtime.provider} 缺省/空/{@code openhands}（与 openHandsClient Bean 的
 * {@code matchIfMissing=true} 条件同键同值域）；此外 {@code my.agent.sandbox.provider} 字面
 * {@code cube}（equalsIgnoreCase）时确定性退避（return false）——与 Cube 沙箱腿
 * {@code CubeSandboxAutoConfiguration} 的 provider=cube 装配条件同键同值域互斥，
 * 双侧都是属性条件（相位无关），{@code AgentSandbox} 装配面恰好一侧在场。
 * <p>
 * 🔴 <b>刻意不用 {@code @ConditionalOnBean(OpenHandsClient/OpenHandsProvider.class)}</b>：
 * 本模块的自动配置在扫描根含 {@code com.bidr} 的宿主（epc 形态）里会被<b>组件扫描</b>提前求值，
 * 扫描相位早于 agent-runtime 的 auto-config 注册——跨 auto-config 的 Bean 在场判定恒 false
 * （P2a 实证，案卷见 {@code AgentCapabilityAutoConfiguration} 类注释）；属性条件与相位无关。
 * Bean 真缺席（如宿主显式装配了 agent-system 腿）由 Bean 方法内的 getIfUnique 兜底降级。
 *
 * @author Sharp
 * @since 2026/11/14
 */
public class AgentRuntimeOpenHandsCondition implements Condition {

    @Override
    public boolean matches(ConditionContext context, AnnotatedTypeMetadata metadata) {
        // 字面 true（equalsIgnoreCase，不 trim——review #9）：对齐 Spring 原生 @ConditionalOnProperty
        // 的 equalsIgnoreCase 语义，trim/relaxed（on/yes/1）都不放行
        String enabled = context.getEnvironment().getProperty("my.agent.runtime.enabled");
        if (enabled == null || !"true".equalsIgnoreCase(enabled)) {
            return false;
        }
        // Cube 沙箱腿互斥退避（棒1）：宿主点名 provider=cube 时 OpenHands 腿确定性让位——与
        // CubeSandboxAutoConfiguration 的 @ConditionalOnProperty(name="my.agent.sandbox.provider",
        // havingValue="cube") 同键同值域，双侧都是属性条件（与相位无关，P2a 口径），
        // AgentSandbox 装配面恰好一侧在场，不会双腿同时装配
        String sandboxProvider = context.getEnvironment().getProperty("my.agent.sandbox.provider");
        if ("cube".equalsIgnoreCase(sandboxProvider)) {
            return false;
        }
        String provider = context.getEnvironment().getProperty("my.agent.runtime.provider");
        return provider == null || provider.isEmpty()
                || OpenHandsProvider.NAME.equalsIgnoreCase(provider);
    }
}
