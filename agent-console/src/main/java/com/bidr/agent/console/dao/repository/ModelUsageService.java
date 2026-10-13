package com.bidr.agent.console.dao.repository;

import com.bidr.agent.console.dao.entity.ModelUsage;
import com.bidr.agent.console.dao.mapper.ModelUsageMapper;
import com.bidr.agent.console.dto.ModelUsageSummary;
import com.bidr.kernel.mybatis.repository.BaseSqlRepo;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

/**
 * Title: ModelUsageService
 * Description: 模型调用用量流水数据仓库（M14 棒5，仅读写方法，DDL 见 Schema）。
 *
 * <p>本表是框架平台表，<b>不参与空间行级隔离</b>（同 {@code AgentManifestService}）：它是复盘用的
 * 用量证据，不是业务资产。core 侧只出<b>用量事实</b>的聚合，不含单价与金额，也不按空间拆账——
 * 那是上层运营政策，且按 key／空间拆账要等模型 key 分家才成立。</p>
 *
 * @author Sharp
 * @since 2026-10-13
 */
@Service
public class ModelUsageService extends BaseSqlRepo<ModelUsageMapper, ModelUsage> {

    /** 单日 24 小时窗口（统计端点的默认时间跨度） */
    private static final long DAY_MS = 24L * 3600L * 1000L;

    /**
     * 组数／流水行的硬上限（一个尺子管两条读腿）：聚合排序是「日新→旧、调用次数大→小」，
     * 超限被截掉的必然是最旧、调用最少的那几组；全量对账不走读腿，直接查表。
     */
    private static final int MAX_ROWS = 500;

    /**
     * 按「日 × 用途档 × agent × 模型」聚合。from/to 为 null 时给默认：
     * to＝现在，from＝现在−1 天（读数窗口短，不默认全表）。
     */
    public List<ModelUsageSummary> summarize(Date from, Date to, String agentKey, String purposeType) {
        Date end = to == null ? new Date() : to;
        Date start = from == null ? new Date(end.getTime() - DAY_MS) : from;
        if (!start.before(end)) {
            return new ArrayList<>();
        }
        return super.getBaseMapper().summarize(start, end, trimToNull(agentKey), trimToNull(purposeType), MAX_ROWS);
    }

    /** 一场会话的用量流水（新→旧；验收口径"一次调用一行"就看这条） */
    public List<ModelUsage> listBySession(String sessionId, int limit) {
        if (sessionId == null || sessionId.trim().isEmpty() || limit <= 0) {
            return new ArrayList<>();
        }
        return lambdaQuery()
                .eq(ModelUsage::getSessionId, sessionId.trim())
                .orderByDesc(ModelUsage::getId)
                .last("limit " + Math.min(limit, MAX_ROWS))
                .list();
    }

    private static String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
