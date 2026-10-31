package com.bidr.llm.team;

import org.junit.Assert;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Title: TeamRunnerTest
 * Description: 三模式执行用例（T4.2 团队编排门面，机制断言 M-8 落地）：成员执行经
 * {@link TeamMemberExecutor} lambda mock（不起容器、不依赖模型）——
 * 流水线顺序移交（3 成员链式输入断言）/ 辩论 2+1（主持人收到两份意见并产出共识、
 * 自定义主持提示词透传）/ 路由分发（@[key] 按出现顺序去重派发、未识别 key 跳过、
 * 结论汇集）/ fail-fast 保留现场（失败步记录、error 回填、结论置空）。
 *
 * @author Sharp
 * @since 2026/11/1
 */
public class TeamRunnerTest {

    /**
     * 调用留痕：memberKey + " <- " + input，按执行顺序追加
     */
    private final List<String> calls = new ArrayList<>();

    /**
     * 主持人收到的提示词与输入（辩论用例断言用）
     */
    private TeamMember moderatorMember;
    private String moderatorInput;

    private TeamMember member(String key, String name) {
        TeamMember member = new TeamMember();
        member.setKey(key);
        member.setName(name);
        member.setPrompt("人设-" + key);
        return member;
    }

    private TeamDefinition team(TeamMode mode, TeamMember... members) {
        TeamDefinition definition = new TeamDefinition();
        definition.setTeamKey("t-" + mode.name().toLowerCase());
        definition.setName("测试团队");
        definition.setMode(mode);
        definition.setMembers(new ArrayList<>(Arrays.asList(members)));
        return definition;
    }

    /**
     * 记录并产出统一形态输出：out[key:input]
     */
    private TeamMemberExecutor echoExecutor() {
        return (member, input) -> {
            calls.add(member.getKey() + " <- " + input);
            return "out[" + member.getKey() + ":" + input + "]";
        };
    }

    /**
     * 流水线：3 成员顺序移交——成员1 收原始输入，成员2 收成员1 输出，成员3 收成员2 输出，
     * 末位输出即结论
     */
    @Test
    public void pipelineHandsOutputToNextMemberInOrder() {
        TeamDefinition team = team(TeamMode.PIPELINE,
                member("a", "翻译"), member("b", "校对"), member("c", "排版"));
        TeamRunResult result = new TeamRunner(echoExecutor()).run(team, "原文");

        Assert.assertNull(result.getError());
        Assert.assertEquals(3, result.getSteps().size());
        Assert.assertEquals("out[c:out[b:out[a:原文]]]", result.getFinalOutput());
        Assert.assertEquals(Arrays.asList(
                "a <- 原文",
                "b <- out[a:原文]",
                "c <- out[b:out[a:原文]]"), calls);
        // 步骤元信息：顺序、角色、输入随步可查
        Assert.assertEquals(TeamRunner.ROLE_MEMBER, result.getSteps().get(0).getRole());
        Assert.assertEquals("原文", result.getSteps().get(0).getInput());
        Assert.assertEquals("排版", result.getSteps().get(2).getMemberName());
        Assert.assertEquals("PIPELINE", result.getMode());
    }

    /**
     * 辩论：2 成员各出一轮意见，主持人收齐两份意见 + 原始任务后裁决；自定义主持提示词透传
     */
    @Test
    public void debateModeratorReceivesAllOpinionsAndConcludes() {
        TeamDefinition team = team(TeamMode.DEBATE, member("a", "正方"), member("b", "反方"));
        team.setModerator("你是资深评审，请裁决");
        TeamRunner runner = new TeamRunner((member, input) -> {
            calls.add(member.getKey() + " <- " + input);
            if ("moderator".equals(member.getKey())) {
                moderatorMember = member;
                moderatorInput = input;
                return "共识：以A方案为准";
            }
            return "观点" + member.getKey();
        });

        TeamRunResult result = runner.run(team, "任务X");

        Assert.assertNull(result.getError());
        // 成员依次出意见，主持人最后收两份 + 原始任务
        Assert.assertEquals(3, result.getSteps().size());
        Assert.assertEquals("观点a", result.getSteps().get(0).getOutput());
        Assert.assertEquals("观点b", result.getSteps().get(1).getOutput());
        Assert.assertNotNull(moderatorInput);
        Assert.assertTrue("主持人应收到原始任务", moderatorInput.contains("任务X"));
        Assert.assertTrue("主持人应收到成员a的意见", moderatorInput.contains("观点a"));
        Assert.assertTrue("主持人应收到成员b的意见", moderatorInput.contains("观点b"));
        // 自定义主持提示词透传到合成成员
        Assert.assertNotNull(moderatorMember);
        Assert.assertEquals("你是资深评审，请裁决", moderatorMember.getPrompt());
        Assert.assertEquals(TeamRunner.ROLE_MODERATOR, result.getSteps().get(2).getRole());
        Assert.assertEquals("共识：以A方案为准", result.getFinalOutput());
        Assert.assertEquals(Arrays.asList("a <- 任务X", "b <- 任务X", "moderator <- " + moderatorInput), calls);
    }

    /**
     * 辩论：定义未配 moderator 提示词时用内置兜底提示词（不因缺配而失败），裁决即结论
     */
    @Test
    public void debateFallsBackToBuiltinModeratorPrompt() {
        TeamDefinition team = team(TeamMode.DEBATE, member("a", "正方"));
        final TeamMember[] moderatorRef = new TeamMember[1];
        TeamRunResult result = new TeamRunner((member, input) -> {
            if ("moderator".equals(member.getKey())) {
                moderatorRef[0] = member;
                return "裁决";
            }
            return "意见";
        }).run(team, "任务");

        Assert.assertNull(result.getError());
        Assert.assertNotNull("主持人应参与执行", moderatorRef[0]);
        Assert.assertTrue("未配 moderator 时应使用内置兜底提示词",
                moderatorRef[0].getPrompt().contains("主持人"));
        Assert.assertEquals(2, result.getSteps().size());
        Assert.assertEquals(TeamRunner.ROLE_MODERATOR, result.getSteps().get(1).getRole());
        Assert.assertEquals("裁决", result.getFinalOutput());
    }

    /**
     * 路由：主控输出含 @[b] @[b]（重复）与 @[ghost]（未识别）——b 只派发一次、ghost 跳过，
     * 派发成员收到原始输入，结论=主控说明+派发结果汇集
     */
    @Test
    public void routerDispatchesReferencedMembersOnceWithOriginalInput() {
        TeamDefinition team = team(TeamMode.ROUTER, member("a", "主控"), member("b", "检索"), member("c", "写作"));
        TeamRunner runner = new TeamRunner((member, input) -> {
            calls.add(member.getKey() + " <- " + input);
            if ("a".equals(member.getKey())) {
                return "分工：@[b] 然后 @[b]，其次 @[c]，外部 @[ghost]";
            }
            return "交付" + member.getKey();
        });

        TeamRunResult result = runner.run(team, "需求Y");

        Assert.assertNull(result.getError());
        // 主控 + b（去重一次）+ c = 3 步；ghost 未派发
        Assert.assertEquals(3, result.getSteps().size());
        Assert.assertEquals(TeamRunner.ROLE_ROUTER, result.getSteps().get(0).getRole());
        Assert.assertEquals(Arrays.asList(
                "a <- 需求Y",
                "b <- 需求Y",
                "c <- 需求Y"), calls);
        String finalOutput = result.getFinalOutput();
        Assert.assertTrue("结论应含主控路由说明", finalOutput.contains("分工：@[b] 然后 @[b]，其次 @[c]，外部 @[ghost]"));
        Assert.assertTrue("结论应汇集b产出", finalOutput.contains("【检索】\n交付b"));
        Assert.assertTrue("结论应汇集c产出", finalOutput.contains("【写作】\n交付c"));
    }

    /**
     * fail-fast 保留现场：流水线第2棒失败——已执行步与失败步随结果返回，结论置空、error 回填
     */
    @Test
    public void pipelineFailureKeepsExecutedStepsAndReportsError() {
        TeamDefinition team = team(TeamMode.PIPELINE, member("a", "甲"), member("b", "乙"));
        TeamRunResult result = new TeamRunner((member, input) -> {
            if ("b".equals(member.getKey())) {
                throw new IllegalStateException("下游模型超时");
            }
            return "首棒产出";
        }).run(team, "原文");

        Assert.assertNotNull(result.getError());
        Assert.assertTrue(result.getError().contains("下游模型超时"));
        Assert.assertNull(result.getFinalOutput());
        Assert.assertEquals(2, result.getSteps().size());
        Assert.assertNull(result.getSteps().get(1).getOutput());
        Assert.assertEquals("下游模型超时", result.getSteps().get(1).getError());
        Assert.assertTrue(result.getCostMs() >= 0);
    }

    /**
     * 防线：无成员/无模式/空执行器拒绝执行
     */
    @Test
    public void rejectsInvalidDefinitions() {
        TeamRunner runner = new TeamRunner(echoExecutor());
        try {
            runner.run(team(TeamMode.PIPELINE), "原文");
            Assert.fail("空成员团队应拒绝执行");
        } catch (IllegalArgumentException e) {
            Assert.assertTrue(e.getMessage().contains("至少需要一名成员"));
        }
        TeamDefinition noMode = team(TeamMode.PIPELINE, member("a", "甲"));
        noMode.setMode(null);
        try {
            runner.run(noMode, "原文");
            Assert.fail("缺模式应拒绝执行");
        } catch (IllegalArgumentException e) {
            Assert.assertTrue(e.getMessage().contains("编排模式不能为空"));
        }
        try {
            new TeamRunner(null);
            Assert.fail("缺执行器应拒绝构造");
        } catch (IllegalArgumentException e) {
            Assert.assertTrue(e.getMessage().contains("TeamMemberExecutor 不能为空"));
        }
    }
}
