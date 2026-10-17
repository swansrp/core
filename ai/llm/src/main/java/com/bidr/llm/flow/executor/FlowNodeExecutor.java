package com.bidr.llm.flow.executor;

import com.bidr.llm.flow.FlowContext;
import com.bidr.llm.flow.FlowGraph;
import com.bidr.llm.flow.FlowNodeMeta;

import java.util.Map;

/**
 * Title: FlowNodeExecutor
 * Description: DAG 结点执行器——按 type 注册为 Spring Bean（封闭集：引擎按 Bean 注册的 type 分发，
 * 未注册的类型校验时拒绝），业务模块扩展结点即新增实现类：
 * <ul>
 *     <li>{@link #execute} 返回 true 表示结点已完成，引擎沿出边继续；</li>
 *     <li>返回 false 表示挂起（流式 LLM 结点），模型回调线程写完输出变量后调
 *         {@code FlowEngine.resume(nodeId, ctx)} 续跑后续结点；</li>
 *     <li>抛出异常即链路失败：SSE 链路发 error 事件后关闭，非 SSE 链路向上抛出。</li>
 * </ul>
 * 执行轨迹的摘要/全文/挂起补充/调试变量清理也内聚在本接口（default 方法），
 * 引擎埋点统一回调，业务执行器按需覆写。
 *
 * @author Sharp
 * @since 2026/8/16
 */
public interface FlowNodeExecutor {

    /**
     * 结点类型标识（封闭集：通用层 start/llm/output + 业务方扩展注册的类型）
     */
    String type();

    /**
     * 结点类型元数据（工作台画布 palette 与属性表单的数据源）：type/label/desc + 配置表单 schema。
     * 默认 label=type、无配置表单；执行器按需覆写（label/desc 文案、fields 各控件声明），
     * fields 的 defaultValue 同时约定画布新增结点的初始 config
     */
    default FlowNodeMeta nodeMeta() {
        return FlowNodeMeta.of(type(), type(), "");
    }

    /**
     * 执行结点；返回 false 表示挂起，等待流式回调续跑
     */
    boolean execute(FlowGraph.FlowNode node, FlowContext context);

    /**
     * 读字符串型 config，缺失时返回默认值
     */
    default String strConfig(Map<String, Object> config, String key, String defaultValue) {
        Object value = config.get(key);
        return value == null ? defaultValue : String.valueOf(value);
    }

    /**
     * 读布尔型 config（画布保存的 JSON 布尔值），缺失时返回默认值
     */
    default boolean boolConfig(Map<String, Object> config, String key, boolean defaultValue) {
        Object value = config.get(key);
        return value instanceof Boolean ? (Boolean) value : defaultValue;
    }

    /**
     * 执行轨迹摘要（一行）：完成/挂起埋点时由引擎调用；类型专属摘要（如 llm 的输出字数）
     * 由执行器覆写，默认通用文案
     */
    default String traceSummary(FlowGraph.FlowNode node, FlowContext context, boolean suspended) {
        return "执行完成";
    }

    /**
     * 执行轨迹调试全文（如 llm 渲染后提示词、提取输入与结果）：无则返回 null；
     * 执行器把全文写入调试变量（{@link FlowContext} 的 TRACE_* 常量），引擎埋点读取后清除
     */
    default String traceDetail(FlowGraph.FlowNode node, FlowContext context, boolean suspended) {
        return null;
    }

    /**
     * 挂起结点续跑时的轨迹补充全文（如流式 llm 的模型回答）：无则返回 null，引擎补记到该结点详情
     */
    default String suspendedTraceSupplement(FlowContext context) {
        return null;
    }

    /**
     * 清除轨迹调试变量（埋点读取后由引擎调用，避免全文残留变量池）
     */
    default void clearTraceVars(FlowContext context) {
    }
}
