package com.bidr.agent.runtime.constant.param;

import com.bidr.kernel.constant.param.MetaParam;
import com.bidr.kernel.constant.param.Param;
import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * Title: AgentRuntimeParam
 * Description: Agent Runtime 平台接入系统参数——@MetaParam 由 SysConfigCacheService 启动扫描自动补进
 * sys_config，管理页修改后广播生效（{@code AgentRuntimeConfigProvider} 每次调用实时读取）。
 * <p>
 * 数据库值优先于应用配置 {@code my.agent.runtime.*}；密钥默认值为占位符（sk-****），
 * 含 {@code *} 或留空时视为未填写、回落应用配置，避免明文密钥进代码仓库。
 * <p>
 * 键名统一带 {@code AGENT_RUNTIME_} 前缀：sys_config 的 config_key 即枚举名，跨模块必须全局唯一
 * （否则与 {@code LlmParam.BASE_URL} 等既有参数相撞）。
 *
 * @author sharp
 * @since 2026/9/22
 */
@Getter
@MetaParam
@AllArgsConstructor
public enum AgentRuntimeParam implements Param {

    /**
     * 留空回落应用配置 my.agent.runtime.base-url
     */
    AGENT_RUNTIME_BASE_URL("Agent平台服务地址", "",
            "Agent Runtime 开放面地址，如 http://10.3.5.103:8100；留空回落应用配置 my.agent.runtime.base-url"),

    /**
     * 占位符 sk-****：真实密钥须在系统参数管理页手动填写
     */
    AGENT_RUNTIME_API_KEY("Agent平台密钥", "sk-****",
            "平台开放面密钥（sk-rt- 开头）；占位符或留空时视为未填写，回落应用配置 my.agent.runtime.api-key"),

    AGENT_RUNTIME_API_PREFIX("Agent平台接口前缀", "",
            "开放面接口前缀，默认 /open/v1；留空回落应用配置 my.agent.runtime.api-prefix"),

    AGENT_RUNTIME_CONNECT_TIMEOUT_MS("Agent平台连接超时(毫秒)", "",
            "建连超时；留空回落应用配置 my.agent.runtime.connect-timeout-ms"),

    AGENT_RUNTIME_IDLE_TIMEOUT_MS("Agent单轮流空闲超时(毫秒)", "",
            "上游单轮流多久无数据即视为断流（平台 15 秒心跳，勿低于 60 秒）；留空回落应用配置 my.agent.runtime.idle-timeout-ms"),

    AGENT_RUNTIME_HEARTBEAT_SECONDS("Agent中转心跳(秒)", "",
            "浏览器侧注释心跳周期，防反代空闲断连；留空回落应用配置 my.agent.runtime.heartbeat-seconds"),

    AGENT_RUNTIME_RELAY_THREADS("Agent中转线程数", "",
            "SSE 中转泵线程池上限（每活跃轮次占 1 线程 + 1 条上游连接）；留空回落应用配置 my.agent.runtime.relay-threads");

    private final String title;
    private final String defaultValue;
    private final String remark;
}