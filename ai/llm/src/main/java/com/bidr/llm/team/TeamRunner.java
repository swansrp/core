package com.bidr.llm.team;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Title: TeamRunner
 * Description: 团队编排执行器（T4.2 团队编排门面）——按 {@link TeamDefinition} 组织多成员协作，
 * 单成员产出经 {@link TeamMemberExecutor} 接缝（生产=模型驱动，单测=lambda mock）：
 * <ul>
 *     <li>{@link TeamMode#PIPELINE}：成员按定义顺序执行，前者输出作为后者输入，末位成员输出即结论；</li>
 *     <li>{@link TeamMode#ROUTER}：第一成员为主控，输出中含 {@code @[memberKey]} 指令即按出现顺序
 *         派发给对应成员（去重、以原始输入为任务），收集结果——结论=主控路由说明+各派发结果汇集；
 *         未识别的 memberKey 跳过（WARN 留痕）；</li>
 *     <li>{@link TeamMode#DEBATE}：各成员依次出一轮意见（输入=原始任务），主持人按
 *         {@link TeamDefinition#getModerator()} 提示词（缺省用内置主持提示词）汇总裁决，裁决即结论。</li>
 * </ul>
 * 失败语义 fail-fast 但保留现场：某成员抛错即停止后续环节，已执行步骤与失败原因随结果返回
 * （编排台据此展示到哪一步、卡在谁）。
 *
 * @author Sharp
 * @since 2026/11/1
 */
@Slf4j
@Service
public class TeamRunner {

    /**
     * 步骤角色：普通成员
     */
    public static final String ROLE_MEMBER = "member";

    /**
     * 步骤角色：路由主控
     */
    public static final String ROLE_ROUTER = "router";

    /**
     * 步骤角色：辩论主持人
     */
    public static final String ROLE_MODERATOR = "moderator";

    /**
     * 路由派发指令形态：@[memberKey]（key 取到首个空白/右括号前，容忍全角空格）
     */
    private static final Pattern ROUTE_DIRECTIVE = Pattern.compile("@\\[([^\\]\\s]+)\\]");

    /**
     * 辩论模式主持人提示词缺省兜底（定义未配 moderator 时）
     */
    private static final String DEFAULT_MODERATOR_PROMPT =
            "你是团队主持人。请汇总各成员意见：指出共识与分歧，给出最终结论（裁决）。";

    /**
     * 成员执行接缝（生产=ChatLanguageModel 驱动，单测=lambda mock）
     */
    private final TeamMemberExecutor executor;

    public TeamRunner(TeamMemberExecutor executor) {
        if (executor == null) {
            throw new IllegalArgumentException("TeamMemberExecutor 不能为空");
        }
        this.executor = executor;
    }

    /**
     * 按定义执行一次团队协作（同步阻塞）；失败不抛出——失败原因与已执行步骤随结果返回
     */
    public TeamRunResult run(TeamDefinition definition, String input) {
        if (definition == null) {
            throw new IllegalArgumentException("团队定义不能为空");
        }
        if (definition.getMembers() == null || definition.getMembers().isEmpty()) {
            throw new IllegalArgumentException("团队至少需要一名成员: " + definition.getTeamKey());
        }
        if (definition.getMode() == null) {
            throw new IllegalArgumentException("编排模式不能为空: " + definition.getTeamKey());
        }
        TeamRunResult result = new TeamRunResult();
        result.setTeamKey(definition.getTeamKey());
        result.setTeamName(definition.getName());
        result.setMode(definition.getMode().name());
        long startMs = System.currentTimeMillis();
        try {
            switch (definition.getMode()) {
                case PIPELINE:
                    runPipeline(definition, input, result);
                    break;
                case ROUTER:
                    runRouter(definition, input, result);
                    break;
                case DEBATE:
                    runDebate(definition, input, result);
                    break;
                default:
                    throw new IllegalArgumentException("不支持的编排模式: " + definition.getMode());
            }
        } catch (RuntimeException e) {
            // fail-fast 但保留现场：失败原因与已执行步骤随结果返回，编排台可见卡在哪一步
            log.warn("团队编排执行失败, teamKey={}, mode={}", definition.getTeamKey(), definition.getMode(), e);
            result.setError(e.getMessage() == null ? "团队编排执行失败" : e.getMessage());
        }
        result.setCostMs(System.currentTimeMillis() - startMs);
        return result;
    }

    /**
     * 流水线：顺序移交，前者输出=后者输入，末位输出即结论
     */
    private void runPipeline(TeamDefinition definition, String input, TeamRunResult result) {
        String current = input;
        List<TeamMember> members = definition.getMembers();
        for (TeamMember member : members) {
            TeamRunResult.MemberStep step = executeMember(member, current, ROLE_MEMBER);
            result.getSteps().add(step);
            if (step.getError() != null) {
                throw new IllegalStateException("成员 " + displayName(member) + " 执行失败: " + step.getError());
            }
            current = step.getOutput();
        }
        result.setFinalOutput(current);
    }

    /**
     * 路由分发：首位成员为主控，输出中的 @[memberKey] 按出现顺序去重派发（任务=原始输入），
     * 结论=主控路由说明 + 各派发结果汇集；主控自身失败即整体失败
     */
    private void runRouter(TeamDefinition definition, String input, TeamRunResult result) {
        List<TeamMember> members = definition.getMembers();
        TeamMember router = members.get(0);
        TeamRunResult.MemberStep routerStep = executeMember(router, input, ROLE_ROUTER);
        result.getSteps().add(routerStep);
        if (routerStep.getError() != null) {
            throw new IllegalStateException("主控 " + displayName(router) + " 执行失败: " + routerStep.getError());
        }

        List<TeamMember> dispatched = routeTargets(members, routerStep.getOutput());
        StringBuilder finalOutput = new StringBuilder(routerStep.getOutput());
        for (TeamMember member : dispatched) {
            TeamRunResult.MemberStep step = executeMember(member, input, ROLE_MEMBER);
            result.getSteps().add(step);
            if (step.getError() != null) {
                throw new IllegalStateException("成员 " + displayName(member) + " 执行失败: " + step.getError());
            }
            finalOutput.append("\n\n【").append(displayName(member)).append("】\n").append(step.getOutput());
        }
        result.setFinalOutput(finalOutput.toString());
    }

    /**
     * 主控输出 → 派发目标（按出现顺序去重；未识别 key 跳过留痕）
     */
    private List<TeamMember> routeTargets(List<TeamMember> members, String routerOutput) {
        Set<String> seen = new LinkedHashSet<>();
        Matcher matcher = ROUTE_DIRECTIVE.matcher(routerOutput == null ? "" : routerOutput);
        while (matcher.find()) {
            seen.add(matcher.group(1));
        }
        List<TeamMember> targets = new ArrayList<>();
        for (String key : seen) {
            TeamMember hit = findByKey(members, key);
            if (hit == null) {
                log.warn("路由指令指向未识别成员, key={}", key);
                continue;
            }
            targets.add(hit);
        }
        return targets;
    }

    /**
     * 辩论评审：各成员依次出一轮意见（输入=原始任务），主持人汇总裁决即结论
     */
    private void runDebate(TeamDefinition definition, String input, TeamRunResult result) {
        StringBuilder opinions = new StringBuilder();
        for (TeamMember member : definition.getMembers()) {
            TeamRunResult.MemberStep step = executeMember(member, input, ROLE_MEMBER);
            result.getSteps().add(step);
            if (step.getError() != null) {
                throw new IllegalStateException("成员 " + displayName(member) + " 执行失败: " + step.getError());
            }
            opinions.append("\n【").append(displayName(member)).append("】\n").append(step.getOutput()).append("\n");
        }
        TeamMember moderator = new TeamMember();
        moderator.setKey("moderator");
        moderator.setName("主持人");
        moderator.setPrompt(StringUtils.hasText(definition.getModerator())
                ? definition.getModerator() : DEFAULT_MODERATOR_PROMPT);
        String moderatorInput = "原始任务：\n" + (input == null ? "" : input) + "\n\n各成员意见："
                + opinions + "\n请给出最终结论。";
        TeamRunResult.MemberStep step = executeMember(moderator, moderatorInput, ROLE_MODERATOR);
        result.getSteps().add(step);
        if (step.getError() != null) {
            throw new IllegalStateException("主持人汇总裁决失败: " + step.getError());
        }
        result.setFinalOutput(step.getOutput());
    }

    /**
     * 执行单成员并记录步（异常转为步内 error，由各模式决定终止语义）
     */
    private TeamRunResult.MemberStep executeMember(TeamMember member, String input, String role) {
        TeamRunResult.MemberStep step = new TeamRunResult.MemberStep();
        step.setMemberKey(member.getKey());
        step.setMemberName(displayName(member));
        step.setRole(role);
        step.setInput(input);
        long startMs = System.currentTimeMillis();
        try {
            step.setOutput(executor.execute(member, input));
        } catch (RuntimeException e) {
            log.warn("团队成员执行失败, memberKey={}", member.getKey(), e);
            step.setError(e.getMessage() == null ? "成员执行失败" : e.getMessage());
        }
        step.setCostMs(System.currentTimeMillis() - startMs);
        return step;
    }

    private static TeamMember findByKey(List<TeamMember> members, String key) {
        for (TeamMember member : members) {
            if (key.equals(member.getKey())) {
                return member;
            }
        }
        return null;
    }

    private static String displayName(TeamMember member) {
        return StringUtils.hasText(member.getName()) ? member.getName()
                : (StringUtils.hasText(member.getKey()) ? member.getKey() : "未命名成员");
    }
}
