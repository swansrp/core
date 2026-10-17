package com.bidr.insight.smartquery.dao.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.bidr.insight.smartquery.dao.entity.InsightAgentProposal;
import com.bidr.kernel.mybatis.mapper.MyBaseMapper;
import org.apache.ibatis.annotations.Mapper;

/**
 * Title: InsightAgentProposalDao
 * Description: Agent 问数资产变更建议表 Mapper
 *
 * @author Sharp
 * @since 2026/8/19
 */
@Mapper
public interface InsightAgentProposalDao extends BaseMapper<InsightAgentProposal>, MyBaseMapper<InsightAgentProposal> {
}
