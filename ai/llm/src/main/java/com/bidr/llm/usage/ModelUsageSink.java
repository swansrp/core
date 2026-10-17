package com.bidr.llm.usage;

/**
 * Title: ModelUsageSink
 * Description: 模型用量上报回调（M14 棒5 计量的框架侧唯一出口）。
 *
 * <p><b>为什么挂在这里</b>：采集点选在模型包装层（{@code RawSyncChatModel} /
 * {@code RawSseStreamingChatModel} / {@code RawVisionChatModel}），因为"每次模型调用一行"
 * 的粒度只有这一层数得准——消费方（会话、摘要、拟标题、范围解析、insight）各写各的，
 * 任何上层埋点都会漏掉后来者。挂在这一层＝<b>任何消费方都绕不过</b>。</p>
 *
 * <p><b>装配口径同 {@code AgentToolSurfaceSink}</b>：实现由能力台账模块（core/agent-console）
 * 经 Spring 提供，框架侧经 {@code BeanUtil} 现取，取不到即静默跳过 ⇒ 不装台账的工程零行为变化；
 * 回调异常一律由 {@link ModelUsageReporter} 吞掉记 warn，绝不击穿模型调用。</p>
 *
 * @author Sharp
 * @since 2026-10-13
 */
public interface ModelUsageSink {

    /**
     * 一次模型调用收口（仅成功腿：失败腿网关不给 usage，落一行空数字对计量无意义）
     *
     * @param facts 框架侧用量事实，不含任何计价语义与明文凭据
     */
    void report(ModelUsageFacts facts);
}
