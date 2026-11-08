package com.bidr.llm.team;

/**
 * Title: TeamMemberExecutor
 * Description: 团队成员执行器（函数式接缝，T4.2 团队编排门面）——{@link TeamRunner} 只负责
 * 协作编排（移交/派发/裁决），单成员的"给提示词与输入、产出文本"走本接缝：
 * <ul>
 *     <li>生产装配：{@code ChatModelTeamMemberExecutor}（既有 ChatModel 抽象驱动，
 *         member.prompt 组装 system、input 组装 user）；</li>
 *     <li>单测：传 lambda 即可 mock 成员行为（不依赖模型、不起容器），对应机制断言 M-8 的落地方式。</li>
 * </ul>
 *
 * @author Sharp
 * @since 2026/11/1
 */
@FunctionalInterface
public interface TeamMemberExecutor {

    /**
     * 执行单个成员：输入提示词人设（member.prompt）与本次输入，返回该成员的产出文本；
     * 执行失败直接抛异常（由 {@link TeamRunner} 按模式决定终止语义）
     */
    String execute(TeamMember member, String input);
}
