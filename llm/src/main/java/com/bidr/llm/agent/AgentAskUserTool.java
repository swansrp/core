package com.bidr.llm.agent;

import com.bidr.llm.agent.session.AgentQuestion;
import com.bidr.llm.agent.session.AgentSessionContext;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.List;

/**
 * Title: AgentAskUserTool
 * Description: 通用「问用户做选择」工具（框架级，业务零绑定）：LLM 遇真歧义调 ask_user 提问，
 * 工具线程阻塞等待用户作答，答案作为工具结果回填继续循环。双载体适配：
 * 会话链（自主资产生成等）传 AgentSessionContext——问题落状态快照由前端渲染问题卡片，
 * 用户经作答端点选择/输入/跳过（1s 醒查，与暂停机制同构），超时（默认 5 分钟，业务侧可注入）不失败引导
 * LLM 按合理默认继续，停止请求随时打断等待；票据链（维护问数等）传 Awaiter——问题交业务
 * 载体等作答（挂票据随轮询下发/唤醒均由业务侧实现）。候选项解析与校验统一在本框架层，
 * 业务差异（何时算真歧义、回填文案口径）经 Awaiter 返回文本与追加提示词注入。
 * 无交互通道的链路注册时不传本工具
 *
 * @author Sharp
 * @since 2026/8/21
 */
@Slf4j
public class AgentAskUserTool {

    /** 等待作答超时默认值（毫秒）：超时引导 LLM 按合理默认继续，不无限占用全局任务锁；业务侧经构造参数覆盖 */
    private static final long DEFAULT_TIMEOUT_MILLIS = 300_000;

    /** 候选项条数上限（防 LLM 罗列刷屏） */
    private static final int OPTIONS_MAX = 6;

    private static final ObjectMapper OM = new ObjectMapper();

    /** 阻塞等待回调（票据等非会话载体）：问题+候选项交业务载体等用户作答，返回给 LLM 的完整回填文本 */
    public interface Awaiter {
        String await(String question, List<String> options);
    }

    /** 会话载体（与 awaiter 二选一；可空=无交互通道） */
    private final AgentSessionContext sessionCtx;

    /** 通用载体（可空） */
    private final Awaiter awaiter;

    /** 等待作答超时（毫秒），会话链生效；票据链超时由 Awaiter 业务侧自管 */
    private final long timeoutMillis;

    public AgentAskUserTool(AgentSessionContext sessionCtx) {
        this(sessionCtx, DEFAULT_TIMEOUT_MILLIS);
    }

    /** 会话链构造：超时上限由业务侧注入（配置化），非正值回落默认 5 分钟 */
    public AgentAskUserTool(AgentSessionContext sessionCtx, long timeoutMillis) {
        this.sessionCtx = sessionCtx;
        this.awaiter = null;
        this.timeoutMillis = timeoutMillis > 0 ? timeoutMillis : DEFAULT_TIMEOUT_MILLIS;
    }

    public AgentAskUserTool(Awaiter awaiter) {
        this.sessionCtx = null;
        this.awaiter = awaiter;
        this.timeoutMillis = DEFAULT_TIMEOUT_MILLIS;
    }

    @Tool("向用户提出一个需要业务决策的问题并等待用户选择。当你遇到无法从已获取信息中确定的"
            + "业务判断（口径有多种合理解释、术语含义拿不准、关键取舍不明）时，必须调用"
            + "本工具交给用户决策，禁止自行猜测。一次只问一个决策面：同一决策面的多个疑点"
            + "（如多列单位矛盾、同类术语歧义）合并为一道逐列列举的问题一次问清，不要拆成多次提问；"
            + "候选项须来自真实数据/工具返回；"
            + "提问前先自查已有信息，能确定的不问。工具将阻塞等待用户作答，拿到答复后按其口径继续")
    public String askUser(@P("问题（一句话，含必要背景与每个选项的影响）") String question,
                          @P("候选项 JSON 数组（2-6 个，基于真实数据；确无候选传 []）") String optionsJson) {
        if (sessionCtx == null && awaiter == null) {
            return "{\"error\":\"无交互通道，无法向用户提问。按合理默认继续，并在产出中注明该口径未经用户确认\"}";
        }
        if (question == null || question.trim().isEmpty()) {
            return "拒绝：问题不能为空，请补充问题背景后重提";
        }
        List<String> options = parseOptions(optionsJson);
        if (options == null) {
            return "拒绝：候选项不是合法 JSON 数组（请传如 [\"选项A\",\"选项B\"]），修正后重提";
        }
        if (options.size() > OPTIONS_MAX) {
            options = new ArrayList<>(options.subList(0, OPTIONS_MAX));
        }
        if (awaiter != null) {
            return awaiter.await(question.trim(), options);
        }
        log.info("[LLM工具] 会话 '{}' ask_user: {}", sessionCtx.getSessionId(), question);
        sessionCtx.log("工具 ask_user：向用户提问等待选择——" + question.trim());
        AgentQuestion item = sessionCtx.askQuestion(question.trim(), options);
        String result = sessionCtx.awaitAnswer(item, timeoutMillis);
        if (AgentSessionContext.AWAIT_STOPPED.equals(result)) {
            return "{\"error\":\"任务已被用户停止\"}";
        }
        if (AgentSessionContext.AWAIT_EXPIRED.equals(result)) {
            return "用户 " + Math.max(1, timeoutMillis / 60_000) + " 分钟内未作答。按你的合理判断自行决策并继续，"
                    + "该自决口径须登记为待确认口径（链路提供登记工具时），并在 finish 总结中注明";
        }
        if (result == null) {
            // 用户跳过：状态已置 skipped，交由 LLM 自行决策（同属自决口径，须登记待确认）
            return "用户将该问题交由你自行决定。选择合理默认并继续，把决策依据写入对应资产的 description/notes，"
                    + "并将该自决口径登记为待确认口径（链路提供登记工具时）";
        }
        sessionCtx.log("用户作答 #" + item.getId() + "：" + result);
        return "用户选择：" + result + "\n（该答复即口径依据，须落实到对应资产的 description/notes）";
    }

    /** 候选项解析：合法数组返回条目清单（空数组合法=纯开放题），非法返回 null */
    private static List<String> parseOptions(String optionsJson) {
        List<String> options = new ArrayList<>();
        try {
            JsonNode arr = OM.readTree(optionsJson == null || optionsJson.trim().isEmpty() ? "[]" : optionsJson);
            if (!arr.isArray()) {
                return null;
            }
            for (JsonNode n : arr) {
                String t = n.isTextual() ? n.asText() : n.toString();
                if (t != null && !t.trim().isEmpty()) {
                    options.add(t.trim());
                }
            }
            return options;
        } catch (Exception e) {
            return null;
        }
    }
}
