package com.bidr.insight.smartquery.dao.repository;

import com.bidr.insight.smartquery.dao.entity.InsightAgent;
import com.bidr.insight.smartquery.dao.mapper.InsightAgentDao;
import com.bidr.kernel.mybatis.repository.BaseSqlRepo;
import org.springframework.stereotype.Service;

/**
 * Title: InsightAgentService
 * Description: 智能问数 Agent 配置表仓储（bean 名须与实体名对应：
 * BaseAdminController 按 decapitalize(entitySimpleName) + "Service" 查找）
 *
 * @author Sharp
 * @since 2026/8/18
 */
@Service
public class InsightAgentService extends BaseSqlRepo<InsightAgentDao, InsightAgent> {
}
