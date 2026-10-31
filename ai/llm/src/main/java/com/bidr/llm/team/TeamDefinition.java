package com.bidr.llm.team;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * Title: TeamDefinition
 * Description: 团队编排定义（T4.2 团队编排门面）——一次多成员协作的静态配置：
 * 模式 + 成员清单 + （辩论模式的）主持人提示词。经 {@link TeamDefinitionStore} 落库，
 * {@link TeamRunner} 按定义执行。
 *
 * @author Sharp
 * @since 2026/11/1
 */
@Data
public class TeamDefinition {

    /**
     * 团队标识（唯一键，保存即按此幂等覆盖）
     */
    private String teamKey;

    /**
     * 团队显示名
     */
    private String name;

    /**
     * 编排模式（流水线/路由分发/辩论评审）
     */
    private TeamMode mode = TeamMode.PIPELINE;

    /**
     * 成员清单（PIPELINE 按列表顺序移交；ROUTER 首位为主控；DEBATE 各出一轮意见）
     */
    private List<TeamMember> members = new ArrayList<>();

    /**
     * 主持人提示词（DEBATE 模式汇总裁决用；缺省时用 {@link TeamRunner} 内置主持提示词兜底）
     */
    private String moderator;
}
