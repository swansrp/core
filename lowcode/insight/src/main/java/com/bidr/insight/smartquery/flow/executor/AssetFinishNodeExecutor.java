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
 * Title: AssetFinishNodeExecutor
 * Description: 落盘收口结点——补齐落盘骨架（LLM 推断配对与工具探索登记新增的码值域/维度不丢，
 * 人工四类非空保留不受影响）；逐类失败清单非空时进度置 FAILED 终态（comments 逐类列出成败原因）。
 *
 * @author Sharp
 * @since 2026/8/20
 */
@Component
@RequiredArgsConstructor
public class AssetFinishNodeExecutor implements com.bidr.llm.flow.executor.FlowNodeExecutor {

    private final ObjectProvider<SmartAgentAssetGenerateService> service;

    @Override
    public String type() {
        return "asset-finish";
    }

    @Override
    public FlowNodeMeta nodeMeta() {
        return FlowNodeMeta.of(type(), "落盘收口", "补齐落盘骨架三类；逐类失败清单汇总置终态");
    }

    @Override
    public boolean execute(FlowGraph.FlowNode node, FlowContext context) {
        service.getObject().flowFinish(AssetGenFlowDefinition.genCtx(context));
        return true;
    }

    @Override
    public String traceSummary(FlowGraph.FlowNode node, FlowContext context, boolean suspended) {
        GenTaskContext ctx = AssetGenFlowDefinition.genCtx(context);
        return ctx.getFlowFailures().isEmpty()
                ? "草稿全部落盘完成"
                : "落盘完成，但 " + ctx.getFlowFailures().size() + " 类生成失败（已置失败终态）";
    }
}
