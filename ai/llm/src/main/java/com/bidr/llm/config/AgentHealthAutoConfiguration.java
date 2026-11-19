package com.bidr.llm.config;

import com.bidr.document.spi.AgentHealthContributor;
import com.bidr.llm.agent.health.AgentCapabilityHealthProbe;
import com.bidr.llm.agent.health.AgentHealthSseHub;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;

import java.util.List;

/**
 * Title: AgentHealthAutoConfiguration
 * Description: agent 探活三件套装配（P3-0 自 agent-capability 物理迁入）——装 llm 的宿主
 * 自动获得探活面，零额外接线：
 * <ul>
 *     <li>{@link AgentHealthSseHub}（健康度 SSE）：探活状态变化的 broadcast 注册表
 *     （无 receiver 维度），前端经 {@code GET /web/api/agent/health/stream} 订阅
 *     （{@link com.bidr.llm.agent.health.AgentHealthStreamController} 扫描拾取），徽标实时
 *     亮/灰不靠刷新。</li>
 *     <li>{@link AgentCapabilityHealthProbe}（探活聚合器）：按注入的 {@link AgentHealthContributor}
 *     列表逐腿探测聚合（探测细节在各腿实现，contributor bean 在场才收集——「没这个子模块就
 *     不收集」），启动一次＋定时刷新（配置键 {@code llm.agent.health-probe.interval-seconds}
 *     缺省 300，≤0 关定时，与迁出前口径逐字一致），状态变化打一行志＋SSE 广播，维护运行级
 *     健康快照（{@code com.bidr.llm.agent.file.AgentCapabilityHealthProvider} 端口实现，
 *     经 /agents 契约的 health 字段上报前端徽标）。不改能力位、不阻断启动。</li>
 * </ul>
 * 两 Bean 均宿主自建同类型 Bean 时让位（{@code @ConditionalOnMissingBean} 与迁出前原口径
 * 逐字一致）。注册入口双轨（同 {@link AgentSessionAutoConfiguration} 等既有 llm 装配）：
 * 宿主扫描根含 {@code com.bidr} 时按扫描件装配（epc 形态）；更窄扫描根的宿主走
 * {@code META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports}。
 *
 * @author Sharp
 * @since 2026/11/19
 */
@AutoConfiguration
public class AgentHealthAutoConfiguration {

    /**
     * 健康度 SSE 广播注册表（无 receiver 维度，broadcast 全员）：探活状态变化的实时推送口，
     * 前端经 {@code GET /web/api/agent/health/stream} 订阅（{@code AgentHealthStreamController}
     * 扫描拾取，构造注入本 Bean）。宿主自建同类型 Bean 时让位。
     */
    @Bean
    @ConditionalOnMissingBean(AgentHealthSseHub.class)
    public AgentHealthSseHub agentHealthSseHub() {
        return new AgentHealthSseHub();
    }

    /**
     * 能力探活聚合器：收集与调度接线——启动一次＋定时（{@code llm.agent.health-probe.interval-seconds}
     * 缺省 300，≤0 关定时）打状态变化行并维护运行级健康快照（经 /agents 契约上报前端徽标）。
     * 探针异常吞掉不阻断启动；探活结果<b>不改能力位</b>（capabilities 仍按声明级推导）。
     * 宿主自建同类型 Bean 时让位。
     */
    @Bean
    @ConditionalOnMissingBean(AgentCapabilityHealthProbe.class)
    public AgentCapabilityHealthProbe agentCapabilityHealthProbe(
            List<AgentHealthContributor> contributors,
            @Value("${llm.agent.health-probe.interval-seconds:300}") long intervalSeconds,
            AgentHealthSseHub healthSseHub) {
        return new AgentCapabilityHealthProbe(contributors, intervalSeconds, healthSseHub);
    }
}
