package com.bidr.insight.chatbi.dao.repository;

import com.bidr.insight.chatbi.dao.entity.ChatBiFlow;
import com.bidr.insight.chatbi.dao.mapper.ChatBiFlowMapper;
import com.bidr.kernel.mybatis.repository.BaseSqlRepo;
import org.springframework.stereotype.Service;

/**
 * Title: ChatBiFlowService
 * Description: 智能问数流程编排 Repository Service——按 flowKey 存取 DAG 定义
 *
 * @author Sharp
 * @since 2026/8/15
 */
@Service
public class ChatBiFlowService extends BaseSqlRepo<ChatBiFlowMapper, ChatBiFlow> {

    /**
     * 按流程标识取编排记录（库中无自定义时为 null，由引擎回落内置默认链）
     */
    public ChatBiFlow getByFlowKey(String flowKey) {
        return super.selectOne(getQueryWrapper().eq(ChatBiFlow::getFlowKey, flowKey));
    }

    /**
     * 覆盖保存（无则插入，有则更新 graph/name）
     */
    public void saveFlow(ChatBiFlow flow) {
        ChatBiFlow existing = getByFlowKey(flow.getFlowKey());
        if (existing == null) {
            super.insert(flow);
        } else {
            existing.setName(flow.getName());
            existing.setGraph(flow.getGraph());
            super.updateById(existing);
        }
    }
}
