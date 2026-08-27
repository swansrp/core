package com.bidr.insight.smartquery.flow.executor;

import com.bidr.insight.smartquery.flow.AssetGenFlowDefinition;
import com.bidr.insight.smartquery.service.GenTaskContext;
import com.bidr.insight.smartquery.service.SmartAgentAssetGenerateService;
import com.bidr.llm.flow.FlowContext;
import com.bidr.llm.flow.FlowGraph;
import com.bidr.llm.flow.FlowNodeMeta;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

/**
 * Title: SensitiveGateNodeExecutor
 * Description: 敏感闸结点——LLM 生成（配对推断/工具探索会采样真实映射）前的强制闸：
 * 敏感治理未就绪即抛错收链（骨架不落盘白做）；skeleton 模式直通（骨架模式的
 * 「已配置才清理」在骨架结点内做）。敏感标记在此预载，供后续配对/逐类生成强制层使用。
 *
 * @author Sharp
 * @since 2026/8/20
 */
@Component
@RequiredArgsConstructor
public class SensitiveGateNodeExecutor implements com.bidr.llm.flow.executor.FlowNodeExecutor {

    /** 惰性取业务服务（避免与服务 FlowEngine 注入构成构造环） */
    private final ObjectProvider<SmartAgentAssetGenerateService> service;

    @Override
    public String type() {
        return "asset-sensitive-gate";
    }

    @Override
    public FlowNodeMeta nodeMeta() {
        return FlowNodeMeta.of(type(), "敏感闸", "敏感治理强制闸：未就绪即收链；skeleton 模式直通");
    }

    @Override
    public boolean execute(FlowGraph.FlowNode node, FlowContext context) {
        service.getObject().flowSensitiveGate(genCtx(context));
        return true;
    }

    private GenTaskContext genCtx(FlowContext context) {
        return AssetGenFlowDefinition.genCtx(context);
    }
}
