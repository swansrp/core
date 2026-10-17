package com.bidr.llm.flow;

import com.bidr.llm.flow.executor.FlowNodeExecutor;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Title: FlowManagerService
 * Description: 流程编排管理——管理页画布的存取入口：
 * <ul>
 *     <li>getFlow：库中无记录/记录非法时返回内置默认链并标记 builtin=true
 *         （与引擎执行回落同一口径 {@link FlowEngine#loadFlow}，画布数据源永远合法，保存即修复坏记录）；</li>
 *     <li>saveFlow：引擎结构校验（含 start、无环、类型合法）后经 {@link FlowDefinitionStore} 落库，
 *         前端预校验之外的第二道防线；未接入 store 的应用报错提示；</li>
 *     <li>resetFlow：删除库中自定义记录，执行回落内置默认链（单一真源，幂等；未接入 store 视同已重置）。</li>
 * </ul>
 * 读写只面向已注册的 flowKey（封闭集：{@link FlowDefinitionProvider} 注册的成员），
 * 防止任意 key 写库。
 *
 * @author Sharp
 * @since 2026/8/16
 */
@Slf4j
@Service
public class FlowManagerService {

    private final ObjectProvider<FlowDefinitionStore> storeProvider;

    private final FlowEngine flowEngine;

    /**
     * 流程标识 → 注册器索引（封闭集来源）
     */
    private final Map<String, FlowDefinitionProvider> providers = new LinkedHashMap<>();

    private final ObjectMapper objectMapper = new ObjectMapper();

    public FlowManagerService(ObjectProvider<FlowDefinitionStore> storeProvider,
                              FlowEngine flowEngine,
                              List<FlowDefinitionProvider> providerList) {
        this.storeProvider = storeProvider;
        this.flowEngine = flowEngine;
        for (FlowDefinitionProvider provider : providerList) {
            providers.put(provider.flowKey(), provider);
        }
    }

    /**
     * skill 注册表：skill 下的链清单 + 画布可用结点类型元数据（工作台启动数据源）。
     * 类型集 = 该 skill 各链当前生效 graph（内置默认或库中自定义，与执行回落同口径）
     * 出现过的类型并集——业务方注册的私有结点类型不会泄漏到其他 skill 的画布
     */
    public FlowRegistryRes registry(String skillCode) {
        if (!StringUtils.hasText(skillCode)) {
            throw new IllegalArgumentException("skill 标识不能为空");
        }
        String skill = skillCode.trim();
        List<FlowDefinitionProvider> skillProviders = new ArrayList<>();
        for (FlowDefinitionProvider provider : providers.values()) {
            if (skill.equals(provider.skillCode())) {
                skillProviders.add(provider);
            }
        }
        if (skillProviders.isEmpty()) {
            throw new IllegalArgumentException("未注册的 skill 标识: " + skill);
        }
        FlowRegistryRes res = new FlowRegistryRes();
        res.setSkillCode(skill);
        Set<String> usedTypes = new LinkedHashSet<>();
        for (FlowDefinitionProvider provider : skillProviders) {
            FlowRegistryRes.FlowSummary summary = new FlowRegistryRes.FlowSummary();
            summary.setFlowKey(provider.flowKey());
            summary.setDisplayName(provider.displayName());
            res.getFlows().add(summary);
            for (FlowGraph.FlowNode node : flowEngine.loadFlow(provider.flowKey()).graph.getNodes()) {
                usedTypes.add(node.getType());
            }
        }
        for (FlowNodeExecutor executor : flowEngine.registeredExecutors()) {
            if (usedTypes.contains(executor.type())) {
                res.getNodeTypes().add(executor.nodeMeta());
            }
        }
        return res;
    }

    /**
     * 查询编排详情：库记录合法→返回自定义 graph（builtin=false）；
     * 无记录或解析/校验失败→内置默认链（builtin=true），保证画布数据源永远合法
     */
    public FlowDetailRes getFlow(String flowKey) {
        FlowEngine.LoadedFlow loaded = flowEngine.loadFlow(flowKey);
        FlowDetailRes res = new FlowDetailRes();
        res.setFlowKey(flowKey);
        res.setName(loaded.name);
        res.setGraph(loaded.graph);
        res.setBuiltin(loaded.builtin);
        return res;
    }

    /**
     * 保存编排：结构校验通过后覆盖落库（无则插入，有则更新，由 store 实现决定），提示词模板随 graph 一并存库即改即生效
     */
    public void saveFlow(FlowSaveReq req) {
        if (req == null || !StringUtils.hasText(req.getFlowKey())) {
            throw new IllegalArgumentException("流程标识不能为空");
        }
        String flowKey = req.getFlowKey().trim();
        requireKnownFlowKey(flowKey);
        flowEngine.validateGraph(req.getGraph());
        String safeName = StringUtils.hasText(req.getName())
                ? req.getName().trim() : requireProvider(flowKey).displayName();
        String graphJson;
        try {
            graphJson = objectMapper.writeValueAsString(req.getGraph());
        } catch (Exception e) {
            throw new IllegalStateException("流程 graph 序列化失败", e);
        }
        requireStore().save(flowKey, safeName, graphJson);
    }

    /**
     * 重置为内置默认链：删除自定义记录（幂等，无记录同样成功）
     */
    public void resetFlow(String flowKey) {
        requireKnownFlowKey(flowKey);
        FlowDefinitionStore store = storeProvider.getIfAvailable();
        if (store != null) {
            store.delete(flowKey);
        }
    }

    /**
     * 流程标识封闭集校验（已注册的 Provider 成员），防止任意 key 写库
     */
    private void requireKnownFlowKey(String flowKey) {
        requireProvider(flowKey);
    }

    private FlowDefinitionProvider requireProvider(String flowKey) {
        FlowDefinitionProvider provider = providers.get(flowKey);
        if (provider == null) {
            throw new IllegalArgumentException("未注册的流程标识: " + flowKey);
        }
        return provider;
    }

    /**
     * 未接入编排持久化的应用不可保存（重置幂等无需报错）
     */
    private FlowDefinitionStore requireStore() {
        FlowDefinitionStore store = storeProvider.getIfAvailable();
        if (store == null) {
            throw new IllegalStateException("应用未接入编排持久化（FlowDefinitionStore），无法保存自定义编排");
        }
        return store;
    }
}
