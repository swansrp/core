package com.bidr.llm.flow;

import com.bidr.llm.flow.executor.FlowNodeExecutor;
import com.bidr.llm.flow.trace.FlowTrace;
import com.bidr.llm.flow.trace.FlowTraceRecorder;
import com.bidr.llm.sse.FlowSseSender;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Title: FlowEngine
 * Description: DAG 流程执行引擎——加载编排（{@link FlowDefinitionStore} 中的自定义 graph，
 * 缺失/非法/未接入回落 {@link FlowDefinitionProvider} 注册的内置默认链）后从 start 结点沿边执行：
 * <ul>
 *     <li>条件边求值：空=恒真 / {@code var == 'x'} / {@code var != 'x'} / {@code notEmpty(var)}，
 *         多条真边取首条；</li>
 *     <li>流式结点挂起（executor 返回 false）：模型回调线程写完输出变量后
 *         {@link #resume} 续跑，变量池为并发容器保证线程安全；</li>
 *     <li>enabled=false 的结点跳过执行、控制流直通（沿出边继续）；</li>
 *     <li>链路收口经 {@link FlowExecutionListener} 回调（成功 error=null / 失败携带原因），
 *         业务模块在此补写对话等记录；</li>
 *     <li>异常兜底：SSE 链路发 error 事件后关闭连接，非 SSE 链路向上抛出。</li>
 * </ul>
 * 引擎为单例，挂起续跑的全部状态（graph/变量池/SSE 发送器）都在
 * {@link FlowContext} 内，天然支持多请求并发。
 *
 * @author Sharp
 * @since 2026/8/16
 */
@Slf4j
@Service
public class FlowEngine {

    /**
     * 单链路执行步数上限（防 graph 环导致的死循环，校验之外的双保险）
     */
    private static final int MAX_STEPS = 200;

    /**
     * 条件表达式：var == '值' / var != '值'
     */
    private static final Pattern CONDITION_EQ = Pattern.compile("^(\\w+)\\s*(==|!=)\\s*'(.*)'$");

    /**
     * 条件表达式：notEmpty(var)
     */
    private static final Pattern CONDITION_NOT_EMPTY = Pattern.compile("^notEmpty\\((\\w+)\\)$");

    /**
     * 编排持久化（未接入的应用始终用内置默认链）
     */
    private final ObjectProvider<FlowDefinitionStore> storeProvider;

    /**
     * 执行轨迹记录器（flow 调试反馈回路）
     */
    private final FlowTraceRecorder traceRecorder;

    /**
     * 结点类型 → 执行器索引（构造时按 type 建立）
     */
    private final Map<String, FlowNodeExecutor> executors = new LinkedHashMap<>();

    /**
     * 链路收口回调（成功/失败统一走 onFinish）
     */
    private final List<FlowExecutionListener> listeners;

    /**
     * 流程标识 → 注册器索引（封闭集，构造时按 flowKey 建立）
     */
    private final Map<String, FlowDefinitionProvider> providers = new LinkedHashMap<>();

    private final ObjectMapper objectMapper = new ObjectMapper();

    public FlowEngine(ObjectProvider<FlowDefinitionStore> storeProvider,
                      FlowTraceRecorder traceRecorder,
                      List<FlowNodeExecutor> executorList,
                      List<FlowExecutionListener> listenerList,
                      List<FlowDefinitionProvider> providerList) {
        this.storeProvider = storeProvider;
        this.traceRecorder = traceRecorder;
        this.listeners = listenerList == null ? Collections.emptyList() : listenerList;
        for (FlowNodeExecutor executor : executorList) {
            executors.put(executor.type(), executor);
        }
        for (FlowDefinitionProvider provider : providerList) {
            providers.put(provider.flowKey(), provider);
        }
    }

    /**
     * 已注册结点执行器（type 索引序只读视图；管理页聚合结点类型元数据用）
     */
    public Collection<FlowNodeExecutor> registeredExecutors() {
        return Collections.unmodifiableCollection(executors.values());
    }

    /**
     * 执行一条链路：加载编排→开启轨迹→校验→从 start 沿边执行；流式结点挂起后本方法返回，
     * 链路在模型回调线程经 {@link #resume} 继续（沿用同一 traceId 归并轨迹）。
     * 响应结果由 output 结点映射进 ctx
     */
    public void execute(String flowKey, FlowContext context) {
        LoadedFlow loaded = loadFlow(flowKey);
        context.setGraph(loaded.graph);
        context.setTraceId(traceRecorder.startTrace(flowKey, context.getString("question"), loaded.builtin,
                context.getOperator()));
        run(loaded.graph, findStart(loaded.graph), context, false);
    }

    /**
     * 挂起续跑：从已执行完的结点沿出边继续（skipSelf=true 跳过该结点自身执行）；
     * 续跑前先补记挂起结点的模型回答全文
     */
    public void resume(String nodeId, FlowContext context) {
        FlowGraph graph = context.getGraph();
        if (graph == null) {
            log.warn("流程续跑缺少 graph 上下文, nodeId={}", nodeId);
            return;
        }
        FlowGraph.FlowNode node = findNode(graph, nodeId);
        if (node == null) {
            log.warn("流程续跑找不到结点, nodeId={}", nodeId);
            return;
        }
        completeSuspendedTrace(context, node);
        run(graph, node, context, true);
    }

    /**
     * 流式回调失败：由执行器回调线程调用（SSE error 事件由执行器自身发送）——
     * 挂起结点的轨迹补记失败原因后整条轨迹标记 error，并通知收口回调
     */
    public void markTraceError(FlowContext context, String nodeId, String message) {
        String traceId = context.getTraceId();
        if (StringUtils.hasText(traceId)) {
            traceRecorder.appendNodeDetail(traceId, nodeId, "\n\n【流式失败】\n" + message);
            traceRecorder.finishTrace(traceId, "error", message);
        }
        notifyListeners(context, message);
    }

    /**
     * 校验 DAG 结构（保存与加载共用）：结点非空、id 唯一、类型已注册、
     * 恰一个 start、连线引用存在、无环路；不合法抛 IllegalArgumentException
     */
    public void validateGraph(FlowGraph graph) {
        if (graph == null || graph.getNodes().isEmpty()) {
            throw new IllegalArgumentException("流程结点为空");
        }
        Set<String> ids = new HashSet<>();
        int startCount = 0;
        for (FlowGraph.FlowNode node : graph.getNodes()) {
            if (!StringUtils.hasText(node.getId())) {
                throw new IllegalArgumentException("存在缺少 id 的结点");
            }
            if (!ids.add(node.getId())) {
                throw new IllegalArgumentException("结点 id 重复: " + node.getId());
            }
            if ("start".equals(node.getType())) {
                startCount++;
            }
            if (!executors.containsKey(node.getType())) {
                throw new IllegalArgumentException("未注册的结点类型: " + node.getType());
            }
        }
        if (startCount != 1) {
            throw new IllegalArgumentException("流程必须且只能包含一个 start 结点，当前 " + startCount + " 个");
        }
        for (FlowGraph.FlowEdge edge : graph.getEdges()) {
            if (!ids.contains(edge.getSource()) || !ids.contains(edge.getTarget())) {
                throw new IllegalArgumentException(
                        "连线引用了不存在的结点: " + edge.getSource() + " -> " + edge.getTarget());
            }
        }
        checkAcyclic(graph, ids);
    }

    /**
     * 加载编排：持久化记录优先（解析/校验失败或未接入 store 回落内置默认链，执行永不中断）；
     * 同时带回来源标记与名称（自定义保存名 / 注册显示名），供轨迹与管理页复用。
     * 未注册的 flowKey 直接抛错（调用方硬编码 Provider 的常量，传错即编程错误）
     */
    public LoadedFlow loadFlow(String flowKey) {
        FlowDefinitionStore store = storeProvider.getIfAvailable();
        if (store != null) {
            FlowDefinitionRecord record = null;
            try {
                record = store.load(flowKey);
            } catch (Exception e) {
                log.warn("读取流程编排记录失败，回落内置默认链, flowKey={}", flowKey, e);
            }
            if (record != null && StringUtils.hasText(record.getGraph())) {
                try {
                    FlowGraph graph = objectMapper.readValue(record.getGraph(), FlowGraph.class);
                    validateGraph(graph);
                    return new LoadedFlow(graph, false, record.getName());
                } catch (Exception e) {
                    log.warn("流程自定义 graph 非法，回落内置默认链, flowKey={}", flowKey, e);
                }
            }
        }
        FlowDefinitionProvider provider = requireProvider(flowKey);
        FlowGraph defaults = provider.defaultGraph();
        validateGraph(defaults);
        return new LoadedFlow(defaults, true, provider.displayName());
    }

    /**
     * 加载结果：graph + 是否内置默认链 + 名称（管理页与管理服务复用）
     */
    public static final class LoadedFlow {

        public final FlowGraph graph;

        public final boolean builtin;

        public final String name;

        LoadedFlow(FlowGraph graph, boolean builtin, String name) {
            this.graph = graph;
            this.builtin = builtin;
            this.name = name;
        }
    }

    /**
     * 从 from 结点迭代执行；skipSelf=true 时跳过自身（挂起续跑场景）直接走出边
     */
    private void run(FlowGraph graph, FlowGraph.FlowNode from, FlowContext context, boolean skipSelf) {
        try {
            int steps = 0;
            FlowGraph.FlowNode current = from;
            boolean executeSelf = !skipSelf;
            while (current != null) {
                if (++steps > MAX_STEPS) {
                    throw new IllegalStateException("流程执行步数超过 " + MAX_STEPS + "，疑似存在环路");
                }
                if (executeSelf) {
                    if (Boolean.FALSE.equals(current.getEnabled())) {
                        recordNodeEvent(context, current, "skipped", 0, "enabled=false 跳过", null);
                    } else {
                        long nodeStart = System.currentTimeMillis();
                        if (!executorOf(current).execute(current, context)) {
                            // 挂起：流式结点由模型回调线程 resume 续跑
                            recordExecuted(context, current, nodeStart, true);
                            return;
                        }
                        recordExecuted(context, current, nodeStart, false);
                    }
                }
                executeSelf = true;
                current = nextNode(graph, current, context);
            }
            traceRecorder.finishTrace(context.getTraceId(), "success", null);
            notifyListeners(context, null);
        } catch (Exception e) {
            fail(context, e);
        }
    }

    /**
     * 结点执行完成/挂起的轨迹埋点：摘要/全文/调试变量清理内聚在执行器，引擎统一回调
     */
    private void recordExecuted(FlowContext context, FlowGraph.FlowNode node,
                                long startMillis, boolean suspended) {
        FlowNodeExecutor executor = executorOf(node);
        recordNodeEvent(context, node, "ok", System.currentTimeMillis() - startMillis,
                executor.traceSummary(node, context, suspended),
                executor.traceDetail(node, context, suspended));
        executor.clearTraceVars(context);
    }

    private void recordNodeEvent(FlowContext context, FlowGraph.FlowNode node, String status,
                                 long elapsedMs, String summary, String detail) {
        String traceId = context.getTraceId();
        if (!StringUtils.hasText(traceId)) {
            return;
        }
        FlowTrace.NodeEvent event = new FlowTrace.NodeEvent();
        event.setNodeId(node.getId());
        event.setType(node.getType());
        event.setName(node.getName());
        event.setStatus(status);
        event.setElapsedMs(elapsedMs);
        event.setSummary(summary);
        event.setDetail(detail);
        traceRecorder.recordNode(traceId, event);
    }

    /**
     * 流式挂起结点的轨迹收尾：补记执行器给出的补充全文（如模型回答，onComplete 写入调试变量后才会 resume）
     */
    private void completeSuspendedTrace(FlowContext context, FlowGraph.FlowNode node) {
        FlowNodeExecutor executor = executorOf(node);
        String traceId = context.getTraceId();
        if (StringUtils.hasText(traceId)) {
            String supplement = executor.suspendedTraceSupplement(context);
            if (StringUtils.hasText(supplement)) {
                traceRecorder.appendNodeDetail(traceId, node.getId(), "\n\n" + supplement);
            }
        }
        executor.clearTraceVars(context);
    }

    /**
     * 沿出边找下一个结点：条件为真的第一条边；无条件真边即链路结束
     */
    private FlowGraph.FlowNode nextNode(FlowGraph graph, FlowGraph.FlowNode current,
                                        FlowContext context) {
        for (FlowGraph.FlowEdge edge : graph.getEdges()) {
            if (!current.getId().equals(edge.getSource())) {
                continue;
            }
            if (evaluateCondition(edge.getCondition(), context)) {
                return findNode(graph, edge.getTarget());
            }
        }
        return null;
    }

    /**
     * 条件边求值：空=恒真；不认识的表达式视为不成立（log.warn）
     */
    private boolean evaluateCondition(String condition, FlowContext context) {
        if (!StringUtils.hasText(condition)) {
            return true;
        }
        String expr = condition.trim();
        Matcher notEmpty = CONDITION_NOT_EMPTY.matcher(expr);
        if (notEmpty.matches()) {
            return StringUtils.hasText(context.getString(notEmpty.group(1)));
        }
        Matcher equality = CONDITION_EQ.matcher(expr);
        if (equality.matches()) {
            String actual = context.getString(equality.group(1));
            // == 时相等成立，!= 时不等成立
            return "==".equals(equality.group(2)) == actual.equals(equality.group(3));
        }
        log.warn("无法识别的条件表达式，视为不成立: {}", condition);
        return false;
    }

    /**
     * 三色标记 DFS 环检测
     */
    private void checkAcyclic(FlowGraph graph, Set<String> ids) {
        Map<String, List<FlowGraph.FlowEdge>> outEdges = new HashMap<>();
        for (FlowGraph.FlowEdge edge : graph.getEdges()) {
            outEdges.computeIfAbsent(edge.getSource(), key -> new ArrayList<>()).add(edge);
        }
        Map<String, Integer> state = new HashMap<>();
        for (String id : ids) {
            if (state.getOrDefault(id, 0) == 0) {
                dfsAcyclic(id, outEdges, state);
            }
        }
    }

    private void dfsAcyclic(String nodeId, Map<String, List<FlowGraph.FlowEdge>> outEdges,
                            Map<String, Integer> state) {
        state.put(nodeId, 1);
        for (FlowGraph.FlowEdge edge : outEdges.getOrDefault(nodeId, Collections.emptyList())) {
            Integer targetState = state.getOrDefault(edge.getTarget(), 0);
            if (targetState == 1) {
                throw new IllegalArgumentException("流程存在环路: " + edge.getSource() + " -> " + edge.getTarget());
            }
            if (targetState == 0) {
                dfsAcyclic(edge.getTarget(), outEdges, state);
            }
        }
        state.put(nodeId, 2);
    }

    private void fail(FlowContext context, Exception error) {
        log.warn("DAG 流程执行失败", error);
        String message = error.getMessage() == null ? "流程执行失败" : error.getMessage();
        traceRecorder.finishTrace(context.getTraceId(), "error", message);
        notifyListeners(context, message);
        FlowSseSender sender = context.getSseSender();
        if (sender != null) {
            sender.send(FlowSseSender.EVENT_ERROR, message);
            sender.complete();
            return;
        }
        if (error instanceof RuntimeException) {
            throw (RuntimeException) error;
        }
        throw new IllegalStateException(error);
    }

    /**
     * 链路收口回调：单个回调异常只记日志，不影响其余回调与主流程
     */
    private void notifyListeners(FlowContext context, String error) {
        for (FlowExecutionListener listener : listeners) {
            try {
                listener.onFinish(context, error);
            } catch (Exception e) {
                log.warn("链路收口回调失败, listener={}, error={}", listener.getClass().getSimpleName(), e.getMessage());
            }
        }
    }

    private FlowNodeExecutor executorOf(FlowGraph.FlowNode node) {
        FlowNodeExecutor executor = executors.get(node.getType());
        if (executor == null) {
            throw new IllegalArgumentException("未注册的结点类型: " + node.getType());
        }
        return executor;
    }

    private FlowDefinitionProvider requireProvider(String flowKey) {
        FlowDefinitionProvider provider = providers.get(flowKey);
        if (provider == null) {
            throw new IllegalArgumentException("未注册的流程标识: " + flowKey);
        }
        return provider;
    }

    private FlowGraph.FlowNode findStart(FlowGraph graph) {
        for (FlowGraph.FlowNode node : graph.getNodes()) {
            if ("start".equals(node.getType())) {
                return node;
            }
        }
        throw new IllegalArgumentException("流程缺少 start 结点");
    }

    private FlowGraph.FlowNode findNode(FlowGraph graph, String nodeId) {
        for (FlowGraph.FlowNode node : graph.getNodes()) {
            if (nodeId.equals(node.getId())) {
                return node;
            }
        }
        return null;
    }
}
