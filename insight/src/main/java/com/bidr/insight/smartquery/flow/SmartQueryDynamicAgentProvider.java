package com.bidr.insight.smartquery.flow;

import com.bidr.insight.smartquery.dao.entity.InsightAgent;
import com.bidr.insight.smartquery.dao.repository.InsightAgentService;
import com.bidr.llm.agent.registry.AgentDescriptor;
import com.bidr.llm.agent.registry.DynamicAgentProvider;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.util.ArrayList;
import java.util.List;

/**
 * Title: SmartQueryDynamicAgentProvider
 * Description: 智能问数动态 agent 注册——按 insight_agent 表逐行纳入 llm 统一注册中心
 * （命名空间 smartquery，agentCode=smartquery:{agent_code}，随 DB 增删实时生效）。
 * <p>
 * 冲突防护口径：命名空间前缀结构性隔离（与 flow:/自主 agentKey 不可能重叠）；
 * 命名空间内唯一性由 insight_agent.agent_code 唯一约束保证（通用管理端录入）。
 * 停用的 Agent 同样注册（历史对话/评价回看需要），状态经 meta 透出
 *
 * @author Sharp
 * @since 2026/8/22
 */
@Component
public class SmartQueryDynamicAgentProvider implements DynamicAgentProvider {

    /** 命名空间（全局唯一，注册中心启动时校验） */
    public static final String NAMESPACE = "smartquery";

    @Resource
    private InsightAgentService insightAgentService;

    @Override
    public String namespace() {
        return NAMESPACE;
    }

    @Override
    public String module() {
        return "insight";
    }

    @Override
    public List<AgentDescriptor> agents() {
        List<InsightAgent> rows = insightAgentService.select();
        List<AgentDescriptor> result = new ArrayList<>();
        if (rows == null) {
            return result;
        }
        for (InsightAgent agent : rows) {
            AgentDescriptor descriptor = new AgentDescriptor();
            // 裸业务 code，注册中心统一拼 smartquery:{code}
            descriptor.setAgentCode(agent.getAgentCode());
            descriptor.setDisplayName(agent.getAgentName());
            descriptor.setSkillCode("smartquery");
            descriptor.setModule(module());
            descriptor.getMeta().put("status", agent.getStatus());
            descriptor.getMeta().put("dsName", agent.getDsName());
            result.add(descriptor);
        }
        return result;
    }
}
