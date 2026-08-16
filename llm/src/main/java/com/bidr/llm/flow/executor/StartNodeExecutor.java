package com.bidr.llm.flow.executor;

import com.bidr.llm.flow.FlowContext;
import com.bidr.llm.flow.FlowGraph;
import com.bidr.llm.flow.FlowNodeMeta;
import org.springframework.stereotype.Component;

/**
 * Title: StartNodeExecutor
 * Description: start 结点——链路起点锚点，执行为空操作：
 * 输入变量（question/history 等业务输入）由调用方在 {@code engine.execute} 前注入上下文，
 * start 仅保证链有唯一起点供引擎定位。
 *
 * @author Sharp
 * @since 2026/8/16
 */
@Component
public class StartNodeExecutor implements FlowNodeExecutor {

    @Override
    public String type() {
        return "start";
    }

    /**
     * 工作台元数据：入口锚点，无配置项（输入变量由调用方在执行前注入）
     */
    @Override
    public FlowNodeMeta nodeMeta() {
        return FlowNodeMeta.of(type(), type(), "入口：注入 question/tableId/history 等输入变量");
    }

    @Override
    public boolean execute(FlowGraph.FlowNode node, FlowContext context) {
        return true;
    }

    @Override
    public String traceSummary(FlowGraph.FlowNode node, FlowContext context, boolean suspended) {
        return "输入变量注入完成";
    }
}
