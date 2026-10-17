package com.bidr.insight.smartquery.dao.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.bidr.insight.smartquery.dao.entity.InsightAgentTable;
import com.bidr.kernel.mybatis.mapper.MyBaseMapper;
import org.apache.ibatis.annotations.Mapper;

/**
 * Title: InsightAgentTableDao
 * Description: Agent 选表关联表 Mapper
 *
 * @author Sharp
 * @since 2026/8/18
 */
@Mapper
public interface InsightAgentTableDao extends BaseMapper<InsightAgentTable>, MyBaseMapper<InsightAgentTable> {
}
