package com.bidr.llm.flow;

import com.bidr.kernel.exception.ServiceException;
import com.bidr.llm.flow.executor.FlowNodeExecutor;
import com.bidr.llm.flow.trace.FlowTraceRecorder;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.*;

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
     * 流程版本化存储（可选 SPI：无实现的应用版本化能力整体关闭，管理方法明确报"未启用"）
     */
    private final ObjectProvider<FlowDefinitionVersionStore> versionStoreProvider;

    /**
     * 执行轨迹记录器（版本化运行也开轨迹，与普通执行同一条反馈回路；旧构造路径为 null）
     */
    private final FlowTraceRecorder traceRecorder;

    /**
     * 流程标识 → 注册器索引（封闭集来源）
     */
    private final Map<String, FlowDefinitionProvider> providers = new LinkedHashMap<>();

    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * 兼容旧构造（版本化与轨迹不启用）；Spring 装配走 {@link #FlowManagerService(ObjectProvider, FlowEngine,
     * List, ObjectProvider, FlowTraceRecorder)}（多构造器时以 @Autowired 标注为准）
     */
    public FlowManagerService(ObjectProvider<FlowDefinitionStore> storeProvider,
                              FlowEngine flowEngine,
                              List<FlowDefinitionProvider> providerList) {
        this(storeProvider, flowEngine, providerList, null, null);
    }

    @Autowired
    public FlowManagerService(ObjectProvider<FlowDefinitionStore> storeProvider,
                              FlowEngine flowEngine,
                              List<FlowDefinitionProvider> providerList,
                              ObjectProvider<FlowDefinitionVersionStore> versionStoreProvider,
                              FlowTraceRecorder traceRecorder) {
        this.storeProvider = storeProvider;
        this.flowEngine = flowEngine;
        this.versionStoreProvider = versionStoreProvider;
        this.traceRecorder = traceRecorder;
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

    // ==================== 流程版本化（T4.1 门面增量；无 FlowDefinitionVersionStore 实现时整体关闭）====================

    /**
     * 某流程的全部版本号（升序）；未启用版本化或尚无版本返回空清单
     */
    public List<Integer> flowVersions(String flowKey) {
        requireProvider(flowKey);
        FlowDefinitionVersionStore store = versionStore();
        if (store == null) {
            return Collections.emptyList();
        }
        List<Integer> versions = store.versions(flowKey);
        return versions == null ? Collections.emptyList() : new ArrayList<>(versions);
    }

    /**
     * 读取指定版本编排（解析校验后返回，画布/审计可用；builtin 恒 false——版本记录必然是保存的自定义编排）。
     * 未启用版本化或版本不存在报 ServiceException
     */
    public FlowDetailRes getFlowVersion(String flowKey, int version) {
        requireProvider(flowKey);
        FlowDefinitionRecord record = requireVersionRecord(flowKey, version);
        FlowGraph graph = parseVersionGraph(record);
        FlowDetailRes res = new FlowDetailRes();
        res.setFlowKey(flowKey);
        res.setName(record.getName());
        res.setGraph(graph);
        res.setBuiltin(Boolean.FALSE);
        return res;
    }

    /**
     * 保存流程版本：graphJson 结构校验通过后原样落库（保留提交者排版），提示词模板随 graph 一并存档；
     * version 缺省 = 最新版本 + 1，传已有版本号 = 覆盖该版本。返回落库的版本号
     */
    public int saveFlowVersion(FlowVersionSaveReq req) {
        if (req == null || !StringUtils.hasText(req.getFlowKey())) {
            throw new IllegalArgumentException("流程标识不能为空");
        }
        String flowKey = req.getFlowKey().trim();
        requireProvider(flowKey);
        FlowDefinitionVersionStore store = requireVersionStore();
        if (!StringUtils.hasText(req.getGraphJson())) {
            throw new IllegalArgumentException("流程 graph JSON 不能为空");
        }
        FlowGraph graph = parseVersionGraph(req.getFlowKey().trim(), req.getGraphJson());
        flowEngine.validateGraph(graph);
        String safeName = StringUtils.hasText(req.getName())
                ? req.getName().trim() : requireProvider(flowKey).displayName();
        int version = req.getVersion() != null ? req.getVersion() : store.latestVersion(flowKey) + 1;
        if (version <= 0) {
            throw new IllegalArgumentException("版本号必须是正整数: " + version);
        }
        store.save(flowKey, safeName, req.getGraphJson(), version);
        return version;
    }

    /**
     * 按指定版本执行：加载<b>该版本</b>的 graph（与当前生效编排无关）注入上下文后，
     * 从 start 结点驱动引擎执行（start 结点本身是空锚点，经 resume 通道跳过自身执行等价于从 start 起跑，
     * 与 {@link FlowEngine#execute} 的差异仅是 graph 来源钉死在版本记录）；
     * 返回轨迹标识（轨迹未装配时为 null，执行不受影响）。未启用版本化或版本不存在报 ServiceException
     */
    public String runFlowVersion(String flowKey, int version, FlowContext context) {
        requireProvider(flowKey);
        FlowDefinitionRecord record = requireVersionRecord(flowKey, version);
        if (context == null) {
            throw new IllegalArgumentException("执行上下文不能为空");
        }
        FlowGraph graph = parseVersionGraph(record);
        context.setGraph(graph);
        if (traceRecorder != null) {
            context.setTraceId(traceRecorder.startTrace(flowKey, context.getString("question"), false,
                    context.getOperator()));
        }
        flowEngine.resume(requireStartNodeId(graph), context);
        return context.getTraceId();
    }

    /**
     * 版本存储（未装配返回 null——版本化能力整体关闭）
     */
    private FlowDefinitionVersionStore versionStore() {
        return versionStoreProvider == null ? null : versionStoreProvider.getIfAvailable();
    }

    private FlowDefinitionVersionStore requireVersionStore() {
        FlowDefinitionVersionStore store = versionStore();
        if (store == null) {
            throw new ServiceException("应用未启用流程版本化（FlowDefinitionVersionStore），无法使用版本管理");
        }
        return store;
    }

    /**
     * 读版本记录：未启用版本化 / 版本不存在 / 记录缺 graph 统一报 ServiceException
     */
    private FlowDefinitionRecord requireVersionRecord(String flowKey, int version) {
        FlowDefinitionVersionStore store = requireVersionStore();
        FlowDefinitionRecord record = store.load(flowKey, version);
        if (record == null || !StringUtils.hasText(record.getGraph())) {
            throw new ServiceException("流程版本不存在: " + flowKey + " v" + version);
        }
        return record;
    }

    /**
     * 版本 graphJson → FlowGraph（坏记录直接报错，版本读取不做静默回落——
     * 历史版本是不可变存档，坏即坏，明确暴露而不是悄悄换成默认链）
     */
    private FlowGraph parseVersionGraph(FlowDefinitionRecord record) {
        return parseVersionGraph(record.getFlowKey(), record.getGraph());
    }

    private FlowGraph parseVersionGraph(String flowKey, String graphJson) {
        try {
            return objectMapper.readValue(graphJson, FlowGraph.class);
        } catch (Exception e) {
            throw new ServiceException("流程版本 graph 解析失败: " + flowKey + " - " + e.getMessage(), e);
        }
    }

    private String requireStartNodeId(FlowGraph graph) {
        for (FlowGraph.FlowNode node : graph.getNodes()) {
            if ("start".equals(node.getType())) {
                return node.getId();
            }
        }
        throw new IllegalArgumentException("流程缺少 start 结点");
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
