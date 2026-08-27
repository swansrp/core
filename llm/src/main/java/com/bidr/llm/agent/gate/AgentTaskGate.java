package com.bidr.llm.agent.gate;

import com.bidr.kernel.exception.NoticeException;

/**
 * Title: AgentTaskGate
 * Description: 全局单任务闸门（会话无关的分布式互斥原语）——「全平台同时仅一个在途任务」
 * 语义的通用实现：锁值存 {属主令牌, 心跳}，任务侧周期调 {@link #heartbeat} 续期；
 * 属主失联（宕机/重启杀线程）后无人续期，查询/停止/再发起入口经 {@link #forceUnlockIfOrphan}
 * 与 {@link #checkFree}/{@link #acquire} 内置的自愈逻辑当场强删残留锁，无需干等 TTL。
 * <p>
 * 适用边界：仅互斥+活性自愈。停止信号、进度协议、可续作语义属业务层，不在本原语范围。
 * 占用提示经 {@link NoticeException} 抛出，文案由调用方传入（业务友好口径）。
 *
 * @author Sharp
 * @since 2026/8/23
 */
public interface AgentTaskGate {

    /**
     * 独占获取任务键：占用且属主存活 → 抛占用提示（{@link NoticeException}，busyMessage）；
     * 占用但属主失联（锁内心跳超失联阈值）→ 强删后重试一次（失联自愈）
     *
     * @param taskKey     全局任务键（业务自定义，如 asset-gen）
     * @param ownerToken  属主令牌（须 JVM 级：请求线程拿锁、异步线程释锁场景通用）
     * @param ttlSeconds  锁 TTL（心跳周期续期；失联后兜底自动失效时长）
     * @param busyMessage 占用时的友好提示文案
     */
    void acquire(String taskKey, String ownerToken, int ttlSeconds, String busyMessage);

    /** 预检不拿锁（会话创建前的在途拦截用）：先自愈失联残留锁，仍占用 → 抛占用提示 */
    void checkFree(String taskKey, String busyMessage);

    /** 心跳续期：仅属主匹配时刷新锁内心跳与 TTL（任务侧周期调用，活动即存活信号） */
    void heartbeat(String taskKey, String ownerToken, int ttlSeconds);

    /** 释放：仅属主匹配才删（防误删他实例/后继任务的锁） */
    void release(String taskKey, String ownerToken);

    /** 失联强解锁：锁存在且内心跳超失联阈值即删（任意实例可调，停止/查询入口自愈用）；返回是否解锁 */
    boolean forceUnlockIfOrphan(String taskKey);
}
