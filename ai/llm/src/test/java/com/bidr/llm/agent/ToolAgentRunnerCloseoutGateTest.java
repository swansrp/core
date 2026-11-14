package com.bidr.llm.agent;

import com.bidr.llm.agent.ScriptedModel.Step;
import dev.langchain4j.agent.tool.Tool;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.UserMessage;
import org.junit.Assert;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;

import static com.bidr.llm.agent.ScriptedModel.Step.done;
import static com.bidr.llm.agent.ScriptedModel.Step.tool;

/**
 * Title: ToolAgentRunnerCloseoutGateTest
 * Description: 触顶收口保留工具面（closeoutKeepTools）＋续期闸（askBeforeCloseout）端到端测试
 * （2026-11-14 真机实锤驱动：监理日志素材缺口会话 12 轮触顶，旧收口剥光工具 ⇒ save/finish 双缺、
 * 产出只留会话文本不落库；用户拍板硬停止前先问人）。ScriptedModel 假模型零外部依赖，锁死：
 * ①保留链收口轮带 save/finish 工具面＋「先收尾再直出」指令；②未登记链逐字符旧口径（零行为变化）；
 * ③收口轮上限到顶强制直出；④续期闸同意 → 抬升上限继续跑完；⑤拒绝/⑥超时文案 → 回落收口；
 * ⑦无 askUser 工具时闸自动跳过
 *
 * @author Sharp
 * @since 2026/11/14
 */
public class ToolAgentRunnerCloseoutGateTest {

    /** 探针工具：explore（探索类，永不进收口面）＋save_supervision_log/finish（收尾对，落库计数透出） */
    public static class CloseoutProbeTools {
        int saves;

        @Tool(name = "save_supervision_log", value = "落库")
        public String saveSupervisionLog(String logDate, String summary) {
            saves++;
            return "已落库（id=1，logDate=" + logDate + "）。确认无误后调用 finish 收口";
        }

        @Tool(name = "finish", value = "收口")
        public String finish(String summary) {
            return "已确认收口。任务总结已记录，请向用户做最终简报（不再调用任何工具）";
        }

        @Tool(name = "explore", value = "探索")
        public String explore(String query) {
            return "探索结果：" + query;
        }
    }

    /** 监听器：记录工具调用事件（续期闸合成请求可断言）；finish 结果置终止标志（shouldTerminate 链形态） */
    private static final class TermListener implements AgentLoopListener {
        final List<String> toolCalls = new ArrayList<>();
        volatile boolean finished;

        @Override
        public void log(String line) {
        }

        @Override
        public boolean shouldStop() {
            return false;
        }

        @Override
        public void onToolCall(String toolCallId, String toolName, String argumentsJson) {
            toolCalls.add(toolName + "#" + toolCallId);
        }

        @Override
        public void onToolResult(String toolCallId, String toolName, String resultText) {
            if (resultText != null && resultText.contains("已确认收口")) {
                finished = true;
            }
        }

        @Override
        public boolean shouldTerminate() {
            return finished;
        }

        @Override
        public String terminalText() {
            return "终局：任务完成总结";
        }
    }

    /** 保留链基础配置：maxRounds=1 即触顶（脚本一步探索烧完预算），保留面＝save+finish */
    private static AgentLoopOptions keepOpts(int closeoutMaxRounds) {
        AgentLoopOptions opt = new AgentLoopOptions(1, 30);
        opt.setCloseoutKeepTools(new HashSet<>(Arrays.asList("save_supervision_log", "finish")));
        opt.setCloseoutMaxRounds(closeoutMaxRounds);
        return opt;
    }

    /** 入窗消息里最后一条 UserMessage 文本（收口指令/续期通知断言取数） */
    private static String lastUserText(List<ChatMessage> messages) {
        for (int i = messages.size() - 1; i >= 0; i--) {
            if (messages.get(i) instanceof UserMessage) {
                return ((UserMessage) messages.get(i)).singleText();
            }
        }
        return "";
    }

    private static List<String> specNames(List<dev.langchain4j.agent.tool.ToolSpecification> specs) {
        List<String> names = new ArrayList<>();
        for (dev.langchain4j.agent.tool.ToolSpecification s : specs) {
            names.add(s.name());
        }
        return names;
    }

    /** ①保留链：收口轮带 save/finish 工具面＋先收尾指令；save→finish→终止短路返回终文本 */
    @Test
    public void 收口保留面含save与finish且终止短路() {
        TermListener listener = new TermListener();
        ScriptedModel model = new ScriptedModel(
                tool("call-1", "explore", "{\"query\":\"素材\"}"),
                tool("call-2", "save_supervision_log", "{\"logDate\":\"2026-11-14\",\"summary\":\"正文\"}"),
                tool("call-3", "finish", "{\"summary\":\"已落库并总结\"}"));
        AgentLoopResult r = new ToolAgentRunner().run(model, "系统", "任务",
                Arrays.<Object>asList(new CloseoutProbeTools()), keepOpts(3), listener);
        Assert.assertEquals("终止信号命中应返回终文本", "终局：任务完成总结", r.getText());
        // 收口第一次 generate 的工具面：只有保留集（explore 必须剥掉；askUser 未注册自然缺席）
        List<String> closing = specNames(model.views.get(1).specs);
        Assert.assertTrue("save_supervision_log 应在收口面", closing.contains("save_supervision_log"));
        Assert.assertTrue("finish 应在收口面", closing.contains("finish"));
        Assert.assertFalse("探索工具严禁进收口面", closing.contains("explore"));
        // 收口指令是新口径（先收尾再直出），不再是「禁止再调用任何工具」
        String instruction = lastUserText(model.views.get(1).messages);
        Assert.assertTrue(instruction.contains("收口保留工具"));
        Assert.assertTrue(instruction.contains("先存后收"));
        Assert.assertFalse(instruction.contains("禁止再调用任何工具"));
    }

    /** ②未登记链（默认 options）：收口面只有 askUser、指令逐字符旧口径——零行为变化 */
    @Test
    public void 未登记链收口面与指令保持旧口径() {
        TermListener listener = new TermListener();
        ScriptedModel model = new ScriptedModel(
                tool("call-1", "explore", "{\"query\":\"素材\"}"),
                done("直接结论"));
        new ToolAgentRunner().run(model, "系统", "任务",
                Arrays.<Object>asList(new CloseoutProbeTools(),
                        new ToolAgentRunnerOffloadTest.FakeAskUserTool("口径答复")),
                new AgentLoopOptions(1, 30), listener);
        List<String> closing = specNames(model.views.get(1).specs);
        Assert.assertEquals("未登记链收口面＝仅 askUser（旧口径）",
                Arrays.asList("askUser"), closing);
        String instruction = lastUserText(model.views.get(1).messages);
        Assert.assertTrue("旧指令文本保持", instruction.contains("禁止再调用任何工具"));
        Assert.assertFalse("新口径措辞不得出现", instruction.contains("收口保留工具"));
    }

    /** ③收口轮上限：closeoutMaxRounds=2，save 两批后强制无工具面直出 */
    @Test
    public void 收口轮上限到顶强制直出() {
        TermListener listener = new TermListener();
        CloseoutProbeTools probe = new CloseoutProbeTools();
        ScriptedModel model = new ScriptedModel(
                tool("call-1", "explore", "{\"query\":\"素材\"}"),
                tool("call-2", "save_supervision_log", "{\"logDate\":\"2026-11-14\",\"summary\":\"一稿\"}"),
                tool("call-3", "save_supervision_log", "{\"logDate\":\"2026-11-14\",\"summary\":\"二稿\"}"),
                done("收口结论X"));
        AgentLoopResult r = new ToolAgentRunner().run(model, "系统", "任务",
                Arrays.<Object>asList(probe), keepOpts(2), listener);
        Assert.assertEquals("上限 2＝save 两轮后强制无工具直出", "收口结论X", r.getText());
        Assert.assertEquals("落库恰好两轮", 2, probe.saves);
        Assert.assertEquals("收口 2 轮 generate＋末轮直出＝主循环 1 轮＋收口 3 次", 4, model.views.size());
        Assert.assertTrue("最后一次 generate 无工具面", model.views.get(3).specs.isEmpty());
    }

    /** ④续期闸：用户选继续 → roundLimit 抬升，任务继续跑完（save→finish→终止短路） */
    @Test
    public void 续期闸同意后抬升上限继续跑完() {
        TermListener listener = new TermListener();
        ScriptedModel model = new ScriptedModel(
                tool("call-1", "explore", "{\"query\":\"素材\"}"),
                tool("call-2", "save_supervision_log", "{\"logDate\":\"2026-11-14\",\"summary\":\"正文\"}"),
                tool("call-3", "finish", "{\"summary\":\"已落库并总结\"}"));
        AgentLoopOptions opt = keepOpts(3);
        opt.setAskBeforeCloseout(true);
        opt.setRoundExtension(2);
        AgentLoopResult r = new ToolAgentRunner().run(model, "系统", "任务",
                Arrays.<Object>asList(new CloseoutProbeTools(),
                        new ToolAgentRunnerOffloadTest.FakeAskUserTool(
                                "用户选择：继续（追加 2 轮）\n（该答复即口径依据，须落实到对应资产的 description/notes）")),
                opt, listener);
        Assert.assertEquals("续期后任务应跑完并走终止短路", "终局：任务完成总结", r.getText());
        Assert.assertTrue("续期询问应作为合成 askUser 事件上报（前端决策卡复用同通道）",
                listener.toolCalls.contains("askUser#round-extend-ask-1"));
        boolean renewalNoted = false;
        for (ScriptedModel.RoundView v : model.views) {
            for (ChatMessage m : v.messages) {
                if (m instanceof UserMessage && ((UserMessage) m).singleText().contains("【预算续期】")) {
                    renewalNoted = true;
                }
            }
        }
        Assert.assertTrue("续期通知应注入模型上下文", renewalNoted);
    }

    /** ⑤续期闸：用户选收口 → 回落收口路径（保留面在场），无续期通知 */
    @Test
    public void 续期闸拒绝回落收口() {
        TermListener listener = new TermListener();
        ScriptedModel model = new ScriptedModel(
                tool("call-1", "explore", "{\"query\":\"素材\"}"),
                done("已按用户要求收口"));
        AgentLoopOptions opt = keepOpts(3);
        opt.setAskBeforeCloseout(true);
        AgentLoopResult r = new ToolAgentRunner().run(model, "系统", "任务",
                Arrays.<Object>asList(new CloseoutProbeTools(),
                        new ToolAgentRunnerOffloadTest.FakeAskUserTool(
                                "用户选择：收口（结束任务，按编排纪律完成收尾）\n（该答复即口径依据）")),
                opt, listener);
        Assert.assertEquals("拒绝续期＝走收口直出", "已按用户要求收口", r.getText());
        Assert.assertFalse("拒绝路径不得注入续期通知", lastUserText(model.views.get(1).messages).contains("【预算续期】"));
        Assert.assertTrue("收口保留面仍在（保产出）",
                specNames(model.views.get(1).specs).contains("save_supervision_log"));
    }

    /** ⑥续期闸：超时固定文案（不含继续选项原文）一律视为拒绝——无人值守回落收口 */
    @Test
    public void 续期闸超时文案视为拒绝() {
        TermListener listener = new TermListener();
        ScriptedModel model = new ScriptedModel(
                tool("call-1", "explore", "{\"query\":\"素材\"}"),
                done("超时自决收口"));
        AgentLoopOptions opt = keepOpts(3);
        opt.setAskBeforeCloseout(true);
        AgentLoopResult r = new ToolAgentRunner().run(model, "系统", "任务",
                Arrays.<Object>asList(new CloseoutProbeTools(),
                        new ToolAgentRunnerOffloadTest.FakeAskUserTool(
                                "用户 5 分钟内未作答。按你的合理判断自行决策并继续，"
                                        + "该自决口径须登记为待确认口径（链路提供登记工具时），并在 finish 总结中注明")),
                opt, listener);
        Assert.assertEquals("超时＝拒绝续期＝收口", "超时自决收口", r.getText());
        Assert.assertEquals("续期只问一次（额度默认 1）", 1,
                (int) listener.toolCalls.stream().filter(t -> t.startsWith("askUser#round-extend-ask")).count());
    }

    /** ⑦闸自动跳过：askUser 未注册时开闸也直接收口（无合成事件） */
    @Test
    public void 无askUser工具时续期闸自动跳过() {
        TermListener listener = new TermListener();
        ScriptedModel model = new ScriptedModel(
                tool("call-1", "explore", "{\"query\":\"素材\"}"),
                done("无闸收口"));
        AgentLoopOptions opt = keepOpts(3);
        opt.setAskBeforeCloseout(true);
        AgentLoopResult r = new ToolAgentRunner().run(model, "系统", "任务",
                Arrays.<Object>asList(new CloseoutProbeTools()), opt, listener);
        Assert.assertEquals("无 askUser＝闸跳过＝直接收口", "无闸收口", r.getText());
        Assert.assertTrue("不得有合成询问事件",
                listener.toolCalls.stream().noneMatch(t -> t.startsWith("askUser#round-extend-ask")));
    }
}
