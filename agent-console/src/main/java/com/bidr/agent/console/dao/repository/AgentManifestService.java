package com.bidr.agent.console.dao.repository;

import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.bidr.agent.console.dao.entity.AgentManifest;
import com.bidr.agent.console.dao.mapper.AgentManifestMapper;
import com.bidr.kernel.mybatis.repository.BaseSqlRepo;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * Title: AgentManifestService
 * Description: agent 会话装配单数据仓库（仅读写方法，DDL 见 Schema）。
 *
 * <p>本表是框架平台表，<b>不参与空间行级隔离</b>：装配单是跨空间的诊断证据（与
 * {@code kb_agent_run} 台账同理），隔离器会把别的空间的行筛掉，读腿就查不出"那场会话手里有什么"。</p>
 *
 * @author Sharp
 * @since 2026-10-12
 */
@Service
public class AgentManifestService extends BaseSqlRepo<AgentManifestMapper, AgentManifest> {

    /** 按会话取装配单（无则 null） */
    public AgentManifest findBySession(String sessionId) {
        return lambdaQuery().eq(AgentManifest::getSessionId, sessionId).one();
    }

    /** 某 agent 最近的装配单（新→旧；agentKey 空＝不限 agent，limit 由调用方夹过） */
    public List<AgentManifest> listRecent(String agentKey, int limit) {
        if (limit <= 0) {
            return new ArrayList<>();
        }
        return lambdaQuery()
                .eq(agentKey != null && !agentKey.trim().isEmpty(), AgentManifest::getAgentKey,
                        agentKey == null ? null : agentKey.trim())
                .orderByDesc(AgentManifest::getId)
                .last("limit " + limit)
                .list();
    }

    /**
     * 同会话重定形时按 sessionId 定位整行覆盖（不整行覆盖身份列以外的空值：
     * 贡献者这一栏没交就保留上一版的值，避免"重跑一次反而把技能清单擦干净"）。
     */
    public void overwriteBySession(AgentManifest row) {
        LambdaUpdateWrapper<AgentManifest> where = new LambdaUpdateWrapper<>();
        where.eq(AgentManifest::getSessionId, row.getSessionId());
        update(row, where);
    }

    /**
     * 补写对话定位列（M14 棒2 增量）：装配单在工具面定形时落行，对话 id 要到收口才产生，
     * 故这是一条<b>定向 UPDATE 单列</b>、不整行覆盖——把技能清单/段清单那些已定的栏原地保住。
     * 无该行（装配单关闭、或会话早于棒 2）时 0 行受影响，静默即可：定位不是会话的必需品。
     */
    public void linkConversation(String sessionId, String conversationId) {
        LambdaUpdateWrapper<AgentManifest> where = new LambdaUpdateWrapper<>();
        where.eq(AgentManifest::getSessionId, sessionId)
                .set(AgentManifest::getConversationId, conversationId);
        update(where);
    }
}
