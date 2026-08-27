package com.bidr.llm.agent.session;

import java.util.List;

/**
 * Title: AgentSessionStore
 * Description: agent 会话存储契约——事件流 + 状态快照 + 分布式控制键（暂停/停止/作答）：
 * <ul>
 *     <li>事件流：会话内 seq 单调递增，前端按 sinceSeq 增量拉取；append 约定单写者
 *     （run 线程）——实现无需并发写防护，读取侧随时并发；</li>
 *     <li>控制键：分布式多实例下任意实例可暂停/停止属主实例的会话（键轮询生效，
 *         属主实例的中断仅加速收口）。</li>
 * </ul>
 * 默认双实现（AgentSessionAutoConfiguration 装配）：Redis（生产，TTL 兜底宕机释放）/
 * 内存（无 core/redis 时的单实例 fallback）
 *
 * @author Sharp
 * @since 2026/8/20
 */
public interface AgentSessionStore {

    /** 覆盖保存会话状态（实现负责 TTL 续期） */
    void saveState(AgentSessionState state);

    /** 读取会话状态；不存在返回 null */
    AgentSessionState getState(String sessionId);

    /** 现存全部会话标识（含已终态未过期；活跃会话列表端点枚举用，服务层再过滤） */
    List<String> listSessionIds();

    /** 追加事件（store 分配 seq，1 起单调递增），返回分配的序号 */
    long appendEvent(String sessionId, String type, Object payload);

    /** 读取 seq 大于 sinceSeq 的事件（增量轮询）；不存在返回空列表 */
    List<AgentEvent> events(String sessionId, long sinceSeq);

    // ---- 分布式控制键（暂停/停止/作答） ----

    /** 刷新前端存活信号（status 轮询即调用；独立轻键，不涉整条快照读改写） */
    void touchView(String sessionId);

    /** 前端最近存活时间（无信号返回 0；断开即停判定依据） */
    long viewTime(String sessionId);

    /** 暂停会话（note 为暂停说明，前端状态条展示；幂等，重复暂停覆盖说明） */
    void pause(String sessionId, String note);

    /** 恢复会话（guidance 非空时作为恢复指导语，引擎注入下一轮上下文） */
    void resume(String sessionId, String guidance);

    /** 是否处于暂停态 */
    boolean isPaused(String sessionId);

    /** 取出并清除恢复指导语（一次性消费；无指导语返回 null） */
    String consumeResumeGuidance(String sessionId);

    /** 提交用户作答（answerJson 含 questionId/answer/skipped，等待侧按 id 匹配防串答） */
    void submitAnswer(String sessionId, String answerJson);

    /** 取出并清除作答（一次性消费；无作答返回 null） */
    String consumeAnswer(String sessionId);

    /** 请求停止（TTL 兜底，run 线程轮询感知收口） */
    void requestStop(String sessionId);

    /** 是否已请求停止 */
    boolean isStopRequested(String sessionId);

    /** 会话收口清理控制键（终态后调用，幂等） */
    void clearControls(String sessionId);
}
