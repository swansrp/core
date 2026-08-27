package com.bidr.insight.smartquery.dao.repository;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.bidr.insight.smartquery.dao.entity.InsightAgentProposal;
import com.bidr.insight.smartquery.dao.mapper.InsightAgentProposalDao;
import com.bidr.kernel.mybatis.repository.BaseSqlRepo;
import com.bidr.kernel.utils.FuncUtil;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Title: InsightAgentProposalService
 * Description: Agent 问数资产变更建议表仓储
 *
 * @author Sharp
 * @since 2026/8/19
 */
@Service
public class InsightAgentProposalService extends BaseSqlRepo<InsightAgentProposalDao, InsightAgentProposal> {

    /** 审批列表：按 Agent + 状态过滤，新提案在前 */
    public List<InsightAgentProposal> listByAgent(String agentCode, String status) {
        QueryWrapper<InsightAgentProposal> wrapper = new QueryWrapper<InsightAgentProposal>()
                .eq("agent_code", agentCode)
                .orderByDesc("create_at").orderByDesc("id");
        if (FuncUtil.isNotEmpty(status)) {
            wrapper.eq("status", status);
        }
        return super.select(wrapper);
    }

    /** 各 Agent 待审提案数（管理页行内徽标 + 未处理提示，一次聚合查全量） */
    public Map<String, Long> pendingCounts() {
        QueryWrapper<InsightAgentProposal> wrapper = new QueryWrapper<InsightAgentProposal>()
                .select("agent_code", "count(*) AS cnt")
                .eq("status", "0")
                .groupBy("agent_code");
        Map<String, Long> result = new LinkedHashMap<>();
        for (Map<String, Object> row : super.getBaseMapper().selectMaps(wrapper)) {
            result.put(String.valueOf(row.get("agent_code")), ((Number) row.get("cnt")).longValue());
        }
        return result;
    }
}
