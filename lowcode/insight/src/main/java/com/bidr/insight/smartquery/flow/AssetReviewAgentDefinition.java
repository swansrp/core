package com.bidr.insight.smartquery.flow;

import com.bidr.insight.smartquery.layer.EntityDef;
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
 * Title: AssetReviewAgentDefinition
 * Description: AI 评审自主模式的 agent 会话注册——只读复核实体认证结论（列角色/单位/业务键/
 * 分区/归类口径），产出结构化评审报告（review-report 草稿）供管理员逐条处理。
 * 评审链不注册任何写工具（无 save_draft），"评审不修改"由工具集结构保证；
 * 与生成链共全局串行闸（beginReview 内拿闸，runReview finally 释放），停止/暂停经会话键跨实例可达。
 * 终态映射：stoppedByUser→STOPPED（InterruptedException）、failed→FAILED、正常→FINISHED+评审摘要
 *
 * @author Sharp
 * @since 2026/8/25
 */
@Component
@RequiredArgsConstructor
public class AssetReviewAgentDefinition implements AutonomousAgentDefinition {

    public static final String AGENT_KEY = "asset-review-autonomous";

    /** 启动参数键（Controller 透传业务结构） */
    public static final String PAYLOAD_AGENT_CODE = "agentCode";

    private final SmartAgentAssetGenerateService service;

    @Override
    public String agentKey() {
        return AGENT_KEY;
    }

    @Override
    public String displayName() {
        return "资产评审（自主）";
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

    /** 作用对象=业务 agentCode：活跃会话列表按它定向重连本页面的评审任务 */
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
        // 前置校验+全局串行闸同步完成（异常直接抛出由会话层收口 FAILED；闸在 runReview 内释放）
        List<EntityDef> entities = service.beginReview(agentCode);
        GenTaskContext reviewCtx = service.runReview(agentCode, entities, ctx);
        if (reviewCtx.isStoppedByUser()) {
            throw new InterruptedException("用户停止");
        }
        if (reviewCtx.isFailed()) {
            throw new IllegalStateException(
                    reviewCtx.getFailReason() == null ? "AI 评审失败" : reviewCtx.getFailReason());
        }
        ctx.setSummary("AI 评审完成：只读复核 " + entities.size() + " 张表的实体结论，"
                + "评审报告见实体区「评审报告」");
    }

    /**
     * 通用历史对话落盘：问题=评审说明（含业务 agentCode），会话收口后由会话层自动写单轮对话（agentCode=agentKey）
     */
    @Override
    public String conversationQuestion(Map<String, Object> payload) {
        Object code = payload == null ? null : payload.get(PAYLOAD_AGENT_CODE);
        return "AI 评审实体结论" + (code == null ? "" : "（" + code + "）");
    }
}
