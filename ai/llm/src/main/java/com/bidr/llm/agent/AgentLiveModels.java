package com.bidr.llm.agent;

import com.bidr.llm.agent.session.AgentSessionContext;
import com.bidr.llm.model.LiveModelFactory;
import com.bidr.llm.model.StreamingProgressChatModel;
import com.bidr.llm.provider.DbAwareModelConfigProvider;
import dev.langchain4j.model.chat.ChatLanguageModel;

import java.util.function.Supplier;

/**
 * Title: AgentLiveModels
 * Description: Agent 会话的流式进度模型装配点——把各业务 agent 同构的 buildLiveModel
 * 归一进框架（原先在 DocWriterAgent/LogReportAgent 各抄一份）：思考/应答状态行、
 * 等待心跳与流式降级提示走 {@link AgentSessionContext#pushLive}（前端实时思考块逐秒刷新），
 * 其余过程行（轮末思考归档等）走 {@link AgentSessionContext#log}（事件流留痕）。
 * 行分流判据＝{@link StreamingProgressChatModel#isLiveStatusLine}（与发射端同源）；
 * 工厂或框架 Provider 缺席时回落调用方同步模型，行为与未接线一致。
 *
 * @author Sharp
 * @since 2026/10/16
 */
public final class AgentLiveModels {

    private AgentLiveModels() {
    }

    /**
     * @param factory            LiveModelFactory（ObjectProvider.getIfAvailable() 直传，允许 null）
     * @param ctx                agent 会话上下文（live 替换通道与事件流都在其上）
     * @param noProviderFallback 工厂/Provider 缺席时的同步模型回落（懒求值，通常是原 chatModel——
     *                           回落侧可能带「未配置模型即抛」语义，不能在已接线时被提前触发）
     */
    public static ChatLanguageModel forSession(LiveModelFactory factory, AgentSessionContext ctx,
            Supplier<ChatLanguageModel> noProviderFallback) {
        if (factory == null) {
            return noProviderFallback.get();
        }
        return factory.build(DbAwareModelConfigProvider.PURPOSE_AGENT, line -> {
            if (StreamingProgressChatModel.isLiveStatusLine(line)) {
                ctx.pushLive(line);
            } else {
                ctx.log(line);
            }
        }, noProviderFallback, null);
    }
}
