package com.bidr.llm.agent.registry;

import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Title: AgentRegistryController
 * Description: 统一 Agent 注册中心端点（llm 基础框架，业务零绑定）——三类 agent
 * （flow/autonomous/dynamic）归一清单，供前端历史对话/评价按 agentCode 归组与注册表展示。
 * 装配与鉴权口径同 {@link com.bidr.llm.agent.AgentSessionController}（/web/api 拦截链）。
 *
 * @author Sharp
 * @since 2026/8/22
 */
@RestController
@RequestMapping("/web/api/agent")
@RequiredArgsConstructor
@ConditionalOnClass(name = "org.springframework.web.servlet.mvc.method.annotation.SseEmitter")
@ConditionalOnProperty(prefix = "llm.agent-api", name = "enabled", havingValue = "true", matchIfMissing = true)
public class AgentRegistryController {

    private final AgentRegistryService agentRegistryService;

    /** 统一注册清单（三类归一，实时聚合动态项） */
    @GetMapping("/registry")
    public List<AgentDescriptor> registry() {
        return agentRegistryService.all();
    }

    /** 按 agentCode 查单条（不存在返回空响应体） */
    @GetMapping("/registry/{agentCode}")
    public AgentDescriptor registryItem(@PathVariable("agentCode") String agentCode) {
        return agentRegistryService.get(agentCode);
    }
}
