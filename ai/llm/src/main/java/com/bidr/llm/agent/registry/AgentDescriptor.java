package com.bidr.llm.agent.registry;

import lombok.Data;

import java.util.HashMap;
import java.util.Map;

/**
 * Title: AgentDescriptor
 * Description: 统一注册中心的 agent 描述——三类执行形态（flow/autonomous/dynamic）归一后的清单条目，
 * 供前端历史对话/评价按 agentCode 归组展示与注册表管理页渲染。
 * <p>
 * agentCode 命名空间约定（冲突防护第一道）：
 * <ul>
 *     <li>FLOW：flow:{flowKey}（如 flow:ask）</li>
 *     <li>AUTONOMOUS：agentKey 原样（代码约定 skill-业务 形式，如 asset-gen-autonomous）</li>
 *     <li>DYNAMIC：{provider 命名空间前缀}:{业务 code}（如 smartquery:poc），
 *     前缀由 {@link DynamicAgentProvider#namespace()} 声明</li>
 * </ul>
 *
 * @author Sharp
 * @since 2026/8/22
 */
@Data
public class AgentDescriptor {

    /** flow 型 agentCode 保留前缀（命名空间隔离；动态注册禁用此前缀） */
    public static final String FLOW_PREFIX = "flow:";

    /** 全局唯一标识（命名空间规则见类注释） */
    private String agentCode;

    /** 显示名（注册表与前端标题） */
    private String displayName;

    /** 类型（FLOW / AUTONOMOUS / DYNAMIC） */
    private AgentKind kind;

    /** 归属 skill（评价与轨迹聚合维度；可为空） */
    private String skillCode;

    /** 来源模块标识（如 insight；静态定义为 Bean 所在模块约定值） */
    private String module;

    /** 业务扩展（如动态 agent 的原始业务 code；前端按需消费） */
    private Map<String, Object> meta = new HashMap<>();
}
