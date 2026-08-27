package com.bidr.llm.agent.registry;

import java.util.List;

/**
 * Title: DynamicAgentProvider
 * Description: 动态 agent 注册 SPI——业务模块把「数据驱动的 agent」纳入统一注册中心
 * （如 insight 按 InsightAgent 表逐行注册，随 DB 增删）。实现注册为 Spring Bean 即被
 * {@link AgentRegistryService} 聚合；每次读注册表实时调用 {@link #agents()}，无需重启感知变更。
 * <p>
 * 命名空间隔离（冲突防护）：每个实现声明独立 {@link #namespace()} 前缀，注册中心统一拼装
 * agentCode={namespace}:{业务 code}；前缀互不相同且不得占用保留前缀（启动时校验）。
 *
 * @author Sharp
 * @since 2026/8/22
 */
public interface DynamicAgentProvider {

    /** 命名空间前缀（如 smartquery；全局唯一，不得为 flow，不得含冒号） */
    String namespace();

    /** 来源模块标识（如 insight） */
    default String module() {
        return "unknown";
    }

    /**
     * 当前动态 agent 清单：agentCode 填裸业务 code（注册中心统一加命名空间前缀），
     * kind 固定 DYNAMIC；displayName 缺省时注册中心回落业务 code
     */
    List<AgentDescriptor> agents();
}
