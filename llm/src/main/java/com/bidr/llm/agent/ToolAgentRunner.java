package com.bidr.llm.agent;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.langchain4j.agent.tool.Tool;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.agent.tool.ToolSpecifications;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.ToolExecutionResultMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.output.Response;
import dev.langchain4j.model.output.TokenUsage;
import lombok.extern.slf4j.Slf4j;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * Title: ToolAgentRunner
 * Description: 通用自管 LLM 工具循环引擎（langchain4j function calling）：
 * 1) 自管轮次循环，突破 AiServices 0.33 内置「连续 10 轮工具调用」硬上限（上限见 AgentLoopOptions）；
 * 2) 上下文滑动窗口防多轮探索后膨胀（切点对齐非工具结果消息，避免拆散调用↔结果配对）；
 * 3) 触顶后追加收口指令强制产出结论而非报错；
 * 4) 模型委托/端点不支持工具或探索超限时回落直连一次产出；
 * 5) listener 停止信号或线程中断时收口返回 STOPPED（调用方走停止收口，不解析产出）。
 * 工具对象随会话传入（任务作用域生命周期，注册即传入），引擎不做全局注册表。
 * 仅依赖 langchain4j 与 slf4j，不含业务耦合
 *
 * @author Sharp
 * @since 2026/8/20
 */
@Slf4j
public class ToolAgentRunner {

    /** 工具参数 JSON 解析器（自实现：0.33 的 ToolExecutionRequestUtil 为包私有不可复用） */
    private static final ObjectMapper ARG_OM = new ObjectMapper();

    /** 工具循环上下文滑动窗口固定头部条数：system + 首轮 user（不可裁剪）；
     *  无 system 时头部仅首轮 user（1 条，见 trimToolMemory 动态计算） */
    private static final int MEMORY_HEAD = 2;

    /**
     * 运行一个工具循环会话
     *
     * @param model        业务模型（引擎内部包日志代理，每轮请求/响应推 listener）
     * @param systemPrompt 系统提示（仅框架级通用纪律；空白表示无 system 槽，业务内容应拼入 userPrompt）
     * @param userPrompt   用户提示（业务角色/硬约束 + 任务目标 + 上下文）
     * @param toolObjects  工具对象清单（@Tool 注解方法，任务作用域实例）
     * @param options      轮次/窗口参数（null 走默认 30/60）
     * @param listener     业务钩子（null 视为 NONE：无日志、永不停止）
     * @return 会话结果（见 AgentLoopResult）
     */
    public AgentLoopResult run(ChatLanguageModel model, String systemPrompt, String userPrompt,
                               List<Object> toolObjects, AgentLoopOptions options, AgentLoopListener listener) {
        AgentLoopListener sink = listener == null ? AgentLoopListener.NONE : listener;
        AgentLoopOptions opt = options == null ? new AgentLoopOptions() : options;
        // 停止检查透传至 LLM 等待层：等待期间轮询停止键，跨实例停止键也能快速收口（不只靠线程中断）
        ChatLanguageModel logged = new LoggingModel(model, sink::log, sink::shouldStop);
        // ---- L1 上下文预算治理：阈值三级取值（option 正值 > sys_config > 默认全关，I9 关闭态零行为变化）----
        int offloadChars = AgentContextBudget.offloadChars(opt.getToolResultOffloadChars());
        int tokenBudget = AgentContextBudget.tokenBudget(opt.getContextTokenBudget());
        int previewChars = AgentContextBudget.previewChars();
        // I14：全部计数收敛在 run() 局部 RunStats 实例，runner 自身不新增任何字段（Spring 单例共享）
        RunStats stats = new RunStats(sink);
        stats.recallAvailable = sink.supportsToolResultRecall();
        // I5：无回捞通道不卸载；I6：钉住工具 ∪ askUser ∪ recallToolResult 永不卸载
        boolean offloadOn = offloadChars > 0 && stats.recallAvailable;
        // A9/I15 回捞可用性唯一同源判据：卸载开启（指针句柄）或 token 预算开启（digest 行内句柄）任一为真；
        // 回捞工具注册与 digest 写句柄共此一刀，防"有句柄没工具/有工具没句柄"半开态。
        // I9：offloadChars=0 && tokenBudget=0（默认态）→ false：specs 不增工具、digest 不写句柄，关闭态逐字符不变
        boolean recallUsable = stats.recallAvailable && (offloadOn || tokenBudget > 0);
        stats.recallUsable = recallUsable;
        Set<String> neverOffload = new HashSet<>(
                opt.getPinnedTools() == null ? Collections.emptySet() : opt.getPinnedTools());
        neverOffload.add("askUser");
        neverOffload.add(AgentToolRecall.TOOL_NAME);
        List<Object> effectiveTools = new ArrayList<>(toolObjects);
        if (recallUsable) {
            // 回捞工具仅在 recallUsable 时注册（A9 扩面：入场卸载指针与 digest 行内句柄两条回捞路径同源；
            // 默认零副作用，I9：无回捞通道/关闭态 specs 与改造前逐条一致）；
            // 不登记 roundExemptTools——回捞实打实占轮次，轮次上限是回捞爆炸的最终兜底
            stats.recallTool = new AgentToolRecall(sink, AgentContextBudget.recallMaxPerRun(),
                    AgentContextBudget.recallMaxChars(), stats.offloadedHandles);
            effectiveTools.add(stats.recallTool);
        }
        // specs 采集与全部 executeTool 调用点一律走 effectiveTools（否则回捞工具反射找不到 holder）
        List<ToolSpecification> specs = new ArrayList<>();
        for (Object tools : effectiveTools) {
            specs.addAll(ToolSpecifications.toolSpecificationsFrom(tools));
        }
        int fixedOverheadTokens = ContextTokenEstimator.estimateSpecs(specs);
        List<ChatMessage> messages = new ArrayList<>();
        // system 槽可空：无框架级纪律可发时不加 SystemMessage（业务内容由调用方拼入 user 提示）
        if (systemPrompt != null && !systemPrompt.trim().isEmpty()) {
            messages.add(SystemMessage.from(systemPrompt));
        }
        messages.add(UserMessage.from(userPrompt));
        Map<String, String> toolCache = new HashMap<>();
        try {
            long startMs = System.currentTimeMillis();
            boolean budgetHit = false;
            // 预算豁免工具累计耗时（ask_user 等交互等待类）：从预算检查中扣除，等待用户归来不立即触发收口
            long exemptMs = 0L;
            for (int round = 1; round <= opt.getMaxRounds(); round++) {
                // 总时间预算检查：超预算不再发起新一轮，直接走收口路径（杜绝长跑无结论）
                if (opt.getBudgetSeconds() > 0
                        && System.currentTimeMillis() - startMs - exemptMs >= opt.getBudgetSeconds() * 1000L) {
                    budgetHit = true;
                    break;
                }
                if (sink.shouldStop() || Thread.currentThread().isInterrupted()) {
                    sink.log("工具循环第 " + round + " 轮前收到停止信号，会话收口");
                    log.info("工具循环第 {} 轮前收到停止信号，收口返回 STOPPED", round);
                    return AgentLoopResult.stopped(round - 1);
                }
                // 暂停检查点：阻塞至恢复或停止；恢复后再查一次停止（暂停等待期间收到停止请求则立即收口）
                String resumeGuidance = sink.awaitResumeIfPaused();
                if (sink.shouldStop() || Thread.currentThread().isInterrupted()) {
                    sink.log("暂停等待期间收到停止信号，会话收口");
                    return AgentLoopResult.stopped(round - 1);
                }
                if (resumeGuidance != null && !resumeGuidance.trim().isEmpty()) {
                    sink.log("恢复指导语已注入：" + resumeGuidance);
                    messages.add(UserMessage.from("【用户补充指导】" + resumeGuidance.trim()));
                }
                trimToolMemory(messages, opt, fixedOverheadTokens, tokenBudget, stats);
                logContextMeter(sink, stats, messages, fixedOverheadTokens, tokenBudget, offloadOn);
                Response<AiMessage> resp = logged.generate(messages, specs);
                AiMessage ai = resp.content();
                messages.add(ai);
                if (!ai.hasToolExecutionRequests()) {
                    log.info("工具循环第 {} 轮给出结论，会话结束", round);
                    return AgentLoopResult.toolLoop(ai.text(), round);
                }
                // 逐个执行工具并回填结果：异常（参数解析失败/工具内部报错/工具未找到）一律转错误文本
                // 回给 LLM 自纠，绝不让单次工具失败击穿整个会话；调用/返回各推一条过程日志（会话事件流
                // 思考组可见）：参数/结果推全文供前端展开回看（后端转储口径由 sink 侧摘要，存储侧兜底截断）
                // ceremonyOnly 跟踪：本轮是否全部为轮次豁免工具（纯状态仪式，不消耗轮次配额）
                boolean ceremonyOnly = true;
                String breakerAdvice = null;
                for (ToolExecutionRequest req : ai.toolExecutionRequests()) {
                    if (!opt.getRoundExemptTools().contains(req.name())) {
                        ceremonyOnly = false;
                    }
                    String cacheKey = req.name() + "\u0000" + (req.arguments() == null ? "{}" : req.arguments());
                    String cached = opt.getCachedTools().contains(req.name()) ? toolCache.get(cacheKey) : null;
                    if (cached != null) {
                                            sink.log("工具 " + req.name() + " 同参缓存命中，直接返回：" + fullText(cached));
                        sink.onToolCall(req.id(), req.name(), req.arguments());
                        sink.onToolResult(req.id(), req.name(), cached);
                        // 缓存存原文全文（执行结果真源）；命中路径照样施加卸载并配本次新 id 的指针
                        // （呈现形态分层，事件流全文齐全 → 新句柄可回捞，死循环论证见计划岔路 3）
                        appendToolResultMessage(messages, req, cached, neverOffload,
                                offloadChars, previewChars, true, stats);
                        continue;
                    }
                    sink.log("调用工具 " + req.name() + "(" + fullText(req.arguments()) + ")");
                    sink.onToolCall(req.id(), req.name(), req.arguments());
                    long toolStart = System.currentTimeMillis();
                    String toolResult = executeTool(req, effectiveTools);
                    if (opt.getBudgetExemptTools().contains(req.name())) {
                        long waited = System.currentTimeMillis() - toolStart;
                        exemptMs += waited;
                        sink.log("工具 " + req.name() + " 等待耗时 " + (waited / 1000) + "s，不计入会话时间预算");
                    }
                    if (opt.getCachedTools().contains(req.name()) && !toolResult.startsWith("【工具调用失败】")) {
                        toolCache.put(cacheKey, toolResult);
                    }
                    // I4：事件流先收全文（熔断器同样吃原文，防把指针当新失败模式计数）；入场再做卸载
                    sink.log("工具 " + req.name() + " 返回：" + fullText(toolResult));
                    sink.onToolResult(req.id(), req.name(), toolResult);
                    appendToolResultMessage(messages, req, toolResult, neverOffload,
                            offloadChars, previewChars, true, stats);
                    // 失败分类熔断：失败文本按规则集计数，同类累计达阈值时取回拉直方向的指令
                    // （本轮工具循环结束后注入一条用户消息，一次性语义）
                    if (opt.getFailureBreaker() != null && breakerAdvice == null) {
                        breakerAdvice = opt.getFailureBreaker().onToolResult(toolResult);
                    }
                }
                if (breakerAdvice != null) {
                    messages.add(UserMessage.from(breakerAdvice));
                    sink.log("失败模式熔断触发，已注入拉直指令：" + breakerAdvice);
                }
                // 轮次豁免：整轮只调纯仪式工具（如计划待办标记）时不消耗轮次配额（round-- 与
                // 迭代器 round++ 抵消，本轮免费）；时间预算不受豁免影响仍兜底，防无限仪式循环长跑
                if (ceremonyOnly) {
                    round--;
                    sink.log("本轮全部为轮次豁免工具（状态仪式），不消耗轮次配额");
                }
                // 业务终止短路：终结信号（如 finish 工具）已在本轮工具中确认时立即收口，
                // 不再发起下一轮（避免拿着全部上下文再跑一轮只为听一句告别）
                if (sink.shouldTerminate()) {
                    String terminal = sink.terminalText();
                    sink.log("业务终止信号已确认（第 " + round + " 轮），会话短路收口");
                    log.info("工具循环第 {} 轮收到业务终止信号，短路收口", round);
                    return AgentLoopResult.toolLoop(terminal == null ? "" : terminal, round);
                }
            }
            // 触顶/超预算收口：除 askUser 外不再携带工具定义，追加收口指令强制产出结论。
            // askUser 例外通道：链路注册了 askUser 时保留其规格并留例外口子——避免“已判真歧义
            // Must ask 但收口禁工具”被迫瞎猜（模型坚持问则执行一次拿到口径，随后无工具直出）
            sink.log(budgetHit
                    ? "会话总耗时达预算（" + opt.getBudgetSeconds() + "s），收口指令已发出，要求直接输出最终结果"
                    : "工具探索达上限（" + opt.getMaxRounds() + " 轮），收口指令已发出，要求直接输出最终结果");
            messages.add(UserMessage.from("探索预算已用完，禁止再调用任何工具。唯一例外：仍存在影响结论口径"
                    + "且无法自决的真歧义时，可调用 askUser 向用户确认后立即输出最终结论。"
                    + "请基于已有信息立即输出最终结论。"));
            trimToolMemory(messages, opt, fixedOverheadTokens, tokenBudget, stats);
            logContextMeter(sink, stats, messages, fixedOverheadTokens, tokenBudget, offloadOn);
            List<ToolSpecification> closingSpecs = new ArrayList<>();
            for (ToolSpecification s : specs) {
                if ("askUser".equals(s.name())) {
                    closingSpecs.add(s);
                }
            }
            Response<AiMessage> last = closingSpecs.isEmpty()
                    ? logged.generate(messages) : logged.generate(messages, closingSpecs);
            if (last.content().hasToolExecutionRequests()) {
                // 收口例外命中（模型坚持 askUser=真歧义）：执行拿到用户口径后无工具直出
                messages.add(last.content());
                for (ToolExecutionRequest req : last.content().toolExecutionRequests()) {
                    sink.log("收口例外：执行 " + req.name() + " 后无工具直出");
                    sink.onToolCall(req.id(), req.name(), req.arguments());
                    String closingResult = executeTool(req, effectiveTools);
                    sink.onToolResult(req.id(), req.name(), closingResult);
                    // I10 收口路径不卸载：最后一次 generate 之前追加的消息一律原文
                    // （此时省 token 已无意义，丢信息代价最大；allowOffload=false 双保险）
                    appendToolResultMessage(messages, req, closingResult, neverOffload,
                            offloadChars, previewChars, false, stats);
                }
                logContextMeter(sink, stats, messages, fixedOverheadTokens, tokenBudget, offloadOn);
                last = logged.generate(messages);
            }
            log.info("工具循环收口完成（上限 {} 轮 / 预算 {}s，超预算={}）",
                    opt.getMaxRounds(), opt.getBudgetSeconds(), budgetHit);
            return AgentLoopResult.toolLoop(last.content().text(), opt.getMaxRounds());
        } catch (UnsupportedOperationException e) {
            log.warn("模型委托不支持工具调用，回落直连生成: {}", e.getMessage());
            sink.log("模型不支持工具调用，回落直连生成");
            return AgentLoopResult.directFallback(logged.generate(userPrompt));
        } catch (RuntimeException e) {
            if (isInterrupted(e) || Thread.currentThread().isInterrupted()) {
                log.info("工具循环被线程中断（停止请求），收口返回 STOPPED");
                return AgentLoopResult.stopped(0);
            }
            if (isToolUnsupported(e)) {
                log.warn("模型端点不支持 function calling（{}），回落直连生成", e.getMessage());
                sink.log("模型端点不支持 function calling，回落直连生成");
                return AgentLoopResult.directFallback(logged.generate(userPrompt));
            }
            if (isToolLoopExceeded(e)) {
                log.warn("工具探索轮次超限（{}），回落直连生成", e.getMessage());
                sink.log("工具探索轮次超限，回落直连生成（基于已有上下文直接产出）");
                return AgentLoopResult.directFallback(logged.generate(userPrompt));
            }
            throw e;
        }
    }

    /** 日志包装工厂：非循环链路（如逐表小请求直连）复用同口径的逐轮日志与可中断等待；
     *  stopCheck 传 null 时仅响应线程中断（无停止键轮询） */
    public static ChatLanguageModel loggingModel(ChatLanguageModel delegate, Consumer<String> logSink) {
        return new LoggingModel(delegate, logSink, null);
    }

    /** 日志包装工厂（带停止键轮询）：等待 LLM 期间周期性检查停止信号，命中即弃子线程收口 */
    public static ChatLanguageModel loggingModel(ChatLanguageModel delegate, Consumer<String> logSink,
                                                 BooleanSupplier stopCheck) {
        return new LoggingModel(delegate, logSink, stopCheck);
    }

    /** 工具参数/结果全文（事件流原文：前端「展开」可见全部内容；仅设宽裕上限防异常巨型返回击穿存储，
     *  常规防膨胀由下游承担——后端日志/进度记录经 sink 侧摘要，会话事件流由存储层兜底截断） */
    private static String fullText(String s) {
        if (s == null) {
            return "";
        }
        return s.length() <= FULL_TEXT_MAX ? s : s.substring(0, FULL_TEXT_MAX) + "…（内容过长已截断，共 " + s.length() + " 字）";
    }

    /** 事件流单条全文上限（宽裕值：正常工具返回远小于此；仅防 DB 探索类工具异常巨型输出） */
    private static final int FULL_TEXT_MAX = 50000;

    /** 工具参数/结果文本截断（摘要口径：后端日志/进度记录/收口摘要等防膨胀场景） */
    private static String brief(String s) {
        return brief(s, 300);
    }

    private static String brief(String s, int max) {
        if (s == null) {
            return "";
        }
        String one = s.replaceAll("\\s+", " ").trim();
        return one.length() > max ? one.substring(0, max) + "..." : one;
    }

    /** 执行单次工具调用：自反射调用 @Tool 方法，所有异常（参数 JSON 解析失败、工具不存在、
     *  工具内部抛出）一律转错误文本回给 LLM 自纠——不用 0.33 DefaultToolExecutor（其参数解析在
     *  try 外会击穿循环，且工具异常文案仅 t.toString()，NPE 等无 message 时模型无从自纠） */
    private static String executeTool(ToolExecutionRequest req, List<Object> toolObjects) {
        Method method = toolMethodOf(toolObjects, req.name());
        if (method == null) {
            return "【工具调用失败】未找到工具 '" + req.name() + "'，请核对可用工具名后重试";
        }
        Object[] args;
        try {
            args = parseToolArgs(method, req.arguments());
        } catch (Exception e) {
            return "【工具调用失败】工具 '" + req.name() + "' 参数解析失败：" + e.getMessage()
                    + "。请修正参数（合法 JSON 且类型匹配）后重试";
        }
        try {
            Object result = method.invoke(method.getDeclaringClass().cast(holderOf(toolObjects, method)), args);
            return result == null ? "（工具无返回值）" : String.valueOf(result);
        } catch (InvocationTargetException e) {
            return toolErrorText(req.name(), e.getCause() != null ? e.getCause() : e);
        } catch (Exception e) {
            return toolErrorText(req.name(), e);
        }
    }

    /** 解析工具参数 JSON：按方法参数名（@Tool 生成 schema 即参数名）逐个取值并转目标类型；
     *  空/缺省参数传 null，由工具方法自行处理 */
    private static Object[] parseToolArgs(Method method, String argumentsJson) throws Exception {
        Parameter[] params = method.getParameters();
        if (params.length == 0) {
            return new Object[0];
        }
        String json = argumentsJson == null || argumentsJson.trim().isEmpty() ? "{}" : argumentsJson;
        com.fasterxml.jackson.databind.JsonNode node = ARG_OM.readTree(json);
        Object[] args = new Object[params.length];
        for (int i = 0; i < params.length; i++) {
            com.fasterxml.jackson.databind.JsonNode v = node.path(params[i].getName());
            args[i] = v.isMissingNode() || v.isNull() ? null : ARG_OM.convertValue(v, params[i].getType());
        }
        return args;
    }

    /** 工具异常转模型可读错误文本：无 message 的异常（如 NPE）补首个堆栈帧位置 */
    private static String toolErrorText(String toolName, Throwable t) {
        String msg = t.getMessage();
        if (msg == null || msg.trim().isEmpty()) {
            StackTraceElement[] st = t.getStackTrace();
            msg = st != null && st.length > 0 ? "发生在 " + st[0] : "无详细错误信息";
        }
        return "【工具调用失败】" + toolName + " 执行异常：" + t.getClass().getSimpleName() + "：" + msg
                + "。请根据错误信息调整参数后重试一次；仍失败则改用其他途径，不要重复相同的错误调用";
    }

    /** 按工具名定位 @Tool 方法（找不到返回 null，由执行层回错误文本给 LLM 自纠） */
    private static Method toolMethodOf(List<Object> toolObjects, String name) {
        for (Object obj : toolObjects) {
            for (Method m : obj.getClass().getMethods()) {
                Tool tool = m.getAnnotation(Tool.class);
                if (tool == null) {
                    continue;
                }
                String toolName = tool.name().isEmpty() ? m.getName() : tool.name();
                if (name.equals(toolName)) {
                    return m;
                }
            }
        }
        return null;
    }

    /** 定位持有指定 @Tool 方法的工具对象实例（反射 invoke 需实例） */
    private static Object holderOf(List<Object> toolObjects, Method method) {
        for (Object obj : toolObjects) {
            if (obj.getClass() == method.getDeclaringClass()) {
                return obj;
            }
        }
        return toolObjects.get(0);
    }

    /** 工具循环上下文滑动窗口：保留 system+首轮 user（+摘要归档），中段只留最近 window 条；
     *  切点对齐到非工具结果消息，避免拆散「AI 工具调用↔结果」配对导致协议报错；
     *  钉住工具（如 askUser）的消息对被驱逐前提取到头部永久保留（用户已确认口径不可遗忘）；
     *  被驱逐的非钉住消息确定性压缩为摘要归档（零 LLM 成本）插入头部，模型裁窗后仍知探索过什么。
     *  <p>token 维度预算（L1 治理，I11 双约束取更严）：条数切点算法一行不改（I9 关闭态逐条等价），
     *  tokenBudget&gt;0 时另算反向累加切点，两者取更严（max：保留段须同时满足两上限）后仍统一走
     *  同一段切点回退（I1）；
     *  驱逐出口唯一——无论哪个维度触发都走同一条 digest 路径（I12），不新增摘要格式、不新增 LLM 调用。
     *  <p>private（非公共 API，仅供 run() 内部调用）：token 切点与关闭态等价性经 run() 黑盒测试覆盖
     *  （签名含私有嵌套类型 RunStats，同包测试无法直打）。
     *  引用不变式：I1/I2/I3/I9/I11/I12/I15（A9：recallUsable 时 digest 行内写句柄+指令行，同源见 run()） */
    private void trimToolMemory(List<ChatMessage> messages, AgentLoopOptions opt, int fixedOverheadTokens,
                                int tokenBudget, RunStats stats) {
        // 头部保留：有 system 时 system+首轮 user 两条，无 system 时仅首轮 user 一条（与组装口径联动）
        int headEnd = (!messages.isEmpty() && messages.get(0) instanceof SystemMessage)
                ? MEMORY_HEAD : MEMORY_HEAD - 1;
        String oldDigest = "";
        if (messages.size() > headEnd && messages.get(headEnd) instanceof UserMessage) {
            String t = ((UserMessage) messages.get(headEnd)).singleText();
            if (t != null && t.startsWith(DIGEST_PREFIX)) {
                String body = t.substring(DIGEST_PREFIX.length());
                // A9 幂等回收：旧摘要若含回捞指令行（DIGEST_PREFIX 之后第一行）先剥再续写，
                // 防多轮驱逐重拼时指令行重复；关闭态旧摘要本无指令行，剥离为空操作（I9 不受扰）
                if (body.startsWith(DIGEST_RECALL_GUIDANCE)) {
                    body = body.substring(DIGEST_RECALL_GUIDANCE.length());
                }
                oldDigest = body;
                headEnd = headEnd + 1;
            }
        }
        int window = opt.getMemoryWindow();
        // 步骤 A：条数切点——既有算法原样（-1=未触发）；条数未超时不能直接 return，还须判 token 分支（I11）
        int fromCount = messages.size() <= headEnd + window ? -1 : messages.size() - window;
        // 步骤 B：token 切点——从尾部反向累加估算（含 specs 固定开销 + head 段），超预算即止（I8 宁高不低）
        int fromToken = -1;
        if (tokenBudget > 0 && messages.size() > headEnd) {
            long acc = (long) fixedOverheadTokens
                    + ContextTokenEstimator.estimateMessages(messages.subList(0, headEnd));
            int i = messages.size() - 1;
            while (i >= headEnd) {
                int est = ContextTokenEstimator.estimateMessage(messages.get(i));
                if (acc + est > tokenBudget) {
                    break;
                }
                acc += est;
                i--;
            }
            fromToken = i + 1;
            // 步骤 D 兜底：尾部最近一组自身就超预算时回退保留最近一组——不裁空、不抛
            if (fromToken > messages.size() - 1) {
                fromToken = messages.size() - 1;
            }
            // 全保留仍在预算内 → 本维度无需驱逐（fromToken==headEnd 语义归一为未触发）
            if (fromToken <= headEnd) {
                fromToken = -1;
            }
        }
        if (fromCount < 0 && fromToken < 0) {
            return;
        }
        // 双约束取更严（保留段须同时满足条数与预算两个上限 → 取更靠后/驱逐更多的切点，I11）；
        // 单维度触发时取触发者
        int from = fromCount < 0 ? fromToken : (fromToken < 0 ? fromCount : Math.max(fromCount, fromToken));
        // 切点回退避拆对（唯一防线，token 新切点同样必须再走这一段，I1）
        while (from > headEnd && messages.get(from) instanceof ToolExecutionResultMessage) {
            from--;
        }
        if (from <= headEnd) {
            return;
        }
        // 步骤 C：驱逐单一出口（I12）——钉住搬移 + digest 归档 + 重组，条数/token 触发共用
        boolean tokenTriggered = fromToken >= 0 && (fromCount < 0 || fromToken >= fromCount);
        List<ChatMessage> pinned = pinnedBefore(messages, headEnd, from, opt.getPinnedTools());
        String digestNew = digestOf(messages, headEnd, from, pinned, stats.recallUsable);
        List<ChatMessage> tail = new ArrayList<>(messages.subList(from, messages.size()));
        messages.subList(headEnd, messages.size()).clear();
        String digest = trimDigest(oldDigest + digestNew);
        if (!digest.isEmpty()) {
            // A9：回捞指令行落在 DIGEST_PREFIX 之后、正文之前，仅 recallUsable 时存在（I15 同源）；
            // 不占 DIGEST_MAX_LEN 容量（该行是指令非归档内容）
            messages.add(UserMessage.from(DIGEST_PREFIX
                    + (stats.recallUsable ? DIGEST_RECALL_GUIDANCE : "") + digest));
        }
        messages.addAll(pinned);
        messages.addAll(tail);
        stats.totalEvicted += from - headEnd - pinned.size();
        if (tokenTriggered) {
            stats.sink.log("token 预算触发驱逐：保留尾部 " + tail.size() + " 条（钉住对 " + pinned.size()
                    + " 条搬移头部保留），驱逐入摘要 " + (from - headEnd - pinned.size()) + " 条");
        }
        // 步骤 D：软超只观测不迭代——钉住对不可驱逐致保留段仍超预算时告警一次即止（防不可终止）
        if (tokenBudget > 0) {
            int estNow = ContextTokenEstimator.estimateMessages(messages) + fixedOverheadTokens;
            if (estNow > tokenBudget) {
                log.warn("上下文 token 预算软超: 估算 {} > 预算 {}（钉住对不可驱逐所致）", estNow, tokenBudget);
            }
        }
    }

    /** 工具结果入场统一收敛点（三处 add 全走此处）：卸载判定在 messages.add 之前（I2 只替换文本
     *  不改位置/类型/id）；allowOffload=false 供收口路径保原文（I10）；neverOffload 命中保原文（I6）；
     *  无回捞通道由 shouldOffload 的 recallChannelAvailable=false 拒绝卸载（I5）。private（非公共 API，
     *  仅供 run() 内部调用），卸载行为经 run() 黑盒测试覆盖。
     *  引用不变式：I2/I5/I6/I7/I10 */
    private void appendToolResultMessage(List<ChatMessage> messages, ToolExecutionRequest req, String resultText,
                                         Set<String> neverOffload, int offloadChars,
                                         int previewChars, boolean allowOffload, RunStats stats) {
        String text = resultText;
        if (allowOffload && ToolResultOffloader.shouldOffload(req.name(), resultText, offloadChars,
                neverOffload, stats.recallAvailable)) {
            String pointer = ToolResultOffloader.pointerOf(req.id(), req.name(), resultText.length(),
                    ContextTokenEstimator.estimateText(resultText), resultText, previewChars);
            if (pointer != null) {
                text = pointer;
                stats.offloadedThisRound++;
                stats.totalOffloaded++;
                stats.offloadedHandles.add(req.id());
                stats.sink.log("工具结果入场卸载：" + req.name() + " tool_call_id=" + req.id()
                        + " 原 " + resultText.length() + " 字 → 指针 " + pointer.length()
                        + " 字（原文已经事件流归档，可按句柄回捞）");
            }
        }
        messages.add(ToolExecutionResultMessage.from(req, text));
    }

    /** 计量日志（验收数字来源，I8 校准回路）：每次 logged.generate 之前一条，与 LoggingModel
     *  的端点真实 token 透出配对（估算 vs 实测）。仅治理开启态输出（I9：关闭态事件流零新增）；
     *  每轮输出一行并清零本轮卸载计数（累计计数不清） */
    private void logContextMeter(AgentLoopListener sink, RunStats stats, List<ChatMessage> messages,
                                 int fixedOverheadTokens, int tokenBudget, boolean offloadOn) {
        if (tokenBudget <= 0 && !offloadOn) {
            return;
        }
        sink.log("上下文计量：条数=" + messages.size()
                + " 字符=" + ContextTokenEstimator.countChars(messages)
                + " 估算token=" + (ContextTokenEstimator.estimateMessages(messages) + fixedOverheadTokens)
                + "（含工具定义 " + fixedOverheadTokens + "）"
                + " 本轮卸载=" + stats.offloadedThisRound
                + " 累计卸载=" + stats.totalOffloaded
                + " 累计回捞=" + (stats.recallTool == null ? 0 : stats.recallTool.used())
                + " 累计驱逐=" + stats.totalEvicted);
        stats.offloadedThisRound = 0;
    }

    /** run() 作用域统计容器（I14：runner 无实例字段，本对象只作为 run 内局部变量存在）：
     *  卸载/回捞/驱逐计数 + 本 run 已卸载句柄台账（回捞未命中时喂可用清单） */
    private static final class RunStats {
        final AgentLoopListener sink;
        boolean recallAvailable;
        /** A9/I15：回捞唯一同源判据（run() 头部一次性计算，回捞工具注册与 digest 写句柄共用） */
        boolean recallUsable;
        AgentToolRecall recallTool;
        final List<String> offloadedHandles = new ArrayList<>();
        int offloadedThisRound;
        int totalOffloaded;
        int totalEvicted;

        RunStats(AgentLoopListener sink) {
            this.sink = sink;
        }
    }

    /** 被驱逐区段确定性压缩：工具调用一行（名+参数摘要→结果摘要）、结论文本一行；
     *  钉住对完整保留不入摘要。withHandles=true（recallUsable，A9/I15）时命中工具结果的行尾
     *  追加「（句柄=tool_call_id）」——原文恒在会话事件流（I4），句柄即 recallToolResult 取数键，
     *  被驱逐结果与入场卸载指针（I7）同等可回捞；false（关闭态）不写，digest 逐字符不变（I9） */
    private static String digestOf(List<ChatMessage> messages, int headEnd, int from, List<ChatMessage> pinned,
                                   boolean withHandles) {
        Set<ChatMessage> kept = Collections.newSetFromMap(new IdentityHashMap<>());
        kept.addAll(pinned);
        StringBuilder sb = new StringBuilder();
        for (int i = headEnd; i < from; i++) {
            ChatMessage m = messages.get(i);
            if (kept.contains(m) || !(m instanceof AiMessage)) {
                continue;
            }
            AiMessage ai = (AiMessage) m;
            if (ai.hasToolExecutionRequests()) {
                int j = i + 1;
                for (ToolExecutionRequest r : ai.toolExecutionRequests()) {
                    sb.append("· ").append(r.name()).append("(").append(brief(r.arguments(), 60)).append(")");
                    if (j < from && messages.get(j) instanceof ToolExecutionResultMessage
                            && !kept.contains(messages.get(j))) {
                        sb.append(" → ").append(brief(((ToolExecutionResultMessage) messages.get(j)).text(), 100));
                        if (withHandles) {
                            sb.append("（句柄=").append(r.id()).append("）");
                        }
                        j++;
                    }
                    sb.append("\n");
                }
            } else if (ai.text() != null && !ai.text().trim().isEmpty()) {
                sb.append("· 轮结论：").append(brief(ai.text(), 100)).append("\n");
            }
        }
        return sb.toString();
    }

    /** 摘要长度封顶：超出保留最近部分（越新的探索越有参考价值） */
    private static String trimDigest(String d) {
        return d.length() <= DIGEST_MAX_LEN ? d : "…" + d.substring(d.length() - DIGEST_MAX_LEN);
    }

    /** 探索摘要归档前缀（头部识别标记；⚠️ 关闭态也走此头部识别，trimToolMemory 靠
     *  startsWith(DIGEST_PREFIX) 定位旧摘要——本常量一字不可改，A9 指令行只能落在其后） */
    private static final String DIGEST_PREFIX = "【探索记录摘要（窗口裁切压缩归档）】\n";
    /** A9 回捞指令行（仅 recallUsable 时拼在 DIGEST_PREFIX 之后、摘要正文之前；重拼时幂等剥离，
     *  见 trimToolMemory 旧摘要回收；不占 DIGEST_MAX_LEN 封顶容量） */
    private static final String DIGEST_RECALL_GUIDANCE =
            "被裁内容如需原文：凭行内「句柄=」调 recallToolResult，一次一个，受次数上限约束。\n";
    /** 摘要归档长度上限（字符） */
    private static final int DIGEST_MAX_LEN = 3000;

    /** 提取即将被驱逐区段内的钉住消息对：命中钉住工具名的 AI 调用消息及其紧随的全部结果
     *  （同消息多工具调用时整对保留，避免部分结果缺失触发端点协议报错；
     *  已钉在头部的历史对按引用去重，不重复追加） */
    private static List<ChatMessage> pinnedBefore(List<ChatMessage> messages, int headEnd, int from, Set<String> pinnedTools) {
        List<ChatMessage> pinned = new ArrayList<>();
        if (pinnedTools == null || pinnedTools.isEmpty()) {
            return pinned;
        }
        Set<ChatMessage> seen = Collections.newSetFromMap(new IdentityHashMap<>());
        for (int i = headEnd; i < from; i++) {
            ChatMessage m = messages.get(i);
            if (!(m instanceof AiMessage) || seen.contains(m)) {
                continue;
            }
            AiMessage ai = (AiMessage) m;
            if (!ai.hasToolExecutionRequests()) {
                continue;
            }
            boolean hit = false;
            for (ToolExecutionRequest r : ai.toolExecutionRequests()) {
                if (pinnedTools.contains(r.name())) {
                    hit = true;
                    break;
                }
            }
            if (!hit) {
                continue;
            }
            pinned.add(ai);
            seen.add(ai);
            for (int j = i + 1; j < from && messages.get(j) instanceof ToolExecutionResultMessage; j++) {
                pinned.add(messages.get(j));
                seen.add(messages.get(j));
            }
        }
        return pinned;
    }

    /** 中断识别：cause 链含中断类异常即视为停止请求（HTTP 阻塞调用被打断通常表现为 InterruptedIOException；
     *  Redis 等同步等待被打断表现为 InterruptedException 被容器包装外溢）——会话层收口归类同口径复用 */
    public static boolean isInterrupted(Throwable e) {
        Throwable t = e;
        while (t != null && t.getCause() != t) {
            if (t instanceof InterruptedException || t instanceof java.io.InterruptedIOException
                    || t instanceof java.nio.channels.ClosedByInterruptException) {
                return true;
            }
            t = t.getCause();
        }
        return false;
    }

    /** 模型端点不支持 function calling 的异常识别（OpenAI 兼容端点对 tools 参数报 4xx 的常见文案） */
    private static boolean isToolUnsupported(RuntimeException e) {
        String msg = (e.getMessage() == null ? "" : e.getMessage()).toLowerCase();
        Throwable cause = e.getCause();
        if (cause != null && cause.getMessage() != null) {
            msg += " " + cause.getMessage().toLowerCase();
        }
        return msg.contains("tool") && (msg.contains("not support") || msg.contains("unsupported")
                || msg.contains("unknown parameter") || msg.contains("unrecognized") || msg.contains("invalid"));
    }

    /** langchain4j 0.33 AiServices 工具循环硬上限识别（写死 10 轮不可配）：连续工具调用超限的异常文案 */
    private static boolean isToolLoopExceeded(RuntimeException e) {
        String msg = e.getMessage() == null ? "" : e.getMessage();
        return msg.contains("sequential tool executions");
    }

    /**
     * 日志代理模型（0.33 无 chatModelListener，用包装类替代）：每轮请求/响应/失败推一条过程日志，
     * 让业务侧进度窗实时看见 LLM 在干活；直连回落链路同样经过包装（String 重载走 default 实现最终路由到此处）。
     * 同步调用经可中断包装（interruptibly）：子线程跑实际 HTTP 调用，调用线程分片等待——
     * 线程中断立即打断（OkHttp 阻塞读不响应 interrupt，不包装则须等当轮 LLM 返回才收口），
     * 停止键（跨实例场景线程中断不可达）由等待轮询感知，同样快速收口；
     * 代价是被弃子线程的 HTTP 调用自然跑完（结果丢弃）
     */
    private static class LoggingModel implements ChatLanguageModel {
        private final ChatLanguageModel delegate;
        private final Consumer<String> logSink;
        private final BooleanSupplier stopCheck;
        private final java.util.concurrent.atomic.AtomicInteger round = new java.util.concurrent.atomic.AtomicInteger();

        /** 停止键等待轮询周期（毫秒）：停止响应延迟上限，远小于单轮 LLM 耗时 */
        private static final long STOP_POLL_MILLIS = 500;

        /** LLM 同步调用执行池（守护线程，峰值并发=同时进行的生成任务数，全局锁下很小） */
        private static final java.util.concurrent.ExecutorService LLM_CALLS = java.util.concurrent.Executors.newCachedThreadPool(r -> {
            Thread t = new Thread(r, "llm-call-interruptible");
            t.setDaemon(true);
            return t;
        });

        LoggingModel(ChatLanguageModel delegate, Consumer<String> logSink, BooleanSupplier stopCheck) {
            this.delegate = delegate;
            this.logSink = logSink;
            this.stopCheck = stopCheck;
        }

        @Override
        public Response<AiMessage> generate(List<ChatMessage> messages) {
            return logRound(messages, () -> delegate.generate(messages));
        }

        @Override
        public Response<AiMessage> generate(List<ChatMessage> messages, List<ToolSpecification> specs) {
            return logRound(messages, () -> delegate.generate(messages, specs));
        }

        @Override
        public Response<AiMessage> generate(List<ChatMessage> messages, ToolSpecification spec) {
            return logRound(messages, () -> delegate.generate(messages, spec));
        }

        private Response<AiMessage> logRound(List<ChatMessage> messages, Supplier<Response<AiMessage>> call) {
            report("LLM 第 " + round.incrementAndGet() + " 轮请求已发出（携带 " + messages.size() + " 条上下文消息）");
            try {
                Response<AiMessage> resp = interruptibly(call, stopCheck);
                TokenUsage usage = resp.tokenUsage();
                String tokens = usage == null ? ""
                        : "（tokens：输入 " + usage.inputTokenCount() + " / 输出 " + usage.outputTokenCount() + "）";
                AiMessage ai = resp.content();
                if (ai != null && ai.hasToolExecutionRequests()) {
                    // 工具调用轮：把 LLM 决定调的工具与参数摘要透出（工具返回另有工具侧上报）
                    report("LLM 响应已收到，决定调用工具" + tokens);
                    for (ToolExecutionRequest req : ai.toolExecutionRequests()) {
                        report("└ 工具调用：" + req.name() + briefArgs(req.arguments()));
                    }
                } else {
                    // 结论轮：截取 LLM 实际输出的分析/结论正文
                    report("LLM 响应已收到，生成结论" + tokens);
                    String text = ai == null ? null : ai.text();
                    if (text != null && !text.trim().isEmpty()) {
                        report("└ LLM 输出：" + brief(text, 180));
                    }
                }
                return resp;
            } catch (RuntimeException e) {
                report("LLM 请求失败：" + e.getMessage());
                throw e;
            }
        }

        private void report(String line) {
            if (logSink != null) {
                logSink.accept(line);
            }
        }

        /** 可中断执行：子线程跑实际 HTTP 调用，调用线程分片 Future.get 等待——
         *  ① 线程中断立即抛（同实例 stop 的 interrupt 路径）；
         *  ② 每片间隙查停止键（跨实例/会话键路径，中断不可达时也能收口）；
         *  被停止时恢复中断标记并抛 RuntimeException（上层 isInterrupted 收口为 STOPPED），
         *  子线程的调用不强行取消（OkHttp 无法即时切断），跑完自然回收、结果丢弃 */
        private static Response<AiMessage> interruptibly(Supplier<Response<AiMessage>> call, BooleanSupplier stopCheck) {
            java.util.concurrent.Future<Response<AiMessage>> future = LLM_CALLS.submit(call::get);
            try {
                while (true) {
                    try {
                        return future.get(STOP_POLL_MILLIS, java.util.concurrent.TimeUnit.MILLISECONDS);
                    } catch (java.util.concurrent.TimeoutException te) {
                        if (stopCheck != null && stopCheck.getAsBoolean()) {
                            Thread.currentThread().interrupt();
                            throw new RuntimeException("LLM 调用被停止请求中断", te);
                        }
                    }
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new RuntimeException("LLM 调用被停止请求中断", e);
            } catch (java.util.concurrent.ExecutionException e) {
                Throwable cause = e.getCause();
                if (cause instanceof RuntimeException) {
                    throw (RuntimeException) cause;
                }
                if (cause instanceof Error) {
                    throw (Error) cause;
                }
                throw new RuntimeException(cause == null ? e : cause);
            }
        }

        /** 日志截断：压空白只留头部，避免进度记录膨胀 */
        private String brief(String s, int max) {
            String t = s == null ? "" : s.replaceAll("\\s+", " ").trim();
            return t.length() <= max ? t : t.substring(0, max) + "…";
        }

        /** 工具入参摘要：空参省略，非空参数截取前 80 字符 */
        private String briefArgs(String args) {
            if (args == null || args.trim().isEmpty() || "{}".equals(args.trim())) {
                return "";
            }
            return "（参数：" + brief(args, 80) + "）";
        }
    }
}
