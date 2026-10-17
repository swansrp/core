package com.bidr.llm.agent.gate;

import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Title: GateLock
 * Description: 闸门锁值（JSON 存锁键）：属主令牌 + 心跳时刻。
 * 心跳由任务侧经 {@link AgentTaskGate#heartbeat} 周期刷新，
 * 超时未刷即判定属主失联（失联强解锁的依据）
 *
 * @author Sharp
 * @since 2026/8/23
 */
@Data
@NoArgsConstructor
public class GateLock {

    /** 属主令牌（JVM 级；释放/续期须匹配，防误删他实例锁） */
    private String ownerToken;

    /** 心跳时刻（毫秒；acquire 与每次 heartbeat 刷新） */
    private long heartbeat;

    public GateLock(String ownerToken, long heartbeat) {
        this.ownerToken = ownerToken;
        this.heartbeat = heartbeat;
    }
}
