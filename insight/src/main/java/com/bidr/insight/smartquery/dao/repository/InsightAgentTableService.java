package com.bidr.insight.smartquery.dao.repository;

import com.bidr.insight.smartquery.dao.entity.InsightAgentTable;
import com.bidr.insight.smartquery.dao.mapper.InsightAgentTableDao;
import com.bidr.kernel.mybatis.repository.BaseSqlRepo;
import org.springframework.stereotype.Service;

/**
 * Title: InsightAgentTableService
 * Description: Agent 选表关联表仓储
 *
 * @author Sharp
 * @since 2026/8/18
 */
@Service
public class InsightAgentTableService extends BaseSqlRepo<InsightAgentTableDao, InsightAgentTable> {
}
