package com.bidr.insight.smartquery.dao.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.bidr.insight.smartquery.dao.entity.InsightTableTemplate;
import com.bidr.kernel.mybatis.mapper.MyBaseMapper;
import org.apache.ibatis.annotations.Mapper;

/**
 * Title: InsightTableTemplateDao
 * Description: 表级资产模板 Mapper
 *
 * @author Sharp
 * @since 2026/8/24
 */
@Mapper
public interface InsightTableTemplateDao extends BaseMapper<InsightTableTemplate>, MyBaseMapper<InsightTableTemplate> {
}
