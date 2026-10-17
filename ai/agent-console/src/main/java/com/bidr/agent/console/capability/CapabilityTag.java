package com.bidr.agent.console.capability;

/**
 * Title: CapabilityTag
 * Description: 能力包的能力标签——勾选闸门与诊断用的**副作用分级**，不是权限模型。
 * <p>
 * 只读边界由"能力包缺席"结构性保证（工具不挂＝模型摸不到），不由提示词承诺保证，
 * 所以标签必须能一眼区分"碰业务数据的写"与"只动会话的写"：{@code submitPlan} 改写会话台账
 * 但不写业务产物，标成 READ 会让"只读档"的判据失真，标成 WRITE 又会让它被误关。
 *
 * @author Sharp
 * @since 2026-10-12
 */
public enum CapabilityTag {

    /** 只读取证：读上下文/读外部结果，不产生任何业务侧变更 */
    READ,

    /** 会话控制：只动本次会话的运行态（计划台账、向用户提问、下一步候选），不触达业务数据 */
    SESSION,

    /** 业务写入：产出或修改业务数据（落稿、写库） */
    WRITE,

    /** 外部效应：触达外部运行时或编排流程，副作用范围由那侧决定、本层不可枚举 */
    EXTERNAL_EFFECT
}
