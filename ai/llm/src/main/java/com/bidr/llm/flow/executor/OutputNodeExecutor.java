package com.bidr.llm.flow.executor;

import com.bidr.llm.flow.FlowContext;
import com.bidr.llm.flow.FlowGraph;
import com.bidr.llm.flow.FlowNodeMeta;
import com.bidr.llm.flow.FlowNodeMeta.ConfigField;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Title: OutputNodeExecutor
 * Description: output 结点——把变量映射到响应字段：config.outputs 为
 * 「响应字段名 → 变量名」映射表，链路结束后调用方从 ctx.getOutput() 取回响应结构。
 *
 * @author Sharp
 * @since 2026/8/16
 */
@Component
public class OutputNodeExecutor implements FlowNodeExecutor {

    /**
     * 输出变量摘要截断长度（字，超长保时间线可读，全文走 detail）
     */
    private static final int SUMMARY_MAX_CHARS = 120;

    @Override
    public String type() {
        return "output";
    }

    /**
     * 工作台元数据：输出映射表（defaultValue 空表即画布新增结点的初始 config）
     */
    @Override
    public FlowNodeMeta nodeMeta() {
        FlowNodeMeta meta = FlowNodeMeta.of(type(), type(), "把变量映射为链路响应字段");
        meta.setFields(Arrays.asList(
                ConfigField.outputMap("outputs", "输出映射").defaultValue(new LinkedHashMap<>())
        ));
        return meta;
    }

    @Override
    public boolean execute(FlowGraph.FlowNode node, FlowContext context) {
        Object outputs = node.getConfig().get("outputs");
        if (outputs instanceof Map) {
            for (Map.Entry<?, ?> entry : ((Map<?, ?>) outputs).entrySet()) {
                String fieldName = String.valueOf(entry.getKey());
                String varName = String.valueOf(entry.getValue());
                context.putOutput(fieldName, context.getVariable(varName));
            }
        }
        return true;
    }

    @Override
    public String traceSummary(FlowGraph.FlowNode node, FlowContext context, boolean suspended) {
        String out = String.valueOf(context.getOutput());
        return "输出变量: " + (out.length() > SUMMARY_MAX_CHARS
                ? out.substring(0, SUMMARY_MAX_CHARS) + "…（共 " + out.length() + " 字）" : out);
    }

    @Override
    public String traceDetail(FlowGraph.FlowNode node, FlowContext context, boolean suspended) {
        Map<String, Object> out = context.getOutput();
        if (out.isEmpty()) {
            return null;
        }
        StringBuilder text = new StringBuilder("【输出变量】");
        for (Map.Entry<String, Object> entry : out.entrySet()) {
            text.append("\n").append(entry.getKey()).append(" = ").append(entry.getValue());
        }
        return text.toString();
    }
}
