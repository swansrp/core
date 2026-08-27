package com.bidr.insight.smartquery.dao.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.bidr.insight.smartquery.dao.entity.InsightAgentEscapeLog;
import com.bidr.kernel.mybatis.mapper.MyBaseMapper;
import org.apache.ibatis.annotations.Mapper;

/**
 * Title: InsightAgentEscapeLogDao
 * Description: Agent SQL 兜底通道命中台账 Mapper
 *
 * @author Sharp
 * @since 2026/8/23
 */
@Mapper
public interface InsightAgentEscapeLogDao extends BaseMapper<InsightAgentEscapeLog>, MyBaseMapper<InsightAgentEscapeLog> {
}
