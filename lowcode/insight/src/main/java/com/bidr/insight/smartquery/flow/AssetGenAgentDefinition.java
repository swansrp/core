package com.bidr.insight.smartquery.flow;

import com.bidr.insight.smartquery.dao.entity.InsightAgentTable;
import com.bidr.insight.smartquery.service.GenTaskContext;
import com.bidr.insight.smartquery.service.SmartAgentAssetGenerateService;
import com.bidr.llm.agent.AutonomousAgentDefinition;
import com.bidr.llm.agent.DetachPolicy;
import com.bidr.llm.agent.session.AgentSessionContext;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * Title: AssetGenAgentDefinition
 * Description: 资产生成自主模式的 agent 会话注册——原 /generate(mode=autonomous) 的
 * @Async 链路平移为会话驱动：start 内复用 beginGenerate 前置校验（含全局串行闸）与
 * runTask 同步体（骨架→AI 自主会话，任务线程/心跳/停止收口/finally 兜底不变），
 * 过程日志经 runTask 桥接会话事件流，暂停/停止经会话键跨实例可达。
 * 终态映射：stoppedByUser→STOPPED（InterruptedException）、failed→FAILED、正常→FINISHED+骨架摘要
 *
 * @author Sharp
 * @since 2026/8/20
 */
@Component
@RequiredArgsConstructor
public class AssetGenAgentDefinition implements AutonomousAgentDefinition {

    public static final String AGENT_KEY = "asset-gen-autonomous";

    /** 启动参数键（Controller 透传业务结构） */
    public static final String PAYLOAD_AGENT_CODE = "agentCode";

    private final SmartAgentAssetGenerateService service;

    @Override
    public String agentKey() {
        return AGENT_KEY;
    }

    @Override
    public String displayName() {
        return "资产生成（自主）";
    }

    @Override
    public String skillCode() {
        return AssetGenFlowDefinition.SKILL_CODE;
    }

    /** 功能型任务：前端断开（刷新/关抽屉）后台继续，经活跃会话列表重连跟进（不断开即停） */
    @Override
    public DetachPolicy detachPolicy() {
        return DetachPolicy.KEEP_RUNNING;
    }

    /** 作用对象=业务 agentCode：活跃会话列表按它定向重连本页面的生成任务 */
    @Override
    public String sessionSubject(Map<String, Object> payload) {
        Object code = payload == null ? null : payload.get(PAYLOAD_AGENT_CODE);
        return code == null ? null : String.valueOf(code);
    }

    @Override
    public void start(AgentSessionContext ctx, Map<String, Object> payload) throws Exception {
        Object code = payload == null ? null : payload.get(PAYLOAD_AGENT_CODE);
        String agentCode = code == null ? null : String.valueOf(code);
        if (agentCode == null || agentCode.trim().isEmpty()) {
            throw new IllegalArgumentException("启动参数缺少 agentCode");
        }
        // 前置校验+全局串行闸同步完成（异常直接抛出由会话层收口 FAILED；闸在 beginGenerate 内释放）
        List<InsightAgentTable> tables = service.beginGenerate(agentCode, GenTaskContext.MODE_AUTONOMOUS);
        GenTaskContext genCtx = service.runTask(agentCode, GenTaskContext.MODE_AUTONOMOUS,
                tables, ctx.getState().getOperator(), ctx);
        if (genCtx.isStoppedByUser()) {
            // runTask 内部已收口进度终态；此处转会话 STOPPED（会话层 catch InterruptedException）
            throw new InterruptedException("用户停止");
        }
        if (genCtx.isFailed()) {
            throw new IllegalStateException(
                    genCtx.getFailReason() == null ? "资产生成失败" : genCtx.getFailReason());
        }
        ctx.setSummary("资产草稿生成完成：实体 " + genCtx.getEntities().size()
                + " / 维度 " + genCtx.getDimensions().size()
                + " / 码值域 " + genCtx.getDomains().size() + "，发布后生效");
    }

    /**
     * 通用历史对话落盘：问题=自主生成说明（含业务 agentCode），会话收口后由会话层自动写单轮对话（agentCode=agentKey）
     */
    @Override
    public String conversationQuestion(Map<String, Object> payload) {
        Object code = payload == null ? null : payload.get(PAYLOAD_AGENT_CODE);
        return "自主生成资产草稿" + (code == null ? "" : "（" + code + "）");
    }
}
