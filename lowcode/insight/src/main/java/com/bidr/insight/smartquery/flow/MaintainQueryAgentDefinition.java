package com.bidr.insight.smartquery.flow;

import com.bidr.insight.smartquery.service.SmartQueryMaintainService;
import com.bidr.llm.agent.AutonomousAgentDefinition;
import com.bidr.llm.agent.session.AgentSessionContext;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * Title: MaintainQueryAgentDefinition
 * Description: 维护问数自主 agent 注册（计划 B3）：用户问题 → LLM 持工具自主规划闭环
 * （AgentExploreTools 探索缺口 → SemanticQueryTools 组装+dryRun 自纠+execute 真数据验证
 * → AssetProposalTools 落待审提案 → 结论作答），执行体在
 * {@link SmartQueryMaintainService#runAgentSession}；maxRounds=30、阶段随工具调用推进。
 * 旧一次性 /ask 链路保留并存（验证期不删）
 *
 * @author Sharp
 * @since 2026/8/20
 */
@Component
@RequiredArgsConstructor
public class MaintainQueryAgentDefinition implements AutonomousAgentDefinition {

    public static final String AGENT_KEY = "maintain-query";

    /** 启动参数键（Controller 透传业务结构） */
    public static final String PAYLOAD_AGENT_CODE = "agentCode";
    public static final String PAYLOAD_QUESTION = "question";

    private final SmartQueryMaintainService maintainService;

    @Override
    public String agentKey() {
        return AGENT_KEY;
    }

    @Override
    public String displayName() {
        return "维护问数（自主）";
    }

    @Override
    public String skillCode() {
        return AssetGenFlowDefinition.SKILL_CODE;
    }

    @Override
    public void start(AgentSessionContext ctx, Map<String, Object> payload) throws Exception {
        Object code = payload == null ? null : payload.get(PAYLOAD_AGENT_CODE);
        String agentCode = code == null ? null : String.valueOf(code);
        Object q = payload == null ? null : payload.get(PAYLOAD_QUESTION);
        String question = q == null ? null : String.valueOf(q);
        if (agentCode == null || agentCode.trim().isEmpty()
                || question == null || question.trim().isEmpty()) {
            throw new IllegalArgumentException("启动参数缺少 agentCode 或 question");
        }
        // 执行体：前置校验（Agent 存在/敏感闸/资产已发布）失败直接抛出由会话层收口 FAILED；
        // 用户停止抛 InterruptedException 收口 STOPPED；结论摘要由执行体写入 ctx
        maintainService.runAgentSession(ctx, agentCode.trim(), question.trim());
    }

    /**
     * 通用历史对话落盘：问题=用户提问原文，会话收口后由会话层自动写单轮对话。
     * 归属码与票据链/注册中心同构（smartquery:{业务 code}），前端历史抽屉可按页面所选 agent 过滤
     */
    @Override
    public String conversationQuestion(Map<String, Object> payload) {
        Object q = payload == null ? null : payload.get(PAYLOAD_QUESTION);
        return q == null ? null : String.valueOf(q);
    }

    @Override
    public String conversationAgentCode(Map<String, Object> payload) {
        Object code = payload == null ? null : payload.get(PAYLOAD_AGENT_CODE);
        String c = code == null ? null : String.valueOf(code).trim();
        return c == null || c.isEmpty() ? AGENT_KEY
                : SmartQueryDynamicAgentProvider.NAMESPACE + ":" + c;
    }

    /** 会话作用对象=业务 agentCode：活跃列表按它定向重连（测试页刷新后找回本 agent 的进行中会话） */
    @Override
    public String sessionSubject(Map<String, Object> payload) {
        Object code = payload == null ? null : payload.get(PAYLOAD_AGENT_CODE);
        return code == null ? null : String.valueOf(code);
    }
}
