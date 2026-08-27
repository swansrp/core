package com.bidr.insight.smartquery.dao.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.bidr.insight.smartquery.dao.entity.InsightAgentAsset;
import com.bidr.kernel.mybatis.mapper.MyBaseMapper;
import org.apache.ibatis.annotations.Mapper;

/**
 * Title: InsightAgentAssetDao
 * Description: Agent 语义层资产存储表 Mapper
 *
 * @author Sharp
 * @since 2026/8/18
 */
@Mapper
public interface InsightAgentAssetDao extends BaseMapper<InsightAgentAsset>, MyBaseMapper<InsightAgentAsset> {
}
