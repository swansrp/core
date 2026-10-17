package com.bidr.llm.agent.registry;

import com.bidr.llm.agent.AutonomousAgentDefinition;
import com.bidr.llm.agent.session.AgentSessionService;
import com.bidr.llm.flow.FlowDefinitionProvider;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Title: AgentRegistryService
 * Description: 统一 Agent 注册中心（查询时聚合视图，不建表不自持状态）——把三类来源归一成
 * {@link AgentDescriptor} 清单：flow 型（{@link FlowDefinitionProvider} Bean）、
 * 自主型（{@link AutonomousAgentDefinition} Bean，经 AgentSessionService 注册表）、
 * 动态型（{@link DynamicAgentProvider} SPI，实时读业务数据如 InsightAgent 表）。
 * <p>
 * 冲突防护（agentCode 命名空间隔离，结构性防撞）：
 * <ul>
 *     <li>flow 型统一加保留前缀 flow:；自主型 agentKey 禁止含冒号（启动抛错）；
 *     动态型统一拼 {namespace}:{code}，namespace 互不相同且不得为 flow（启动抛错）——
 *     三类在构造规则上不可能重叠；</li>
 *     <li>动态项若仍与静态定义撞车（脏数据绕过校验）：聚合时跳过+warn，
 *     裁决静态定义 &gt; 动态数据（DB 脏数据不阻塞启动）。</li>
 * </ul>
 *
 * @author Sharp
 * @since 2026/8/22
 */
@Slf4j
@Service
public class AgentRegistryService {

    private final AgentSessionService agentSessionService;

    private final List<FlowDefinitionProvider> flowProviders;

    private final List<DynamicAgentProvider> dynamicProviders;

    public AgentRegistryService(AgentSessionService agentSessionService,
                                List<FlowDefinitionProvider> flowProviders,
                                List<DynamicAgentProvider> dynamicProviders) {
        this.agentSessionService = agentSessionService;
        this.flowProviders = flowProviders;
        this.dynamicProviders = dynamicProviders;
        validateStaticDefinitions();
    }

    /**
     * 启动期静态校验（代码级冲突零容忍）：动态命名空间互不相同、不占保留前缀、不含冒号；
     * 自主型 agentKey 不得含冒号（与命名空间 code 结构性区分）
     */
    private void validateStaticDefinitions() {
        Set<String> namespaces = new HashSet<>();
        for (DynamicAgentProvider provider : dynamicProviders) {
            String namespace = provider.namespace();
            if (!StringUtils.hasText(namespace) || namespace.contains(":")) {
                throw new IllegalStateException("DynamicAgentProvider 命名空间非法: " + namespace
                        + "（" + provider.getClass().getName() + "，须非空且不含冒号）");
            }
            if ("flow".equals(namespace)) {
                throw new IllegalStateException("DynamicAgentProvider 命名空间占用保留前缀 flow: "
                        + provider.getClass().getName());
            }
            if (!namespaces.add(namespace)) {
                throw new IllegalStateException("DynamicAgentProvider 命名空间重复注册: " + namespace);
            }
        }
        for (AutonomousAgentDefinition definition : agentSessionService.registeredAgents()) {
            if (definition.agentKey() != null && definition.agentKey().contains(":")) {
                throw new IllegalStateException("自主型 agentKey 不得含冒号（与命名空间 agentCode 区分）: "
                        + definition.agentKey());
            }
        }
        log.info("Agent 统一注册中心就绪：flow {} 个、autonomous {} 个、动态命名空间 {} 个",
                flowProviders.size(), agentSessionService.registeredAgents().size(), namespaces.size());
    }

    /**
     * 统一注册清单（实时聚合：动态项每次现读，随业务数据增删）；
     * 保持 flow → autonomous → dynamic 的展示序
     */
    public List<AgentDescriptor> all() {
        Map<String, AgentDescriptor> indexed = new LinkedHashMap<>();
        for (FlowDefinitionProvider provider : flowProviders) {
            AgentDescriptor descriptor = new AgentDescriptor();
            descriptor.setAgentCode(AgentDescriptor.FLOW_PREFIX + provider.flowKey());
            descriptor.setDisplayName(provider.displayName());
            descriptor.setKind(AgentKind.FLOW);
            descriptor.setSkillCode(provider.skillCode());
            descriptor.setModule("llm-flow");
            indexed.put(descriptor.getAgentCode(), descriptor);
        }
        for (AutonomousAgentDefinition definition : agentSessionService.registeredAgents()) {
            AgentDescriptor descriptor = new AgentDescriptor();
            descriptor.setAgentCode(definition.agentKey());
            descriptor.setDisplayName(definition.displayName());
            descriptor.setKind(AgentKind.AUTONOMOUS);
            descriptor.setSkillCode(definition.skillCode());
            descriptor.setModule("llm-agent");
            indexed.put(descriptor.getAgentCode(), descriptor);
        }
        for (DynamicAgentProvider provider : dynamicProviders) {
            List<AgentDescriptor> agents;
            try {
                agents = provider.agents();
            } catch (Exception e) {
                // 动态源读取失败不影响注册表其余部分（如 DB 抖动），只记日志
                log.warn("动态 agent 提供方 {} 读取失败，本轮跳过: {}", provider.namespace(), e.getMessage());
                continue;
            }
            if (agents == null) {
                continue;
            }
            for (AgentDescriptor item : agents) {
                String code = item.getAgentCode();
                if (!StringUtils.hasText(code)) {
                    continue;
                }
                String agentCode = provider.namespace() + ":" + code.trim();
                if (indexed.containsKey(agentCode)) {
                    // 冲突裁决：静态定义 > 动态数据——脏数据跳过不阻塞
                    log.warn("动态 agent {} 与静态定义冲突，跳过（静态定义优先）", agentCode);
                    continue;
                }
                item.setAgentCode(agentCode);
                item.setKind(AgentKind.DYNAMIC);
                if (!StringUtils.hasText(item.getDisplayName())) {
                    item.setDisplayName(code.trim());
                }
                if (!StringUtils.hasText(item.getModule())) {
                    item.setModule(provider.module());
                }
                indexed.put(agentCode, item);
            }
        }
        return new ArrayList<>(indexed.values());
    }

    /** 按 agentCode 查询（不存在返回 null） */
    public AgentDescriptor get(String agentCode) {
        if (!StringUtils.hasText(agentCode)) {
            return null;
        }
        for (AgentDescriptor descriptor : all()) {
            if (descriptor.getAgentCode().equals(agentCode)) {
                return descriptor;
            }
        }
        return null;
    }

    /**
     * 录入前置闸门（供业务管理端创建/修改动态 agent 时校验）：裸业务 code 拼装后
     * 不得与现有注册项重叠；非法字符（冒号/空白）直接拒绝
     */
    public void assertDynamicCodeAvailable(String namespace, String rawCode) {
        if (!StringUtils.hasText(rawCode) || rawCode.contains(":") || !rawCode.equals(rawCode.trim())) {
            throw new IllegalArgumentException("agent code 非法（须非空、不含冒号、无首尾空白）: " + rawCode);
        }
        String agentCode = namespace + ":" + rawCode.trim();
        if (get(agentCode) != null) {
            throw new IllegalArgumentException("agent code 已被注册: " + agentCode);
        }
    }
}
