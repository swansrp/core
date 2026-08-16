package com.bidr.insight.flow.executor;

import com.bidr.insight.service.ChatBiSemanticService;
import com.bidr.insight.vo.ChatBiSemanticCatalog;
import com.bidr.llm.flow.FlowContext;
import com.bidr.llm.flow.FlowGraph;
import com.bidr.llm.flow.FlowNodeMeta;
import com.bidr.llm.flow.FlowNodeMeta.ConfigField;
import com.bidr.llm.flow.executor.FlowNodeExecutor;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

/**
 * Title: SemanticNodeExecutor
 * Description: semantic 结点——按 tableId 构建语义目录：
 * 目录 JSON 写入 outputVar（默认 catalog）供 llm 模板 {@code {{catalog}}} 引用，
 * 同时归一化写入 tableId/portalName 变量。
 *
 * @author Sharp
 * @since 2026/8/15
 */
@Component
@RequiredArgsConstructor
public class SemanticNodeExecutor implements FlowNodeExecutor {

    private final ChatBiSemanticService chatBiSemanticService;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public String type() {
        return "semantic";
    }

    /**
     * 工作台元数据：两个变量名配置（defaultValue 与执行兜底一致）
     */
    @Override
    public FlowNodeMeta nodeMeta() {
        FlowNodeMeta meta = FlowNodeMeta.of(type(), type(), "按 tableId 构建语义目录 JSON 写入 catalog");
        meta.setHint("目录 JSON 写入输出变量供 llm 模板引用，同时归一化写回 tableId/portalName。");
        meta.setFields(Arrays.asList(
                ConfigField.text("tableIdVar", "tableId 变量名", "默认 tableId").defaultValue("tableId"),
                ConfigField.text("outputVar", "输出变量名", "默认 catalog").defaultValue("catalog")
        ));
        return meta;
    }

    @Override
    public boolean execute(FlowGraph.FlowNode node, FlowContext context) {
        Map<String, Object> config = node.getConfig();
        String tableIdVar = strConfig(config, "tableIdVar", "tableId");
        String outputVar = strConfig(config, "outputVar", "catalog");
        ChatBiSemanticCatalog catalog = chatBiSemanticService.getSemanticCatalog(context.getString(tableIdVar));
        String catalogJson;
        try {
            catalogJson = objectMapper.writeValueAsString(catalog);
        } catch (Exception e) {
            throw new IllegalStateException("语义目录序列化失败", e);
        }
        context.setVariable(outputVar, catalogJson);
        context.setVariable("tableId", catalog.getTableId());
        context.setVariable("portalName", catalog.getPortalName());
        return true;
    }

    /**
     * 轨迹摘要：目录 JSON 字数（同 outputVar 配置变量）
     */
    @Override
    public String traceSummary(FlowGraph.FlowNode node, FlowContext context, boolean suspended) {
        String catalog = context.getString(strConfig(node.getConfig(), "outputVar", "catalog"));
        return "语义目录 " + catalog.length() + " 字";
    }
}
