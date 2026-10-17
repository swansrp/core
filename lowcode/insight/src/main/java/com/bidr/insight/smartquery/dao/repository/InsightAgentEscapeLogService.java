package com.bidr.insight.smartquery.dao.repository;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.bidr.insight.smartquery.dao.entity.InsightAgentEscapeLog;
import com.bidr.insight.smartquery.dao.mapper.InsightAgentEscapeLogDao;
import com.bidr.kernel.mybatis.repository.BaseSqlRepo;
import com.bidr.kernel.utils.FuncUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Title: InsightAgentEscapeLogService
 * Description: Agent SQL 兜底通道命中台账仓储。record 为结晶信号采集：
 * 兜底成功即记一条，失败吞掉不影响问数主流程（台账是旁路观测，不是关键路径）
 *
 * @author Sharp
 * @since 2026/8/23
 */
@Slf4j
@Service
public class InsightAgentEscapeLogService extends BaseSqlRepo<InsightAgentEscapeLogDao, InsightAgentEscapeLog> {

    /** 兜底命中记录：try-catch 吞异常——台账写失败只记 warn，绝不影响问数应答 */
    public void record(String agentCode, String question, String sqlText, String note) {
        try {
            InsightAgentEscapeLog entry = new InsightAgentEscapeLog();
            entry.setAgentCode(agentCode);
            entry.setQuestion(FuncUtil.isEmpty(question) ? "" : question);
            entry.setSqlText(FuncUtil.isEmpty(sqlText) ? "" : sqlText);
            entry.setNote(note);
            super.insert(entry);
        } catch (Exception e) {
            log.warn("Agent '{}' 兜底台账写入失败（不影响问数结果）: {}", agentCode, e.getMessage());
        }
    }

    /** 台账浏览：按 Agent 过滤（可空=全量），新命中在前，最多 limit 条 */
    public List<InsightAgentEscapeLog> recent(String agentCode, int limit) {
        QueryWrapper<InsightAgentEscapeLog> wrapper = new QueryWrapper<InsightAgentEscapeLog>()
                .orderByDesc("create_at").orderByDesc("id")
                .last("LIMIT " + Math.max(1, Math.min(limit, 500)));
        if (FuncUtil.isNotEmpty(agentCode)) {
            wrapper.eq("agent_code", agentCode);
        }
        return super.select(wrapper);
    }
}
