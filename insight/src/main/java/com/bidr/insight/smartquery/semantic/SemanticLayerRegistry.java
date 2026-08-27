package com.bidr.insight.smartquery.semantic;

import com.bidr.insight.smartquery.service.AgentAssetCacheService;
import com.bidr.kernel.exception.NoticeException;
import com.bidr.kernel.utils.FuncUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Title: SemanticLayerRegistry
 * Description: 多 Agent 语义层注册中心（隔离模型）：default Agent 复用启动期
 * classpath /smartquery/ 单例；其余 Agent 只认 DB 已发布资产（AgentAssetCacheService），
 * 无发布资产直接拒绝（不回落 classpath，classpath 资产仅支撑内置 default Agent），
 * 发布/刷新后经 evictAll 丢弃旧实例重建。当前请求的目标 Agent 以 ThreadLocal 绑定（Controller 入口
 * bind、finally clear），各引擎组件经 current() 取用；agent 标识随 semantic_query.agent
 * 往返（queryContext 不可信，未知 agent 直接拒绝，篡改最多换来拒绝）
 *
 * @author Sharp
 * @since 2026/8/18
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SemanticLayerRegistry {

    public static final String DEFAULT_AGENT = "default";

    private final SemanticLayer defaultLayer;

    private final AgentAssetCacheService agentAssetCacheService;

    private final Map<String, SemanticLayer> layers = new ConcurrentHashMap<>();

    private final ThreadLocal<String> currentAgent = new ThreadLocal<>();

    /** 临时语义层叠加（维护问数链路）：优先级高于常规缓存层，仅本线程可见，finally 必须 clearOverride */
    private final ThreadLocal<SemanticLayer> overrideLayer = new ThreadLocal<>();

    /** 按 agentCode 取语义层：null/空/default → 默认实例；其余只取 DB 发布资产，未发布直接拒绝 */
    public SemanticLayer get(String agentCode) {
        if (isDefault(agentCode)) {
            return defaultLayer;
        }
        return layers.computeIfAbsent(agentCode, this::load);
    }

    /** 只取 DB 已发布资产（管理页发布+刷新后生效）；未发布直接拒绝，不做 classpath 回退 */
    private SemanticLayer load(String code) {
        Map<String, String> assets = agentAssetCacheService.assetsFor(code);
        if (FuncUtil.isEmpty(assets)) {
            throw new NoticeException("Agent '" + code + "' 尚未生成并发布语义资产，请先在 Agent 管理中生成并发布");
        }
        try {
            SemanticLayer layer = SemanticLayer.fromContent(assets);
            log.info("smart-query Agent '{}' 语义层自 DB 发布资产加载完成：entities={}, metrics={}, dimensions={}",
                    code, layer.entities().size(), layer.metricMap().size(), layer.dimensionMap().size());
            return layer;
        } catch (IllegalStateException e) {
            throw new NoticeException("Agent '" + code + "' 语义层资产解析失败: " + e.getMessage());
        }
    }

    /** 发布/改稿+刷新缓存后调用：丢弃已缓存的语义层实例，下次问数按最新资产重建 */
    public void evictAll() {
        layers.clear();
    }

    /** 当前请求绑定的语义层（临时叠加层优先；未绑定 → 默认） */
    public SemanticLayer current() {
        SemanticLayer override = overrideLayer.get();
        if (override != null) {
            return override;
        }
        return get(currentAgent.get());
    }

    /** 叠加临时语义层（维护问数：已发布资产 + LLM 建议项），只影响当前线程 */
    public void bindOverride(SemanticLayer layer) {
        overrideLayer.set(layer);
    }

    public void clearOverride() {
        overrideLayer.remove();
    }

    /** 当前请求的 agentCode（未绑定 → default），执行器据此取同名数据源 */
    public String currentAgentCode() {
        String agent = currentAgent.get();
        return agent == null ? DEFAULT_AGENT : agent;
    }

    /** 绑定当前线程的目标 Agent（Controller 入口调用，finally 必须 clear） */
    public void bind(String agentCode) {
        currentAgent.set(isDefault(agentCode) ? null : agentCode);
    }

    public void clear() {
        currentAgent.remove();
    }

    public static boolean isDefault(String agentCode) {
        return agentCode == null || agentCode.isEmpty() || DEFAULT_AGENT.equals(agentCode);
    }
}
