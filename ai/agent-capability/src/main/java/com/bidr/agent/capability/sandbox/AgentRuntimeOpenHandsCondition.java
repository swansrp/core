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
 * {@code matchIfMissing=true} 条件同键同值域）。
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
        String enabled = context.getEnvironment().getProperty("my.agent.runtime.enabled");
        if (enabled == null || !"true".equalsIgnoreCase(enabled.trim())) {
            return false;
        }
        String provider = context.getEnvironment().getProperty("my.agent.runtime.provider");
        return provider == null || provider.trim().isEmpty()
                || OpenHandsProvider.NAME.equalsIgnoreCase(provider.trim());
    }
}
