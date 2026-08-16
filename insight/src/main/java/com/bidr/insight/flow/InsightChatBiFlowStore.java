package com.bidr.insight.flow;

import com.bidr.insight.dao.entity.ChatBiFlow;
import com.bidr.insight.dao.repository.ChatBiFlowService;
import com.bidr.llm.flow.FlowDefinitionRecord;
import com.bidr.llm.flow.FlowDefinitionStore;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Title: InsightChatBiFlowStore
 * Description: 编排持久化接入——llm flow 引擎的 {@link FlowDefinitionStore} 落库实现，
 * 包装 insight_chatbi_flow 表的 Repository Service（表结构与存量数据不迁移，自定义编排续用原存储）。
 *
 * @author Sharp
 * @since 2026/8/16
 */
@Component
@RequiredArgsConstructor
public class InsightChatBiFlowStore implements FlowDefinitionStore {

    private final ChatBiFlowService chatBiFlowService;

    @Override
    public FlowDefinitionRecord load(String flowKey) {
        ChatBiFlow record = chatBiFlowService.getByFlowKey(flowKey);
        if (record == null) {
            return null;
        }
        FlowDefinitionRecord result = new FlowDefinitionRecord();
        result.setFlowKey(record.getFlowKey());
        result.setName(record.getName());
        result.setGraph(record.getGraph());
        return result;
    }

    @Override
    public void save(String flowKey, String name, String graphJson) {
        ChatBiFlow flow = new ChatBiFlow();
        flow.setFlowKey(flowKey);
        flow.setName(name);
        flow.setGraph(graphJson);
        chatBiFlowService.saveFlow(flow);
    }

    @Override
    public void delete(String flowKey) {
        chatBiFlowService.deleteById(flowKey);
    }
}
