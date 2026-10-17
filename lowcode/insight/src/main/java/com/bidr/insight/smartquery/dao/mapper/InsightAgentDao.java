package com.bidr.insight.smartquery.dao.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.bidr.insight.smartquery.dao.entity.InsightAgent;
import com.bidr.kernel.mybatis.mapper.MyBaseMapper;
import org.apache.ibatis.annotations.Mapper;

/**
 * Title: InsightAgentDao
 * Description: 智能问数 Agent 配置表 Mapper
 *
 * @author Sharp
 * @since 2026/8/18
 */
@Mapper
public interface InsightAgentDao extends BaseMapper<InsightAgent>, MyBaseMapper<InsightAgent> {
}
