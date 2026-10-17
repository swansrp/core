package com.bidr.insight.smartquery.service;

import com.bidr.insight.smartquery.dao.entity.InsightAgentAsset;
import com.bidr.insight.smartquery.dao.repository.InsightAgentAssetService;
import com.bidr.kernel.cache.DynamicMemoryCache;
import com.bidr.kernel.utils.FuncUtil;
import com.bidr.platform.config.aop.RedisPublish;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Title: AgentAssetCacheService
 * Description: Agent 语义层资产运行期缓存（同 SysConfigCacheService 的配置流程）：
 * 只加载已发布（status=1）资产进内存，key=agentCode#assetType；发布/改稿后
 * 由管理页调用 /refresh 触发重载（多实例经 Redis 广播同步）。草稿资产不进缓存，
 * 未发布 Agent 问数时回落 classpath 或报未配置
 *
 * @author Sharp
 * @since 2026/8/18
 */
@Slf4j
@Service
public class AgentAssetCacheService extends DynamicMemoryCache<InsightAgentAsset> {

    private static final String KEY_SEP = "#";

    @Resource
    private InsightAgentAssetService insightAgentAssetService;
    @Lazy
    @Resource
    private AgentAssetCacheService self;

    @Override
    protected Map<String, InsightAgentAsset> getCacheData() {
        List<InsightAgentAsset> list = insightAgentAssetService.getPublishedAssets();
        Map<String, InsightAgentAsset> map = new HashMap<>();
        if (FuncUtil.isNotEmpty(list)) {
            for (InsightAgentAsset asset : list) {
                map.put(asset.getAgentCode() + KEY_SEP + asset.getAssetType(), asset);
            }
        }
        return map;
    }

    /** 发布/改稿后触发：重载内存缓存（多实例经 Redis 广播同步） */
    @RedisPublish
    @Override
    public void refresh() {
        super.refresh();
    }

    /**
     * 取指定 Agent 的已发布资产：资产文件名（entities.json 等六类）→ JSON 全文。
     * 无已发布资产返回空 Map（调用方据此回落 classpath）
     */
    public Map<String, String> assetsFor(String agentCode) {
        if (FuncUtil.isEmpty(agentCode)) {
            return Collections.emptyMap();
        }
        Map<String, InsightAgentAsset> all = self.getAllCache();
        if (FuncUtil.isEmpty(all)) {
            return Collections.emptyMap();
        }
        String prefix = agentCode + KEY_SEP;
        Map<String, String> assets = new HashMap<>();
        for (Map.Entry<String, InsightAgentAsset> entry : all.entrySet()) {
            if (entry.getKey().startsWith(prefix) && FuncUtil.isNotEmpty(entry.getValue().getContent())) {
                assets.put(entry.getValue().getAssetType() + ".json", entry.getValue().getContent());
            }
        }
        return assets;
    }
}
