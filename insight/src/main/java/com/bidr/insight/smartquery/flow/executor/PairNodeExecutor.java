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
 * Title: PairNodeExecutor
 * Description: 配对推断结点——LLM 逐表推断「编码字段↔业务名称字段」配对与备注枚举域，
 * 验证真实存在后由后端 GROUP BY 采样真实映射补齐码值域（不阻断后续逐类生成）。
 * promptKey 指向提示词模板键（前端可调，缺省 pairPrompt）。
 *
 * @author Sharp
 * @since 2026/8/20
 */
@Component
@RequiredArgsConstructor
public class PairNodeExecutor implements com.bidr.llm.flow.executor.FlowNodeExecutor {

    private final ObjectProvider<SmartAgentAssetGenerateService> service;

    @Override
    public String type() {
        return "asset-pair";
    }

    @Override
    public FlowNodeMeta nodeMeta() {
        FlowNodeMeta meta = FlowNodeMeta.of(type(), "配对推断",
                "LLM 逐表推断编码↔名称配对与备注枚举域，后端采样补齐码值域");
        meta.getFields().add(FlowNodeMeta.ConfigField
                .text("promptKey", "提示词模板键", "effectivePrompts 中的键，改动须与模板管理一致")
                .defaultValue("pairPrompt"));
        meta.getFields().add(FlowNodeMeta.ConfigField
                .text("estimatedSecs", "预计耗时(秒)", "AgentStages 预计剩余时间用，可改")
                .defaultValue(120));
        return meta;
    }

    @Override
    public boolean execute(FlowGraph.FlowNode node, FlowContext context) {
        String promptKey = strConfig(node.getConfig(), "promptKey", "pairPrompt");
        service.getObject().flowPair(AssetGenFlowDefinition.genCtx(context), promptKey);
        return true;
    }

    @Override
    public String traceSummary(FlowGraph.FlowNode node, FlowContext context, boolean suspended) {
        return "配对推断完成（逐表小请求，明细见过程日志与码值域草稿）";
    }
}
