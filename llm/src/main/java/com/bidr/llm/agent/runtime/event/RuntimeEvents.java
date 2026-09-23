package com.bidr.llm.agent.runtime.event;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

/**
 * Title: RuntimeEvents
 * Description: 框架**规范过程事件**契约：词表 + 帧封套构造（{@code {type, session_id, message_id, data}}）。
 *
 * <p>🔴 所有权声明（M5-2b 第一步）：这套事件词表由框架拥有，**不是任何上游的原生线格式**。
 * 每个 {@code AgentRuntimeProvider} 实现负责把自己上游的事件翻译成这里定义的规范事件；
 * 上游若原生同形（agent-system 的开放协议恰好如此），"原样透传"就只是该实现的一项**优化**，
 * 而不是契约的定义来源——判据是：换一家上游时，需要改的只有它的 codec，规范事件与前端契约不动。</p>
 *
 * <p>投递形态：以 JSON 文本承载（{@link #frame} 返回序列化结果），经
 * {@code RuntimeTurnLink#nextFrame()} 逐条交给中转层泵给浏览器。选文本而非强类型对象，
 * 是为了让"上游原生同形即可透传"这条优化成立，同时保留协议只增不改的向前兼容
 * （未知 type 由消费侧忽略）。字段口径：REST 出参驼峰（框架惯例），**事件内字段保持上游原样
 * snake_case**，前端按 {@code session_id}/{@code tool_call_id}/{@code parent_path} 直读。</p>
 *
 * @author sharp
 * @since 2026/9/24
 */
public final class RuntimeEvents {

    /** 受理首帧：开流即发，data 空；前端据此落位气泡并结束"歧义窗口" */
    public static final String ACCEPTED = "message.accepted";

    /** 运行开始（带 agent 等元信息） */
    public static final String STARTED = "message.started";

    /**
     * 快照帧：携带此前全部帧，语义是**重置该消息本地累积态后逐帧归约**——
     * 挂流恢复与刷新续接靠它保证"无重无缺"
     */
    public static final String SNAPSHOT = "message.snapshot";

    /** 思考增量（子运行帧不直播思考） */
    public static final String THINKING_DELTA = "thinking.delta";

    /** 正文增量 */
    public static final String TEXT_DELTA = "text.delta";

    /** 工具调用：data 带 tool_name/tool_call_id/arguments */
    public static final String TOOL_CALL = "tool.call";

    /** 工具返回：data 带 tool_name/tool_call_id/output */
    public static final String TOOL_RESULT = "tool.result";

    /** 终局·完成：data.text 为权威全文，覆盖增量拼接结果 */
    public static final String DONE = "message.done";

    /** 终局·失败：data 带 code/message */
    public static final String ERROR = "message.error";

    /** 终局·取消 */
    public static final String CANCELLED = "message.cancelled";

    /** 子运行帧标记字段值：与 parent_path（父路径链）同用，前端按路径链归并到 spawn 步 */
    public static final String AGENT_TYPE_SUBAGENT = "subagent";

    /** 子 Agent 容器步的工具名约定（与前端视图模型 SPAWN_TOOL 对齐） */
    public static final String SPAWN_TOOL = "spawn_subagent";

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private RuntimeEvents() {
    }

    /** 构造一个事件 data 节点（provider 实现填字段用） */
    public static ObjectNode newData() {
        return MAPPER.createObjectNode();
    }

    /**
     * 组装一帧规范事件的 JSON 文本。
     *
     * @param type      本类常量之一；未知 type 会被前端忽略（协议只增不改）
     * @param data      可为 null（如受理帧），落为空对象
     */
    public static String frame(String type, String sessionId, String messageId, ObjectNode data) {
        ObjectNode envelope = MAPPER.createObjectNode();
        envelope.put("type", type);
        envelope.put("session_id", sessionId);
        envelope.put("message_id", messageId);
        envelope.set("data", data == null ? MAPPER.createObjectNode() : data);
        return envelope.toString();
    }

    /** 增量类事件（thinking/text delta）的便捷构造：data 只有一个 delta 字段 */
    public static String deltaFrame(String type, String sessionId, String messageId, String delta) {
        ObjectNode data = newData();
        data.put("delta", delta == null ? "" : delta);
        return frame(type, sessionId, messageId, data);
    }
}
