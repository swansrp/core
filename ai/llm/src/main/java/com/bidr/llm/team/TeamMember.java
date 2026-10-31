package com.bidr.llm.team;

import lombok.Data;

/**
 * Title: TeamMember
 * Description: 团队成员定义（T4.2 团队编排门面）——key 为路由派发指令 {@code @[key]} 的目标标识，
 * prompt 为该成员的人设/职责提示词（经 {@link TeamMemberExecutor} 执行时组装进模型调用）。
 *
 * @author Sharp
 * @since 2026/11/1
 */
@Data
public class TeamMember {

    /**
     * 成员标识（团队内唯一；路由模式的派发指令 @[key] 即指向它）
     */
    private String key;

    /**
     * 成员显示名（结果展示与轨迹记录用）
     */
    private String name;

    /**
     * 成员提示词（人设/职责；空则该成员无 system 提示，仅按输入作答）
     */
    private String prompt;
}
