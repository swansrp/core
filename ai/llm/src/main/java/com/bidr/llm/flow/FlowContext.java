package com.bidr.llm.flow;

import com.bidr.llm.sse.SseEventSender;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BooleanSupplier;

/**
 * Title: FlowContext
 * Description: 流程执行上下文——贯穿一次链路执行的变量池与 SSE 发送器：
 * <ul>
 *     <li>变量池：start 结点注入输入（question/history 等由调用方注入），各结点写入产出，
 *     模板渲染与条件边从这里取值；</li>
 *     <li>SSE 发送器：流式结点（llm stream 等）借它直推前端事件，可为 null（非流式链路）；</li>
 *     <li>output 变量：output 结点把变量映射到响应字段，链路结束时由调用方取回。</li>
 * </ul>
 * 流式结点挂起后由模型回调线程 resume，变量池用并发容器保证线程安全。
 *
 * @author Sharp
 * @since 2026/8/16
 */
public class FlowContext {

    // ---- 执行轨迹调试变量（执行器写入全文，引擎埋点读取后清除；__ 前缀避免与业务变量冲突）----

    /**
     * llm 结点：渲染后的提示词全文
     */
    public static final String TRACE_LLM_PROMPT = "__trace_llm_prompt";

    /**
     * llm 结点：模型回答全文（流式结点在 onComplete 回调写入后 resume）
     */
    public static final String TRACE_LLM_ANSWER = "__trace_llm_answer";

    /**
     * extract 类结点：输入文本（LLM 原始输出）——业务结点自用约定
     */
    public static final String TRACE_EXTRACT_INPUT = "__trace_extract_input";

    /**
     * extract 类结点：提取结果摘要——业务结点自用约定
     */
    public static final String TRACE_EXTRACT_RESULT = "__trace_extract_result";

    /**
     * 变量池（结点产出与输入，按变量名共享）
     */
    private final Map<String, Object> variables = new ConcurrentHashMap<>();

    /**
     * SSE 事件发送器（流式链路必填，同步链路为 null）
     */
    private final SseEventSender sseSender;

    /**
     * output 结点映射出的响应变量（链路结束由调用方读取）
     */
    private final Map<String, Object> output = new ConcurrentHashMap<>();

    /**
     * 当前执行的 DAG 定义（引擎执行时注入；流式结点挂起后，模型回调线程借它 resume 续跑）
     */
    private FlowGraph graph;

    /**
     * 轨迹标识（引擎 execute 时注入；挂起续跑沿用同一 id 归并轨迹）
     */
    private String traceId;

    /**
     * 访问人（发起方注入，引擎 startTrace 时写入轨迹；为空回落 anonymous）
     */
    private String operator;

    /**
     * 链路收口标识（业务方注入并自行解读——如 ask 链的历史对话 id，
     * {@link FlowExecutionListener} 收口回调时据此补写业务记录；为空表示无需收口处理）
     */
    private String conversationId;

    /**
     * 停止检查（可空）：引擎每结点执行前轮询，true 即收口——轨迹标记 stopped、
     * 收口回调携带 {@link FlowEngine#STOP_SIGNAL}、不再执行后续结点；业务接分布式停止键
     */
    private BooleanSupplier stopSupplier;

    /** 当前链路是否收到停止请求（未接停止检查时恒 false） */
    public boolean isStopRequested() {
        return stopSupplier != null && stopSupplier.getAsBoolean();
    }

    public BooleanSupplier getStopSupplier() {
        return stopSupplier;
    }

    public void setStopSupplier(BooleanSupplier stopSupplier) {
        this.stopSupplier = stopSupplier;
    }

    public FlowContext(SseEventSender sseSender) {
        this.sseSender = sseSender;
    }

    /**
     * @param value null 值视为不设置（ConcurrentHashMap 不接受 null value；下游 get 返回 null 表示变量缺失）
     */
    public void setVariable(String name, Object value) {
        if (name != null && value != null) {
            variables.put(name, value);
        }
    }

    public void removeVariable(String name) {
        if (name != null) {
            variables.remove(name);
        }
    }

    public Object getVariable(String name) {
        return name == null ? null : variables.get(name);
    }

    /**
     * 取变量并转字符串（缺失返回空串，模板渲染不中断）
     */
    public String getString(String name) {
        Object value = getVariable(name);
        return value == null ? "" : String.valueOf(value);
    }

    /**
     * 取变量并按给定类型弱转（List/Map 等结构变量用）
     */
    @SuppressWarnings("unchecked")
    public <T> T get(String name, Class<T> type) {
        Object value = getVariable(name);
        return value == null ? null : (T) value;
    }

    /**
     * @param value null 值视为不设置（ConcurrentHashMap 不接受 null value；
     *               调用方按「字段缺失=未产出」判别）
     */
    public void putOutput(String name, Object value) {
        if (name != null && value != null) {
            output.put(name, value);
        }
    }

    public Map<String, Object> getOutput() {
        return output;
    }

    public SseEventSender getSseSender() {
        return sseSender;
    }

    /**
     * 便捷注入：历史对话（元素实现 {@link com.bidr.llm.flow.LlmHistoryMessage}），供 llm 结点 includeHistory 使用
     */
    public void setHistory(List<?> history) {
        setVariable("history", history);
    }

    public FlowGraph getGraph() {
        return graph;
    }

    public void setGraph(FlowGraph graph) {
        this.graph = graph;
    }

    public String getTraceId() {
        return traceId;
    }

    public void setTraceId(String traceId) {
        this.traceId = traceId;
    }

    public String getOperator() {
        return operator;
    }

    public void setOperator(String operator) {
        this.operator = operator;
    }

    public String getConversationId() {
        return conversationId;
    }

    public void setConversationId(String conversationId) {
        this.conversationId = conversationId;
    }
}
