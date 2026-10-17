package com.bidr.insight.smartquery.flow.executor;

import com.bidr.insight.smartquery.flow.AssetGenFlowDefinition;
import com.bidr.insight.smartquery.service.SmartAgentAssetGenerateService;
import com.bidr.llm.flow.FlowContext;
import com.bidr.llm.flow.FlowGraph;
import com.bidr.llm.flow.FlowNodeMeta;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

/**
 * Title: AssetLlmNodeExecutor
 * Description: LLM 逐类生成结点（metrics/relations/concepts 共用一类执行器）——
 * 单类资产多轮工具探索生成；指标多表时服务内部走逐表子链路（formula 严格单表+增量落盘）；
 * 单类失败不阻断链路（记入任务失败清单，收口结点汇总置失败终态）。
 * config 持 assetType（资产类型）+ promptKey（提示词模板键，前端可调）。
 *
 * @author Sharp
 * @since 2026/8/20
 */
@Component
@RequiredArgsConstructor
public class AssetLlmNodeExecutor implements com.bidr.llm.flow.executor.FlowNodeExecutor {

    private final ObjectProvider<SmartAgentAssetGenerateService> service;

    @Override
    public String type() {
        return "asset-llm";
    }

    @Override
    public FlowNodeMeta nodeMeta() {
        FlowNodeMeta meta = FlowNodeMeta.of(type(), "LLM 资产生成",
                "单类资产多轮工具探索生成（指标/关系/概念共用；指标多表自动逐表）");
        meta.getFields().add(FlowNodeMeta.ConfigField.select("assetType", "资产类型",
                new FlowNodeMeta.Option("metrics", "指标"),
                new FlowNodeMeta.Option("relations", "关系"),
                new FlowNodeMeta.Option("concepts", "业务概念"))
                .defaultValue("metrics"));
        meta.getFields().add(FlowNodeMeta.ConfigField
                .text("promptKey", "提示词模板键", "effectivePrompts 中的键，改动须与模板管理一致")
                .defaultValue("metricsPrompt"));
        meta.getFields().add(FlowNodeMeta.ConfigField
                .text("estimatedSecs", "预计耗时(秒)", "AgentStages 预计剩余时间用，可改")
                .defaultValue(180));
        return meta;
    }

    @Override
    public boolean execute(FlowGraph.FlowNode node, FlowContext context) {
        String assetType = strConfig(node.getConfig(), "assetType", "metrics");
        String promptKey = strConfig(node.getConfig(), "promptKey", assetType + "Prompt");
        service.getObject().flowAsset(AssetGenFlowDefinition.genCtx(context), assetType, promptKey);
        return true;
    }

    @Override
    public String traceSummary(FlowGraph.FlowNode node, FlowContext context, boolean suspended) {
        String assetType = strConfig(node.getConfig(), "assetType", "metrics");
        return "资产「" + assetType + "」生成完成（多轮探索明细见过程日志与草稿）";
    }
}
