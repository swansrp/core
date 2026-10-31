package com.bidr.llm.team;

/**
 * Title: TeamMode
 * Description: 团队编排模式（T4.2 团队编排门面）——三种协作形态：
 * <ul>
 *     <li>{@link #PIPELINE}：流水线——成员按定义顺序执行，前者输出作为后者输入，末位成员输出即结论；</li>
 *     <li>{@link #ROUTER}：路由分发——第一成员为主控，其输出中含 {@code @[memberKey]} 指令即派发给
 *         对应成员（以原始输入为任务），收集各成员结果；</li>
 *     <li>{@link #DEBATE}：辩论评审——各成员依次出一轮意见，主持人（moderator prompt）汇总裁决。</li>
 * </ul>
 *
 * @author Sharp
 * @since 2026/11/1
 */
public enum TeamMode {

    /**
     * 流水线：顺序移交（前者输出 = 后者输入）
     */
    PIPELINE,

    /**
     * 路由分发：主控派发（@[memberKey] 指令）
     */
    ROUTER,

    /**
     * 辩论评审：各出一轮意见 + 主持裁决
     */
    DEBATE
}
