package com.bidr.llm.agent;

/**
 * Title: DetachPolicy
 * Description: 前端断开后会话策略（经 {@link AutonomousAgentDefinition#detachPolicy()} 声明）：
 * 前端以 status 轮询为存活信号（刷新/关页/离开即信号消失），心跳任务检测信号消失超阈值后
 * 按策略处置——分场景口径：用户型会话断开即停省 token，功能型任务断开后台继续可重连
 *
 * @author Sharp
 * @since 2026/8/23
 */
public enum DetachPolicy {

    /** 前端断开超阈值自动停止（用户场景默认：断开即断，不再空耗 token） */
    STOP_ON_DETACH,

    /** 前端断开后台继续执行（功能场景：如配置/资产生成，刷新后可经活跃会话列表重连） */
    KEEP_RUNNING
}
