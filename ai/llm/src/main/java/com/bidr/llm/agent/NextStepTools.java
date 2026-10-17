package com.bidr.llm.agent;

import com.bidr.llm.agent.session.AgentSessionContext;
import com.bidr.llm.agent.session.PlanBoard;
import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;

import java.util.ArrayList;
import java.util.List;

/**
 * Title: NextStepTools
 * Description: 通用「下一步候选」agent 工具（框架级，业务零绑定；2026-10-09 墨枢 writer 首落，
 * 2026-10-18 按用户指令下沉 core）：LLM 在本轮答复收口前给用户 1-3 条可点快捷候选，
 * 用户点一条＝把那句指令原文发回模型继续。
 *
 * <p><b>为什么由模型出而不是前端拼</b>：前端只能按"本轮有没有产出"这类状态凑通用动作，
 * 凑不出「贴着他刚问的事」的下一步——而点了要真能执行，所以候选必须是<b>发给模型的
 * 指令原文</b>，只有模型知道此刻还有什么可做。</p>
 *
 * <p><b>候选走事件通道、不进会话状态</b>：{@code emit} 一条 {@link #EVENT_NEXT_STEPS} 事件，
 * 前端增量轮询与历史回看（{@code events(sessionId, 0)} 重放）读同一条，零新表零额外端点。
 * 模型不调这个工具就没有候选——前端宁可不显示，也不拿通用动作冒充 AI 建议。
 * 与 {@link AgentAskUserTool} 同为会话态工具：构造时传 {@link AgentSessionContext}，
 * 不挂会话的链路不注册。</p>
 *
 * @author sharp
 * @since 2026/10/18
 */
public final class NextStepTools {

    /** 事件类型（对话前端按它取候选渲染；业务自定义事件类型，框架不解释其语义） */
    public static final String EVENT_NEXT_STEPS = "next_steps";

    /** 条数上限（1-3 条：再多就成了菜单，不是快捷） */
    static final int MAX_STEPS = 3;

    /** 单条字数上限：候选要一行放得下，长句是任务描述不是快捷 */
    static final int MAX_LEN = 40;

    private final AgentSessionContext ctx;

    public NextStepTools(AgentSessionContext ctx) {
        this.ctx = ctx;
    }

    @Tool(value = "本轮答复收口前调用一次，给用户 1-3 条「下一步」候选（前端显示在答复下方，"
            + "点一条就把那句话作为下一条指令发给你）。每条必须是：①一句指令原文（用户口吻、动词开头、"
            + "不超过 40 字），②你收到后真能直接执行的动作（生成/修改/补一节/再查某处这类），"
            + "③贴着本轮内容，不放通用套话。⚠️ 提交/发布/入库这类是用户在界面上的操作，"
            + "不要写成候选（你收不到、也替不了）。本轮已经把话说完、没有可继续做的事时不要调用。",
            name = "suggest_next_steps")
    public String suggestNextSteps(@P("候选指令原文的 JSON 字符串数组，1-3 条，如 "
            + "[\"把这份模板落成一份草稿文档\",\"按监理日志口径调整栏目\"]") String stepsJson) {
        List<String> parsed = PlanBoard.parseItems(stepsJson);
        List<String> steps = new ArrayList<>();
        if (parsed != null) {
            for (String item : parsed) {
                if (steps.size() >= MAX_STEPS) {
                    break;
                }
                steps.add(item.length() > MAX_LEN ? item.substring(0, MAX_LEN) : item);
            }
        }
        if (steps.isEmpty()) {
            return "拒绝：候选为空或不是合法 JSON 字符串数组——按契约给 1-3 条指令原文，或本轮不调用本工具。";
        }
        ctx.emit(EVENT_NEXT_STEPS, steps);
        return "已把 " + steps.size() + " 条下一步候选显示给用户（用户点哪条，下一条指令就是那句原文）。";
    }
}
