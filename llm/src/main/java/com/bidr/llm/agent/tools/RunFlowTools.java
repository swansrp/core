package com.bidr.llm.agent.tools;

import com.bidr.llm.flow.FlowContext;
import com.bidr.llm.flow.FlowEngine;
import com.bidr.llm.flow.FlowGraph;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import lombok.extern.slf4j.Slf4j;

import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.BooleanSupplier;

/**
 * Title: RunFlowTools
 * Description: 自主 agent 的流程调用工具（langchain4j function calling，llm 基础框架通用）：
 * run_flow 同步执行一条已编排链路并取 output 结点产物。三重守卫：
 * ① 仅非流式链（graph 含 llm 结点且 config.stream 开启即拒绝，流式链依赖 SSE 挂起续跑，同步调用无法收口）；
 * ② 调用深度 1（ThreadLocal 计数，flow 节点内再嵌 run_flow 直接拒绝，防递归爆栈）；
 * ③ 30s 超时保护（超时 interrupt 引擎线程走停止收口，防长链路霸占 agent 轮次）。
 * 出参紧凑 JSON，值超长截断控制上下文
 *
 * @author Sharp
 * @since 2026/8/20
 */
@Slf4j
public class RunFlowTools {

    private static final ObjectMapper OM = new ObjectMapper();

    /** 同步执行超时（秒）：长链路应拆节点或改自主编排，不让 run_flow 无限等待 */
    private static final int TIMEOUT_SECONDS = 30;
    /** 单元格值最大长度（超长截断，控制上下文） */
    private static final int CELL_MAX_LEN = 200;

    /** 调用深度（防递归：flow 节点执行线程内再调 run_flow 时 >0） */
    private static final ThreadLocal<Integer> DEPTH = ThreadLocal.withInitial(() -> 0);

    /** 同步执行线程池（守护线程，超时靠 interrupt 收口） */
    private static final ExecutorService EXECUTOR = Executors.newCachedThreadPool(r -> {
        Thread t = new Thread(r, "agent-run-flow");
        t.setDaemon(true);
        return t;
    });

    private final FlowEngine flowEngine;
    /** 停止检查（可为 null） */
    private final BooleanSupplier stopChecker;

    public RunFlowTools(FlowEngine flowEngine, BooleanSupplier stopChecker) {
        this.flowEngine = flowEngine;
        this.stopChecker = stopChecker;
    }

    @Tool("调用一条已编排流程并返回其输出（仅支持同步链路）：flowKey 为流程标识，"
            + "inputJson 为流程入参变量 JSON 对象（可空）；30 秒超时，不支持嵌套调用")
    public String runFlow(@P("流程标识 flowKey，如 asset-gen") String flowKey,
                          @P("流程入参变量 JSON 对象（顶层键值对注入流程变量池，可空）") String inputJson) {
        if (stopChecker != null && stopChecker.getAsBoolean()) {
            return "{\"error\":\"任务已被用户停止，禁止再调用任何工具\"}";
        }
        String key = flowKey == null ? "" : flowKey.trim();
        if (key.isEmpty()) {
            return "{\"error\":\"flowKey 不能为空\"}";
        }
        if (DEPTH.get() > 0) {
            return "{\"error\":\"run_flow 不允许嵌套调用（当前已在 flow 执行中），请直接输出结论\"}";
        }
        // 入参变量解析（可空）
        ObjectNode input = OM.createObjectNode();
        if (inputJson != null && !inputJson.trim().isEmpty()) {
            try {
                JsonNode node = OM.readTree(inputJson);
                if (node.isObject()) {
                    input = (ObjectNode) node;
                } else {
                    return "{\"error\":\"inputJson 必须是 JSON 对象\"}";
                }
            } catch (Exception e) {
                return "{\"error\":\"inputJson 不是合法 JSON: " + e.getMessage() + "\"}";
            }
        }
        // 守卫①：仅非流式链（llm 结点 stream 开启即拒绝）
        FlowEngine.LoadedFlow loaded;
        try {
            loaded = flowEngine.loadFlow(key);
        } catch (Exception e) {
            return "{\"error\":\"流程 '" + key + "' 加载失败: " + e.getMessage() + "\"}";
        }
        if (loaded == null || loaded.graph == null) {
            return "{\"error\":\"流程 '" + key + "' 不存在\"}";
        }
        for (FlowGraph.FlowNode node : loaded.graph.getNodes()) {
            if ("llm".equals(node.getType()) && truthy(node.getConfig() == null ? null : node.getConfig().get("stream"))) {
                return "{\"error\":\"流程 '" + key + "' 含流式结点（" + node.getName()
                        + "），不支持 run_flow 同步调用\"}";
            }
        }
        // 守卫②③：深度 1 + 30s 超时（超时 interrupt 引擎线程走停止收口）；
        // 深度标记同步到执行线程（ThreadLocal 线程隔离，flow 节点内再嵌 run_flow 靠它拒绝）
        final ObjectNode vars = input;
        DEPTH.set(1);
        FlowContext context = new FlowContext(null);
        try {
            Future<Void> future = EXECUTOR.submit(() -> {
                DEPTH.set(1);
                try {
                java.util.Iterator<Map.Entry<String, JsonNode>> it = vars.fields();
                while (it.hasNext()) {
                    Map.Entry<String, JsonNode> e = it.next();
                    JsonNode v = e.getValue();
                    if (v.isValueNode()) {
                        context.setVariable(e.getKey(), v.isTextual() ? v.asText() : v.toString());
                    } else {
                        context.setVariable(e.getKey(), OM.writeValueAsString(v));
                    }
                }
                flowEngine.execute(key, context);
                return null;
                } finally {
                    DEPTH.remove();
                }
            });
            try {
                future.get(TIMEOUT_SECONDS, TimeUnit.SECONDS);
            } catch (TimeoutException te) {
                future.cancel(true);
                return "{\"error\":\"流程 '" + key + "' 执行超时（" + TIMEOUT_SECONDS + "s），已中断收口\"}";
            } catch (java.util.concurrent.ExecutionException ee) {
                Throwable cause = ee.getCause() == null ? ee : ee.getCause();
                log.warn("run_flow '{}' 执行失败: {}", key, cause.getMessage());
                return "{\"error\":\"流程执行失败: " + cause.getMessage() + "\"}";
            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
                future.cancel(true);
                return "{\"error\":\"任务已被用户停止\"}";
            }
            Map<String, Object> output = context.getOutput();
            ObjectNode out = OM.createObjectNode();
            out.put("ok", true);
            out.put("flowKey", key);
            if (context.getTraceId() != null) {
                out.put("traceId", context.getTraceId());
            }
            ObjectNode outVars = out.putObject("output");
            if (output != null) {
                for (Map.Entry<String, Object> e : output.entrySet()) {
                    outVars.put(e.getKey(), cellText(e.getValue()));
                }
            }
            return out.toString();
        } catch (Exception e) {
            log.warn("run_flow '{}' 调用异常: {}", key, e.getMessage());
            return "{\"error\":\"流程调用异常: " + e.getMessage() + "\"}";
        } finally {
            DEPTH.remove();
        }
    }

    /** config 布尔值弱判（true/"true"/"1"） */
    private static boolean truthy(Object v) {
        if (v == null) {
            return false;
        }
        if (v instanceof Boolean) {
            return (Boolean) v;
        }
        String s = String.valueOf(v).trim().toLowerCase();
        return "true".equals(s) || "1".equals(s);
    }

    private static String cellText(Object v) {
        if (v == null) {
            return "";
        }
        String s = String.valueOf(v);
        return s.length() > CELL_MAX_LEN ? s.substring(0, CELL_MAX_LEN) + "..." : s;
    }
}
