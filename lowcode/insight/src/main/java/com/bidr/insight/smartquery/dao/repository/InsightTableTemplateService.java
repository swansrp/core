package com.bidr.insight.smartquery.dao.repository;

import com.bidr.insight.smartquery.dao.entity.InsightTableTemplate;
import com.bidr.insight.smartquery.dao.mapper.InsightTableTemplateDao;
import com.bidr.kernel.mybatis.repository.BaseSqlRepo;
import org.springframework.stereotype.Service;

/**
 * Title: InsightTableTemplateService
 * Description: 表级资产模板仓储（跨 Agent 复用）
 *
 * @author Sharp
 * @since 2026/8/24
 */
@Service
public class InsightTableTemplateService extends BaseSqlRepo<InsightTableTemplateDao, InsightTableTemplate> {
}
