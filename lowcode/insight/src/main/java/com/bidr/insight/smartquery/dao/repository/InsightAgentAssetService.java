package com.bidr.insight.smartquery.dao.repository;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.bidr.insight.smartquery.dao.entity.InsightAgentAsset;
import com.bidr.insight.smartquery.dao.mapper.InsightAgentAssetDao;
import com.bidr.kernel.mybatis.repository.BaseSqlRepo;
import com.bidr.kernel.utils.FuncUtil;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Title: InsightAgentAssetService
 * Description: Agent 语义层资产存储表仓储
 *
 * @author Sharp
 * @since 2026/8/18
 */
@Service
public class InsightAgentAssetService extends BaseSqlRepo<InsightAgentAssetDao, InsightAgentAsset> {

    /** 缓存加载：只取已发布（status=1）资产，草稿不进运行期；
     *  运行期读发布快照列（存量数据快照为空时回落草稿列兼容） */
    public List<InsightAgentAsset> getPublishedAssets() {
        List<InsightAgentAsset> list = super.select(new QueryWrapper<InsightAgentAsset>().eq("status", "1"));
        for (InsightAgentAsset asset : list) {
            if (FuncUtil.isNotEmpty(asset.getPublishedContent())) {
                asset.setContent(asset.getPublishedContent());
            }
        }
        return list;
    }

    /** 各 Agent 未发布草稿资产数（管理页行内徽标 + 发布提醒，一次聚合查全量）；
     *  只统计传入的标准资产类型：review-report/llm-prompts 等非标准行无发布语义，
     *  计入则发布后徽标永不清零 */
    public Map<String, Long> draftCounts(List<String> assetTypes) {
        QueryWrapper<InsightAgentAsset> wrapper = new QueryWrapper<InsightAgentAsset>()
                .select("agent_code", "count(*) AS cnt")
                .eq("status", "0")
                .in("asset_type", assetTypes)
                .groupBy("agent_code");
        Map<String, Long> result = new LinkedHashMap<>();
        for (Map<String, Object> row : super.getBaseMapper().selectMaps(wrapper)) {
            result.put(String.valueOf(row.get("agent_code")), ((Number) row.get("cnt")).longValue());
        }
        return result;
    }
}
