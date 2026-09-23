package com.bidr.agent.runtime.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Title: AgentRuntimeProperties
 * Description: Agent Runtime 接入的应用配置（{@code my.agent.runtime.*}）——系统参数（sys_config）的回落层：
 * 取值顺序为「系统参数优先 → 本配置回落」，统一经 {@link AgentRuntimeConfigProvider} 读取，
 * 业务代码不直接注入本类。
 * <p>
 * 本类中的阈值默认值仅为「未配置时的安全默认」：生产值经系统参数管理页（{@code AGENT_RUNTIME_*}）下发，
 * 应用配置只承载环境相关项（地址、密钥）与本地开发默认。
 * <p>
 * 本模块按「可直接下沉」的形态组织：包名 com.bidr.agent.runtime、@AutoConfiguration 装配、
 * 零项目引用（守卫单测断言），故下沉 core（agent-runtime-client）时只需搬目录 + 改 pom parent，
 * yml 键与包名都不用改。
 *
 * @author sharp
 * @since 2026/9/22
 */
@Getter
@Setter
@ConfigurationProperties(prefix = "my.agent.runtime")
public class AgentRuntimeProperties {

    /**
     * 装配开关：false 时本模块全部 Bean 不装配（见 {@link AgentRuntimeAutoConfiguration}）
     */
    private boolean enabled = true;

    /**
     * 上游运行时实现：{@code agent-system}（开放协议 + SSE 单轮流）或 {@code openhands}
     * （agent-server REST + 会话级 WS，事件在 provider 内映射成 §5.7 帧）。
     * <p>
     * ⚠️ **切换需重启**，不做热切：两家的会话/轮次 id 语义与历史形状不同，运行中混切会让
     * 已登记会话的历史读错路。故本项**只在应用配置**，不进系统参数管理页（避免被误当可热改参数）。
     */
    private String provider = "agent-system";

    /**
     * 平台开放面地址（如 http://127.0.0.1:8100）；留空=未接线
     */
    private String baseUrl = "";

    /**
     * 平台开放面密钥（sk-rt- 开头）；占位符或留空=未填写，生产经系统参数管理页下发
     */
    private String apiKey = "";

    /**
     * 开放面接口前缀
     */
    private String apiPrefix = "/open/v1";

    /**
     * 建连超时（毫秒）
     */
    private int connectTimeoutMs = 5000;

    /**
     * 上游单轮流空闲读超时（毫秒）：超过即视为断流，交由客户端挂流恢复
     */
    private int idleTimeoutMs = 120000;

    /**
     * 浏览器侧注释心跳周期（秒）
     */
    private int heartbeatSeconds = 15;

    /**
     * SSE 中转泵线程池上限：每活跃轮次占 1 线程 + 1 条上游连接
     */
    private int relayThreads = 32;

    // ==================== OpenHands 实现专用（provider=openhands 时生效） ====================

    /**
     * 会话工作区根（上游容器内绝对路径；一个会话一个子目录）
     */
    private String workspaceRoot = "/workspace";

    /**
     * 建会话时引用的 agent profile 名（其 llm_profile_ref 需在上游存在；上游无该名则用其激活档案）
     */
    private String agentProfile = "default";

    /**
     * 单轮最多多少步工具调用（上游 max_iterations；防跑飞，也直接决定单轮成本上限）
     */
    private int maxIterations = 30;
}