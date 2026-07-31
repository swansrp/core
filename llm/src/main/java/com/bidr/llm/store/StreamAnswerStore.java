package com.bidr.llm.store;

/**
 * 流式回答状态存储接口。
 * <p>
 * 用于在"生产端持续写入、消费端按需读取"的流式回答场景中共享中间状态，
 * 典型链路：LLM 流式回调逐步写入 → 前端轮询 / 三方平台刷新回调读取当前内容。
 * 实现方决定存储介质（Redis / 内存等）与过期策略。
 * </p>
 *
 * @author Sharp
 */
public interface StreamAnswerStore {

    /**
     * 更新流式回答内容。
     *
     * @param streamId 流标识（如消息ID、任务ID）
     * @param content  当前全量内容（覆盖式，非增量）
     * @param finish   流是否已结束；true 时实现方必须立即落存储，不得被节流跳过
     */
    void updateContent(String streamId, String content, boolean finish);

    /**
     * 更新完整状态（含业务扩展字段）。
     *
     * @param streamId 流标识
     * @param state    完整状态对象
     */
    void updateState(String streamId, StreamAnswerState state);

    /**
     * 获取当前已生成内容。
     *
     * @param streamId 流标识
     * @return 当前内容，不存在时返回 null
     */
    String getCurrentContent(String streamId);

    /**
     * 获取当前完整状态。
     *
     * @param streamId 流标识
     * @return 状态对象，不存在或读取失败时返回 null
     */
    StreamAnswerState getState(String streamId);
}
