package com.bidr.insight.smartquery.flow.executor;

import com.bidr.insight.smartquery.dao.entity.InsightAgentTable;
import com.bidr.insight.smartquery.flow.AssetGenFlowDefinition;
import com.bidr.insight.smartquery.service.GenTaskContext;
import com.bidr.insight.smartquery.service.SmartAgentAssetGenerateService;
import com.bidr.llm.flow.FlowContext;
import com.bidr.llm.flow.FlowGraph;
import com.bidr.llm.flow.FlowNodeMeta;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Title: SkeletonNodeExecutor
 * Description: 骨架生成结点——逐表读结构+采样码值构建骨架，敏感残留清理后骨架三类先落盘
 * （LLM 逐类生成以其为上下文，失败也不丢骨架成果）。出边经 mode 条件分流：
 * skeleton 模式直通收口，pipeline 进入 LLM 段。
 *
 * @author Sharp
 * @since 2026/8/20
 */
@Component
@RequiredArgsConstructor
public class SkeletonNodeExecutor implements com.bidr.llm.flow.executor.FlowNodeExecutor {

    private final ObjectProvider<SmartAgentAssetGenerateService> service;

    @Override
    public String type() {
        return "asset-skeleton";
    }

    @Override
    public FlowNodeMeta nodeMeta() {
        FlowNodeMeta meta = FlowNodeMeta.of(type(), "骨架生成",
                "逐表读结构+采样码值构建骨架并落盘（三模式共用前置）");
        meta.getFields().add(FlowNodeMeta.ConfigField
                .text("estimatedSecs", "预计耗时(秒)", "AgentStages 预计剩余时间用，可改")
                .defaultValue(30));
        return meta;
    }

    @Override
    public boolean execute(FlowGraph.FlowNode node, FlowContext context) {
        List<InsightAgentTable> tables = context.get(AssetGenFlowDefinition.VAR_TABLES, List.class);
        if (tables == null || tables.isEmpty()) {
            throw new IllegalStateException("流程上下文缺少 tables 变量（须由 handleTask 注入）");
        }
        service.getObject().flowSkeleton(genCtx(context), tables);
        return true;
    }

    @Override
    public String traceSummary(FlowGraph.FlowNode node, FlowContext context, boolean suspended) {
        GenTaskContext ctx = AssetGenFlowDefinition.genCtx(context);
        return "骨架构建完成并落盘：实体 " + ctx.getEntities().size()
                + " / 维度 " + ctx.getDimensions().size()
                + " / 码值域 " + ctx.getDomains().size();
    }

    private GenTaskContext genCtx(FlowContext context) {
        return AssetGenFlowDefinition.genCtx(context);
    }
}
