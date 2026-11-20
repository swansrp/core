package com.bidr.llm.agent.session;

import java.util.Map;

/**
 * Title: SessionContextContributor
 * Description: 会话宿主上下文贡献者——在 {@code session/start} 的<b>请求线程</b>调用，把只有请求线程
 * 可见的宿主信息（如 {@code SpaceContext} 当前项目、{@code AccountContext} 登录工号等 ThreadLocal
 * 数据）抓成键值快照，写入 {@link AgentSessionState#getContext()} 随会话快照持久化：
 * run 线程由 {@link AgentSessionService#start} 另起，读不到请求线程的 ThreadLocal，
 * 工具侧只能从这里取「本会话属于哪个项目/谁发起」。
 * <p>
 * 约定：
 * <ul>
 *     <li>只在 start 的请求线程调用一次；返回值随快照持久化（须 Jackson 可序列化）；</li>
 *     <li>返回 {@code null} 或空 map ＝ 本会话无宿主上下文（等价于未装配贡献者）；</li>
 *     <li>实现方抛异常由框架 {@code log.warn} 后按空 map 处理，<b>不阻断起会话</b>——
 *         宿主上下文是增强信息，缺席时业务侧应有可读兜底。</li>
 * </ul>
 * 装配为可选：容器内零个或多个实现都不影响启动（多实现按「无唯一贡献者」跳过）。
 *
 * @author Sharp
 * @since 2026/11/20
 */
public interface SessionContextContributor {

    /**
     * 采集本轮会话的宿主上下文快照（在 start 的请求线程调用）。
     *
     * @return 键值快照；{@code null}/空 map ＝ 无贡献（按「无宿主上下文」处理）
     */
    Map<String, Object> contribute();
}