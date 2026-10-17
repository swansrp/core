package com.bidr.insight.smartquery.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.bidr.insight.smartquery.dao.entity.InsightAgent;
import com.bidr.insight.smartquery.dao.entity.InsightAgentAsset;
import com.bidr.insight.smartquery.dao.entity.InsightAgentProposal;
import com.bidr.insight.smartquery.dao.repository.InsightAgentEscapeLogService;
import com.bidr.insight.smartquery.dao.repository.InsightAgentService;
import com.bidr.insight.smartquery.derive.IndicatorDeriver;
import com.bidr.insight.smartquery.derive.StatisticResConverter;
import com.bidr.insight.smartquery.flow.SmartQueryDynamicAgentProvider;
import com.bidr.insight.smartquery.layer.DimensionDef;
import com.bidr.insight.smartquery.layer.EntityDef;
import com.bidr.insight.smartquery.layer.MetricDef;
import com.bidr.insight.smartquery.layer.ValueDomainDef;
import com.bidr.insight.smartquery.meta.SupportedDimensionSupport;
import com.bidr.insight.smartquery.model.SemanticQuery;
import com.bidr.insight.smartquery.model.SmartQueryResult;
import com.bidr.insight.smartquery.model.ValidationResult;
import com.bidr.insight.smartquery.semantic.SemanticLayer;
import com.bidr.insight.smartquery.semantic.SemanticLayerRegistry;
import com.bidr.insight.smartquery.semantic.SmartQueryParser;
import com.bidr.insight.smartquery.service.tools.AssetProposalTools;
import com.bidr.insight.smartquery.service.tools.SemanticQueryTools;
import com.bidr.insight.smartquery.vo.SmartQueryAskRes;
import com.bidr.forge.datasource.service.DataSourceCacheService;
import com.bidr.kernel.exception.NoticeException;
import com.bidr.kernel.utils.FuncUtil;
import com.bidr.llm.agent.AgentAskUserTool;
import com.bidr.llm.agent.AgentFailureBreaker;
import com.bidr.llm.agent.AgentLoopListener;
import com.bidr.llm.agent.AgentLoopOptions;
import com.bidr.llm.agent.AgentLoopResult;
import com.bidr.llm.agent.AgentPlanTools;
import com.bidr.llm.agent.session.AgentPlanItem;
import com.bidr.llm.agent.session.PlanBoard;
import com.bidr.llm.agent.ToolAgentRunner;
import com.bidr.llm.agent.conversation.AgentConversationService;
import com.bidr.llm.agent.session.AgentSessionContext;
import com.bidr.llm.agent.session.AgentStage;
import com.bidr.llm.model.LiveModelFactory;
import com.bidr.llm.provider.DbAwareModelConfigProvider;
import com.bidr.llm.sse.SseEventSender;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import dev.langchain4j.model.chat.ChatLanguageModel;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import javax.sql.DataSource;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

/**
 * Title: SmartQueryMaintainService
 * Description: 维护问数编排：自然语言问题 → LLM 解析 semantic_query → 已发布资产校验；
 * 通过则直接执行作答；失败则 LLM 维护（带只读探索工具）产出资产建议 + 改写查询，
 * 以「临时语义层」（已发布资产 + 建议项，ThreadLocal 叠加）执行一次性查询作答，
 * 建议落待审提案表（不动草稿/发布资产），管理员审批合并进草稿后经既有发布+刷新生效。
 * 运行期常规问数链路零改动（临时层仅本线程可见）
 *
 * @author Sharp
 * @since 2026/8/19
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SmartQueryMaintainService {

    private final SmartQueryService smartQueryService;
    private final SmartQueryParser parser;
    private final SemanticLayerRegistry layers;
    private final AgentAssetCacheService agentAssetCacheService;
    private final SmartAgentMetaService smartAgentMetaService;
    private final InsightAgentService insightAgentService;
    /** 兜底命中台账（结晶信号源）：兜底成功后旁路记录，写失败不影响问数 */
    private final InsightAgentEscapeLogService escapeLogService;
    private final ProposalService proposalService;
    private final DataSourceCacheService dataSourceCacheService;
    private final ObjectProvider<ChatLanguageModel> chatModelProvider;
    /** 流式进度模型工厂（llm 框架 Bean：自建 SSE 客户端+同步回落，代理/重试口径随 Bean 固化） */
    private final LiveModelFactory liveModelFactory;
    private final StatisticResConverter statisticConverter;
    private final IndicatorDeriver indicatorDeriver;
    /** 通用历史对话存储（提交+轮询链落盘：提问即存，终态补写助手回复含全量回放负载） */
    private final AgentConversationService agentConversationService;

    private final ObjectMapper om = new ObjectMapper().setSerializationInclusion(JsonInclude.Include.NON_NULL);

    /** 通用 Agent 工具循环引擎（与资产生成同引擎：轮次控制/滑窗/触顶收口/回落直连） */
    private final ToolAgentRunner toolAgentRunner = new ToolAgentRunner();

    /** 流式问数编排执行器（多轮 LLM 长耗时，不占 servlet 线程；临时层 ThreadLocal 全程同线程，隔离不受影响） */
    private final ExecutorService askExecutor = Executors.newCachedThreadPool(r -> {
        Thread t = new Thread(r, "sq-maintain-ask-" + ASK_THREAD_SEQ.incrementAndGet());
        t.setDaemon(true);
        return t;
    });
    private static final AtomicInteger ASK_THREAD_SEQ = new AtomicInteger();

    /** 流式端点自扩事件：编排阶段进度文案（其余事件复用 SseEventSender 协议常量） */
    private static final String EVENT_STEP = "step";

    /** 流式端点自扩事件：LLM 流式应答 live 进度（替换式展示，不追加 step 清单） */
    private static final String EVENT_LIVE = "live";

    /** LLM 思考归档行前缀（StreamingProgressChatModel onComplete 推送）：每轮思考全文，
     *  live 为替换式会覆盖上一轮，归档行进 step 清单永久留存（折叠回看 + 一键复制） */
    private static final String LIVE_ARCHIVE_PREFIX = "【LLM 思考归档】";

    /** 资产文件清单（含 sensitive-fields/row-policies；语义层构建用前六类，row-policies 渲染期行权限注入） */
    private static final String[] ASSET_FILES = {"entities.json", "relations.json", "metrics.json",
            "dimensions.json", "value-domains.json", "concepts.json", "sensitive-fields.json", "row-policies.json"};

    /** 提示词占位符 */
    private static final String PH_INDEX = "{asset_index}";
    /** 旧版全量目录占位符（已保存的 llm-prompts 草稿可能还在用，同口径填索引兼容） */
    private static final String PH_CATALOG_LEGACY = "{catalog_json}";
    private static final String PH_QUESTION = "{question}";
    private static final String PH_ERRORS = "{errors_json}";

    /** 精简索引行数上限：超出截断并引导用 searchAssets 检索 */
    private static final int INDEX_MAX_LINES = 2000;

    /** 解析链工具循环预算：检索核实 + 组装，轮次/窗口均小于维护链；轮次为防死循环保险
 *  （时间预算 240s 为硬顶），计划仪式轮已豁免不计（AgentPlanTools.ROUND_EXEMPT_TOOLS） */
    private static final int MAX_PARSE_ROUNDS = 12;
    private static final int PARSE_MEMORY_WINDOW = 30;

    /** 修复链工具循环预算（每次执行失败修复一次，预算小于解析） */
    private static final int MAX_REPAIR_ROUNDS = 6;
    private static final int REPAIR_MEMORY_WINDOW = 20;

    /** 维护链工具循环预算：探索 + 建议，轮数多于解析但硬封顶 */
    private static final int MAX_MAINTAIN_ROUNDS = 14;
    private static final int MAINTAIN_MEMORY_WINDOW = 60;

    /** 兜底 SQL 通道工具循环预算（语义层两轮校验均失败时的最后逃生） */
    private static final int MAX_ESCAPE_ROUNDS = 6;
    private static final int ESCAPE_MEMORY_WINDOW = 20;
    private static final int ESCAPE_BUDGET_SECONDS = 120;

    /** 跨阶段事实台账注入上限（条）：超出截断（防极端长探索污染后续子会话） */
    private static final int FACTS_MAX_LINES = 60;

    /** 事实行压缩截断上限（压空白 + 截断，与工具侧 FACT_MAX_LEN 同量级） */
    private static final int FACT_LINE_MAX_LEN = 240;

    /** 各链总时间预算（秒）：防多轮慢模型串行叠加导致单题长跑无结论，超预算走收口路径强制产出结论 */
    private static final int PARSE_BUDGET_SECONDS = 240;
    private static final int MAINTAIN_BUDGET_SECONDS = 420;
    private static final int REPAIR_BUDGET_SECONDS = 150;

    /** 目录检索工具名集合：其调用↔结果对被窗口驱逐前提取到头部永久保留——
     *  资产事实不可被遗忘后重复拉取（实测维护链空转主因：同表反复重查） */
    private static final Set<String> CATALOG_PINNED_TOOLS = new HashSet<>(Arrays.asList(
            "searchAssets", "describeEntity", "metricDetail", "dimensionDetail", "conceptDetail"));

    /** 同参缓存只读探索工具集：同会话内同名+同参调用直接返回缓存（重复拉取零成本）；
     *  与钉住配合——钉住防重发，缓存兜底重复拉取仍发生时的执行开销；只读工具才可登记 */
    private static final Set<String> READONLY_CACHE_TOOLS = new HashSet<>(Arrays.asList(
            "searchAssets", "describeEntity", "metricDetail", "dimensionDetail", "conceptDetail", "findValue",
            "describeTable", "sampleRows", "groupByField", "runSql"));

    /** 问数链提示词默认源：classpath 资源 smartquery/prompts-ask.yml（按功能分文件约定，
     *  解析/维护/修复/兜底/agent 各段模板集中管理，随版本演进）；
     *  parsePrompt / maintainPrompt 两个键支持 per-Agent llm-prompts 草稿覆盖（见 effectivePrompts） */
    private static final String ASK_PROMPTS_RESOURCE = "smartquery/prompts-ask.yml";
    /** 问数链模板内存缓存（资源不随运行期变化，首次加载后不再读盘） */
    private static volatile Map<String, String> askPromptCache;

    /** 问数链提示词模板取值（键缺失属打包问题，直接抛异常阻断） */
    private static String askPrompt(String key) {
        String v = loadAskPrompts().get(key);
        if (v == null) {
            throw new IllegalStateException("问数链提示词模板键缺失: " + key);
        }
        return v;
    }

    /** 包内可见供回归测试校验资源完备性（见 SmartQueryAskPromptsTest） */
    static Map<String, String> loadAskPrompts() {
        Map<String, String> cache = askPromptCache;
        if (cache == null) {
            synchronized (SmartQueryMaintainService.class) {
                cache = askPromptCache;
                if (cache == null) {
                    try (java.io.InputStream in = SmartQueryMaintainService.class
                            .getClassLoader().getResourceAsStream(ASK_PROMPTS_RESOURCE)) {
                        if (in == null) {
                            throw new IllegalStateException("问数链提示词模板资源缺失: " + ASK_PROMPTS_RESOURCE);
                        }
                        Map<String, Object> raw = new org.yaml.snakeyaml.Yaml().load(in);
                        if (raw == null || raw.isEmpty()) {
                            throw new IllegalStateException("问数链提示词模板资源为空: " + ASK_PROMPTS_RESOURCE);
                        }
                        cache = new LinkedHashMap<>();
                        for (Map.Entry<String, Object> e : raw.entrySet()) {
                            if (e.getValue() != null) {
                                cache.put(e.getKey(), String.valueOf(e.getValue()));
                            }
                        }
                        askPromptCache = cache;
                    } catch (java.io.IOException e) {
                        throw new IllegalStateException("问数链提示词模板资源读取失败: " + ASK_PROMPTS_RESOURCE, e);
                    }
                }
            }
        }
        return cache;
    }

    // 问数链各段提示词模板已迁 resources/smartquery/prompts-ask.yml（经 askPrompt 键取）：
    // parseBusiness/parsePrompt/clarifySuffix/planExample（解析）、maintainBusiness/maintainPrompt/
    // toolModeSuffix（维护）、repairBusiness/repairPrompt（修复）、escapeBusiness/escapePrompt（兜底）、
    // agentBusiness（自主维护 agent 会话）

    /** 歧义确认等待作答上限（分钟）：超时引导 LLM 自选口径继续，不无限占用编排线程 */
    @Value("${smartquery.agent.ask-timeout-minutes:5}")
    private long clarifyTimeoutMinutes;

    // ────────────────────────── 问数编排 ──────────────────────────

    /**
     * 维护问数（同步版）：解析 → 校验 →（失败时）LLM 维护 + 临时层一次性查询 + 落待审提案。
     * 多轮 LLM 可能 1-2 分钟，交互体验请用提交+轮询链（askSubmit/askPoll，支持歧义确认）
     */
    public SmartQueryAskRes ask(String agentCode, String question, String chartMode) {
        return askInternal(agentCode, question, chartMode, text -> {
        }, text -> {
        }, null);
    }

    /**
     * 维护问数（SSE 流式版）：后台线程跑同一编排，逐阶段推 step 进度事件，
     * 收尾 done 事件携带与同步版同构的完整应答 JSON；异常以 error 事件结束。
     * 客户端断开时下个进度点主动中断编排，不再空转 LLM。
     * 【已废弃·遗留保留】前端已全面切换提交+轮询链（askSubmit/askPoll，SSE 易被中间层缓冲掐断），
     * 当前无前端调用方；歧义确认（askUser）亦仅接入轮询链，本通道无交互能力
     */
    public void askStream(String agentCode, String question, String chartMode, SseEmitter emitter) {
        SseEventSender sender = new SseEventSender(emitter);
        Consumer<String> progress = text -> {
            if (sender.isClientGone()) {
                throw new NoticeException("客户端已断开，终止维护问数");
            }
            sender.send(EVENT_STEP, text);
        };
        // 思考归档行进 step 事件（永久留存可回看），其余 live 进度仍走替换式展示
        Consumer<String> live = text -> {
            if (text != null && text.startsWith(LIVE_ARCHIVE_PREFIX)) {
                sender.send(EVENT_STEP, text);
            } else {
                sender.send(EVENT_LIVE, text);
            }
        };
        askExecutor.submit(() -> {
            try {
                SmartQueryAskRes res = askInternal(agentCode, question, chartMode, progress, live, null);
                sender.send(SseEventSender.EVENT_DONE, writeJson(res));
            } catch (Exception e) {
                log.warn("Agent '{}' 流式维护问数失败", agentCode, e);
                sender.send(SseEventSender.EVENT_ERROR,
                        e.getMessage() == null ? "维护问数失败" : e.getMessage());
            } finally {
                sender.complete();
            }
        });
    }

    /**
     * 维护问数（提交+轮询版，SSE 替代主通道）：即刻返回票据，后台线程跑同一编排，
     * step 过程日志（阶段进度 + 工具循环轮次/调用/返回摘要，即 LLM 思考过程）与结果
     * 累积写入票据，前端经 {@link #askPoll} 每 2s 增量拉取实时上屏。
     * SSE 易被中间层（反代/杀软/浏览器插件）缓冲掐断致前端无进度长转圈，轮询同
     * 资产生成 progress/AgentChat 会话模式，送达可靠；取消语义经 {@link #askCancel}
     * 打取消标记，下个进度点抛出中断编排，不再空转 LLM
     */
    public String askSubmit(String agentCode, String question, String chartMode, String operator) {
        evictExpiredTickets();
        String ticketId = UUID.randomUUID().toString().replace("-", "");
        AskTicket ticket = new AskTicket();
        // 历史对话：提问即存（新对话在此创建），agentCode 与统一注册中心同构（smartquery:{code}）；
        // 对话是辅助链路，失败不影响问数主流程（conversationId 空则终态不补写）
        try {
            ticket.conversationId = agentConversationService.appendUser(
                    SmartQueryDynamicAgentProvider.NAMESPACE + ":" + agentCode,
                    null, operator, question, null);
        } catch (Exception e) {
            log.warn("问数对话落盘失败（不影响问数）, agent={}, error={}", agentCode, e.getMessage());
        }
        askTickets.put(ticketId, ticket);
        Consumer<String> progress = text -> {
            if (ticket.cancelled) {
                throw new NoticeException("问数已停止");
            }
            ticket.steps.add(text);
        };
        // live 进度为替换式展示（LLM 流式应答字数），不进 step 清单；
        // 思考归档行例外进 steps（替换式会被下一轮覆盖，归档需永久留存供回看/复制）
        Consumer<String> live = text -> {
            if (text != null && text.startsWith(LIVE_ARCHIVE_PREFIX)) {
                ticket.steps.add(text);
            } else {
                ticket.live = text;
            }
        };
        askExecutor.submit(() -> {
            SmartQueryAskRes res = null;
            try {
                res = askInternal(agentCode, question, chartMode, progress, live, ticket);
                ticket.resultJson = writeJson(res);
                ticket.status = "done";
            } catch (Exception e) {
                log.warn("Agent '{}' 提交式维护问数失败", agentCode, e);
                ticket.errorMessage = e.getMessage() == null ? "维护问数失败" : e.getMessage();
                ticket.status = "error";
            } finally {
                ticket.finishedAt = System.currentTimeMillis();
                ticket.planBoard.settle("done".equals(ticket.status));
                appendAskConversation(ticket, res);
            }
        });
        return ticketId;
    }

    /**
     * 问数票据增量轮询：返回 offset 之后的 steps + 终态（done 携带与同步版同构
     * result；error 携带 errorMessage）。票据不存在（已清理/服务重启）时返回 error
     * 终态由前端收口停轮询
     */
    public Map<String, Object> askPoll(String ticketId, int offset) {
        Map<String, Object> res = new LinkedHashMap<>();
        AskTicket ticket = askTickets.get(ticketId);
        if (ticket == null) {
            res.put("status", "error");
            res.put("errorMessage", "问数票据不存在或已过期，请重新提交");
            return res;
        }
        List<String> steps = new ArrayList<>();
        synchronized (ticket.steps) {
            int safeOffset = Math.max(0, Math.min(offset, ticket.steps.size()));
            steps.addAll(ticket.steps.subList(safeOffset, ticket.steps.size()));
            res.put("nextOffset", ticket.steps.size());
        }
        res.put("steps", steps);
        res.put("live", ticket.live == null ? "" : ticket.live);
        res.put("status", ticket.status);
        // 历史对话定位标识随轮询下发（前端实时消息点赞/点踩经通用 rate 端点定位）
        if (ticket.conversationId != null) {
            res.put("conversationId", ticket.conversationId);
        }
        if (ticket.messageId != null) {
            res.put("messageId", ticket.messageId);
        }
        // 歧义确认待答问题随轮询下发（前端渲染选项卡，作答经 /ask/answer 唤醒编排）
        ClarifyQuestion cq = ticket.pendingClarify;
        if (cq != null && !cq.answered) {
            ObjectNode qn = om.createObjectNode();
            qn.put("id", cq.id);
            qn.put("question", cq.question);
            ArrayNode opts = qn.putArray("options");
            if (cq.options != null) {
                cq.options.forEach(opts::add);
            }
            res.put("question", qn);
        }
        // 计划待办清单随轮询全量下发（前端勾选清单实时跳动，终态后保留回看）
        if (!ticket.planBoard.isEmpty()) {
            ArrayNode planArr = om.createArrayNode();
            for (AgentPlanItem item : ticket.planBoard.items()) {
                ObjectNode pn = planArr.addObject();
                pn.put("id", item.getId());
                pn.put("text", item.getText());
                pn.put("status", item.getStatus());
                if (item.getNote() != null) {
                    pn.put("note", item.getNote());
                }
            }
            res.put("plan", planArr);
        }
        if ("done".equals(ticket.status) && ticket.resultJson != null) {
            try {
                res.put("result", om.readTree(ticket.resultJson));
            } catch (Exception e) {
                res.put("result", ticket.resultJson);
            }
        } else if ("error".equals(ticket.status)) {
            res.put("errorMessage", ticket.errorMessage);
        }
        return res;
    }

    /** 停止问数：打取消标记，编排在下个进度点抛出终止（不空转 LLM）；票据不存在或已终态返回 false */
    public boolean askCancel(String ticketId) {
        AskTicket ticket = askTickets.get(ticketId);
        if (ticket == null || !"running".equals(ticket.status)) {
            return false;
        }
        ticket.cancelled = true;
        return true;
    }

    /** 歧义确认作答：唤醒等待中的编排线程（answer 空=交由 AI 自选口径）；无票据/无待答问题返回 false */
    public boolean askAnswer(String ticketId, String answer) {
        AskTicket ticket = askTickets.get(ticketId);
        if (ticket == null) {
            return false;
        }
        ClarifyQuestion q = ticket.pendingClarify;
        if (q == null || q.answered) {
            return false;
        }
        synchronized (q) {
            q.answer = FuncUtil.isEmpty(answer) ? null : answer.trim();
            q.answered = true;
            q.notifyAll();
        }
        return true;
    }

    /** 歧义确认阻塞等待（askUser 工具回调）：待答问题挂票据经 askPoll 下发前端选项卡，
     *  用户作答唤醒；取消抛异常终止编排，超时引导 LLM 自选口径（同自主链 ask_user 哨兵口径） */
    private String awaitClarify(AskTicket ticket, String question, List<String> options) {
        ClarifyQuestion q = new ClarifyQuestion(question, options);
        ticket.pendingClarify = q;
        log.info("问数歧义确认等待作答：{}", question);
        long deadline = System.currentTimeMillis() + clarifyTimeoutMinutes * 60_000L;
        synchronized (q) {
            while (!q.answered) {
                if (ticket.cancelled) {
                    throw new NoticeException("问数已停止");
                }
                long remain = deadline - System.currentTimeMillis();
                if (remain <= 0) {
                    ticket.pendingClarify = null;
                    return "用户 " + Math.max(1, clarifyTimeoutMinutes) + " 分钟内未作答。按你的合理判断自行选择口径继续，并在最终应答中注明该口径未经用户确认";
                }
                try {
                    q.wait(Math.min(1000, remain));
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new NoticeException("问数已中断");
                }
            }
        }
        ticket.pendingClarify = null;
        if (q.answer == null) {
            return "用户将该问题交由你决定：自选最可能的口径继续，并在最终应答中注明假设";
        }
        return "用户确认：" + q.answer + "\n（该答复即口径依据，按其组装查询）";
    }

    /** 已完成票据保留时长（ms）：过期于每次提交时清理，防长驻进程内存无限增长 */
    private static final long TICKET_RETAIN_MS = 10 * 60 * 1000L;

    /**
     * 终态补写历史对话助手回复：正文为应答摘要/错误/停止说明，ext.payload 携全量回放负载
     * （result/plan/steps，与 askPoll 终态同构，前端历史恢复经 applyAskPayload 重放）。
     * 停止状态单独记 stopped（票据 cancelled 标记判定，与轮询协议的 error 解耦）
     */
    private void appendAskConversation(AskTicket ticket, SmartQueryAskRes res) {
        if (ticket.conversationId == null) {
            return;
        }
        try {
            String content;
            String status;
            if ("done".equals(ticket.status)) {
                status = "done";
                content = res != null && res.isValid()
                        ? "已按口径组装查询并返回 " + res.getRows().size() + " 行结果"
                        : "问数完成（执行未通过，详情见回放负载）";
            } else if (ticket.cancelled) {
                status = "stopped";
                content = "问数已被用户停止";
            } else {
                status = "error";
                content = ticket.errorMessage == null ? "维护问数失败" : ticket.errorMessage;
            }
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("status", status);
            if (ticket.resultJson != null) {
                // 各阶段提示词全文体积大且仅供实时调试：历史落盘剥离（回放不带提示词，实时轮询应答仍携带）
                JsonNode result = om.readTree(ticket.resultJson);
                if (result instanceof ObjectNode) {
                    ((ObjectNode) result).remove("prompts");
                }
                payload.put("result", result);
            }
            if (!"done".equals(status) && ticket.errorMessage != null) {
                payload.put("errorMessage", ticket.errorMessage);
            }
            synchronized (ticket.steps) {
                payload.put("steps", new ArrayList<>(ticket.steps));
            }
            List<Map<String, Object>> planList = new ArrayList<>();
            for (AgentPlanItem item : ticket.planBoard.items()) {
                Map<String, Object> p = new LinkedHashMap<>();
                p.put("id", item.getId());
                p.put("text", item.getText());
                p.put("status", item.getStatus());
                if (item.getNote() != null) {
                    p.put("note", item.getNote());
                }
                planList.add(p);
            }
            if (!planList.isEmpty()) {
                payload.put("plan", planList);
            }
            Map<String, Object> ext = new LinkedHashMap<>();
            ext.put("payload", payload);
            ticket.messageId = agentConversationService.appendAssistant(ticket.conversationId, content, status, ext);
        } catch (Exception e) {
            log.warn("问数终态对话补写失败（不影响问数）, conversationId={}, error={}",
                    ticket.conversationId, e.getMessage());
        }
    }

    /** 清理过期的已完成票据 */
    private void evictExpiredTickets() {
        long now = System.currentTimeMillis();
        askTickets.entrySet().removeIf(e -> {
            AskTicket t = e.getValue();
            return !"running".equals(t.status) && t.finishedAt > 0 && now - t.finishedAt > TICKET_RETAIN_MS;
        });
    }

    /** 问数票据：后台编排逐条写入 step/结果/错误，前端 2s 增量轮询消费 */
    private static class AskTicket {
        final List<String> steps = Collections.synchronizedList(new ArrayList<>());
        /** 历史对话标识（提问即存时创建；空=落盘失败不补写） */
        volatile String conversationId;
        /** 终态助手回复消息标识（补写成功回填，随轮询下发供前端评价定位） */
        volatile String messageId;
        /** running / done / error */
        volatile String status = "running";
        /** LLM 流式应答 live 进度（替换式，轮询随取随用） */
        volatile String live;
        volatile String resultJson;
        volatile String errorMessage;
        volatile boolean cancelled;
        volatile long finishedAt;
        /** 当前等待作答的歧义确认问题（askUser 工具挂上、askPoll 携带下发、askAnswer 唤醒）；null=无待答问题 */
        volatile ClarifyQuestion pendingClarify;
        /** 计划待办板（框架通用状态机：submit_plan 提交、done_plan_item 挑勾，askPoll 随轮询下发前端勾选清单） */
        final PlanBoard planBoard = new PlanBoard(new ArrayList<>(), null);
    }

    /** 歧义确认问题（ask_user 提问、票据携带、前端选项卡作答） */
    private static class ClarifyQuestion {
        final String id;
        final String question;
        final List<String> options;
        /** 用户作答文本；null=未作答或「交由 AI 决定」 */
        volatile String answer;
        /** 已作答（含交由 AI） */
        volatile boolean answered;

        ClarifyQuestion(String question, List<String> options) {
            this.id = UUID.randomUUID().toString().replace("-", "");
            this.question = question;
            this.options = options;
        }
    }

    private final Map<String, AskTicket> askTickets = new ConcurrentHashMap<>();

    /** 维护问数编排主体（同步/流式共用）；progress 为阶段进度回调，同步版传空实现；
     *  live 为 LLM 流式应答实时进度（替换式展示），同步版传空实现；
     *  ticket 非空（提交+轮询链）时解析链注册 askUser 歧义确认工具并拼歧义提示 */
    private SmartQueryAskRes askInternal(String agentCode, String question, String chartMode,
                                         Consumer<String> progress, Consumer<String> live, AskTicket ticket) {
        if (FuncUtil.isEmpty(agentCode) || FuncUtil.isEmpty(question)) {
            throw new NoticeException("agentCode 与问题均不能为空");
        }
        InsightAgent agent = insightAgentService.selectOne(
                new QueryWrapper<InsightAgent>().eq("agent_code", agentCode));
        if (agent == null) {
            throw new NoticeException("Agent [" + agentCode + "] 不存在");
        }
        // 敏感治理闸：LLM 对话会基于资产采样/作答，未完成逐表敏感声明时禁止
        if (!smartAgentMetaService.sensitiveGoverned(agentCode)) {
            throw new NoticeException("敏感治理未就绪：请先在 Agent 管理的「敏感字段」资产中逐表声明——"
                    + "每张表或标记敏感列（可配替换列）或确认「该表无敏感字段」，全部处理完再进行 LLM 问数对话");
        }
        Map<String, String> assets = loadAssets(agentCode);
        if (assets.isEmpty()) {
            throw new NoticeException("Agent [" + agentCode + "] 尚未生成并发布语义资产，请先在 Agent 管理中生成并发布");
        }
        SemanticLayer layer = SemanticLayer.fromContent(assets);
        String index = buildCatalogIndex(layer);
        ChatLanguageModel model = buildLiveModel(live, agent.getThinkingBudget());

        // 第一步：自然语言 → semantic_query（工具循环：索引定位 + 目录工具核实后组装）
        progress.accept("已加载发布资产，正在 LLM 解析自然语言问题…");
        AgentLoopOptions parseOpt = new AgentLoopOptions(MAX_PARSE_ROUNDS, PARSE_MEMORY_WINDOW, CATALOG_PINNED_TOOLS);
        parseOpt.setBudgetSeconds(PARSE_BUDGET_SECONDS);
        parseOpt.setCachedTools(READONLY_CACHE_TOOLS);
        // 计划仪式轮不消耗轮次配额（票据链注册了计划工具；同步/SSE 链未注册，登记无副作用）
        parseOpt.setRoundExemptTools(AgentPlanTools.ROUND_EXEMPT_TOOLS);
        // askUser 阻塞等用户作答的时长不计入会话总预算（等用户归来不应立即触发超预算收口）
        parseOpt.setBudgetExemptTools(Collections.singleton("askUser"));
        // 失败分类熔断：同类探索失败（列不存在/表范围/只读拒绝）累计 3 次注入拉直指令防死磕
        parseOpt.setFailureBreaker(new AgentFailureBreaker(AgentExploreTools.exploreFailureRules()));
        // 歧义确认通道：票据链注册 askUser（阻塞等用户作答）并拼确认纪律；同步/SSE 链无提问通道保持原样（LLM 自选口径）
        // 跨阶段事实台账：解析链核实的资产事实 + askUser 用户口径，注入维护/兜底子会话直接采信（禁止重复探索）
        List<String> facts = new ArrayList<>();
        // 各阶段发出提示词台账（阶段 → 全文）：随应答下发前端调试区展示/复制，不落历史对话
        Map<String, String> promptTrail = new LinkedHashMap<>();
        List<Object> parseTools = new ArrayList<>(Collections.singletonList(new SemanticCatalogTools(layer, facts::add)));
        String parsePrompt = buildParsePrompt(agentCode, index, question);
        if (ticket != null) {
            parseTools.add(new AgentAskUserTool((q, opts) -> {
                String guidance = awaitClarify(ticket, q, opts);
                facts.add(briefFact("[askUser] " + q + " → " + guidance));
                return guidance;
            }));
            parseTools.add(new AgentPlanTools(ticket.planBoard, null, null));
            parsePrompt = parsePrompt + "\n\n" + askPrompt("clarifySuffix")
                    + AgentPlanTools.planDiscipline(askPrompt("planExample"));
        }
        // 无 system 槽（该链无框架纪律可发）；业务提示拼入 user 首部（system 槽不沾业务）
        String parseFull = askPrompt("parseBusiness").trim() + "\n\n" + parsePrompt;
        promptTrail.put("解析", parseFull);
        AgentLoopResult parseLoop = toolAgentRunner.run(model, null,
                parseFull, parseTools, parseOpt, loopListener(progress));
        if (parseLoop.isStopped()) {
            throw new NoticeException("问数已停止");
        }
        JsonNode sqNode = extractSemanticQuery(parseLoop.getText());
        stampAgent(sqNode, agentCode);

        layers.bind(agentCode);
        try {
            progress.accept("解析完成，正在已发布资产上校验…");
            String ctx = writeJson(sqNode);
            SmartQueryResult dry = smartQueryService.dryRun(ctx);
            if (dry.isValid()) {
                // 已发布资产直接命中：执行作答，无建议（执行失败回 LLM 自纠修复后重试）
                progress.accept("校验通过，正在执行查询…");
                return withPrompts(finishAnswerWithRepair(model, layer, agentCode, index, question, chartMode,
                        false, sqNode, progress, promptTrail), promptTrail);
            }

            // 第二步：LLM 维护（探索工具核实 + 资产建议 + 改写查询）
            log.info("Agent '{}' 问数校验失败，进入 LLM 维护：{}", agentCode, issueText(dry));
            progress.accept("现有资产不足，正在 LLM 探索数据并建议资产（可能需 1-2 分钟）…");
            DataSource pool = dataSourceCacheService.getDataSource(agent.getDsName());
            String maintainAnswer = maintainWithTools(model, pool::getConnection, agentCode, layer,
                    buildMaintainPrompt(agentCode, index, question, issueText(dry)) + factsSuffix(facts),
                    progress, facts, promptTrail);
            JsonNode root = om.readTree(extractJson(maintainAnswer));
            JsonNode additions = root.path("asset_additions");
            JsonNode newSq = root.path("semantic_query");
            if (!additions.isObject() || additions.size() == 0) {
                SmartQueryAskRes esc = tryEscape(model, pool::getConnection, agentCode, layer, question, facts,
                        "LLM 维护未产出任何资产建议", progress, promptTrail);
                if (esc != null) {
                    return withPrompts(esc, promptTrail);
                }
                return withPrompts(failRes(dry, "LLM 维护未产出任何资产建议，无法回答该问题"), promptTrail);
            }
            if (!newSq.isObject() || newSq.size() == 0) {
                SmartQueryAskRes esc = tryEscape(model, pool::getConnection, agentCode, layer, question, facts,
                        "LLM 维护输出缺少 semantic_query", progress, promptTrail);
                if (esc != null) {
                    return withPrompts(esc, promptTrail);
                }
                return withPrompts(failRes(dry, "LLM 维护输出缺少 semantic_query，无法回答该问题"), promptTrail);
            }
            stampAgent(newSq, agentCode);

            // 第三步：临时语义层（已发布 + 建议项）一次性查询
            progress.accept("资产建议已产出，正在构建临时语义层并重新校验…");
            Map<String, String> merged = mergeAdditions(assets, additions);
            SemanticLayer tempLayer;
            try {
                tempLayer = SemanticLayer.fromContent(merged);
            } catch (Exception e) {
                log.warn("Agent '{}' 建议资产构建临时语义层失败", agentCode, e);
                SmartQueryAskRes esc = tryEscape(model, pool::getConnection, agentCode, layer, question, facts,
                        "建议资产不合法: " + e.getMessage(), progress, promptTrail);
                if (esc != null) {
                    return withPrompts(esc, promptTrail);
                }
                return withPrompts(failRes(dry, "建议资产不合法: " + e.getMessage()), promptTrail);
            }
            layers.bindOverride(tempLayer);
            String ctx2 = writeJson(newSq);
            SmartQueryResult dry2 = smartQueryService.dryRun(ctx2);
            if (!dry2.isValid()) {
                SmartQueryAskRes esc = tryEscape(model, pool::getConnection, agentCode, layer, question, facts,
                        "补齐建议资产后仍未通过校验：" + issueText(dry2), progress, promptTrail);
                if (esc != null) {
                    return withPrompts(esc, promptTrail);
                }
                return withPrompts(failRes(dry2, "补齐建议资产后仍未通过校验"), promptTrail);
            }
            progress.accept("重新校验通过，正在执行查询…");
            SmartQueryAskRes res = finishAnswerWithRepair(model, tempLayer, agentCode, index, question,
                    chartMode, true, newSq, progress, promptTrail);
            if (res.getErrorMessage() == null) {
                // 作答成功后才落待审提案（查询失败不建议入库）
                progress.accept("查询完成，正在落待审变更提案…");
                String batchNo = agentCode + "-" + System.currentTimeMillis();
                int count = proposalService.saveProposals(agentCode, batchNo, question, ctx2, additions,
                        root.path("reasons"), layer);
                res.setBatchNo(batchNo);
                res.setProposedCount(count);
                log.info("Agent '{}' 维护问数完成：基于 {} 项临时建议作答，已落待审提案", agentCode, count);
            }
            return withPrompts(res, promptTrail);
        } catch (NoticeException e) {
            throw e;
        } catch (Exception e) {
            log.error("Agent '{}' 维护问数失败", agentCode, e);
            throw new NoticeException("维护问数失败: " + e.getMessage());
        } finally {
            layers.clearOverride();
            layers.clear();
        }
    }

    /** 应答盖章各阶段发出提示词台账（前端调试区展示/复制供排查；台账空则不盖） */
    private static SmartQueryAskRes withPrompts(SmartQueryAskRes res, Map<String, String> promptTrail) {
        if (res != null && !promptTrail.isEmpty()) {
            res.setPrompts(new LinkedHashMap<>(promptTrail));
        }
        return res;
    }

    /** 执行失败（如未知列）的 LLM 修复上限：执行错误回给模型改写 semantic_query，
     *  重校验后重试；超上限收口错误应答（封顶防长任务烧钱） */
    private static final int MAX_EXEC_REPAIRS = 2;

    /** 执行错误文案截断上限（防长错误堆栈撑爆修复提示词） */
    private static final int REPAIR_ERR_MAX_LEN = 500;

    /** 校验通过的查询执行作答 + 失败自纠：执行抛错（如 Unknown column）时把执行错误
     *  回给 LLM（小工具循环，可用目录工具核实正确名称）改写 semantic_query，重校验后重试，
     *  最多 MAX_EXEC_REPAIRS 次；校验类失败（!isValid）不走修复（应经维护链路），仍收口错误清单。
     *  layer 为校验所用语义层（直接命中传发布层，临时层调用点传 tempLayer），目录工具看到同一套资产 */
    private SmartQueryAskRes finishAnswerWithRepair(ChatLanguageModel model, SemanticLayer layer,
                                                    String agentCode, String index, String question,
                                                    String chartMode, boolean usedProposals,
                                                    JsonNode sqNode, Consumer<String> progress,
                                                    Map<String, String> promptTrail) {
        SmartQueryResult dry = smartQueryService.dryRun(writeJson(sqNode));
        SmartQueryAskRes res = finishAnswer(sqNode, dry, chartMode, usedProposals, progress);
        for (int attempt = 1; execFailed(res) && attempt <= MAX_EXEC_REPAIRS; attempt++) {
            progress.accept("查询执行失败（" + res.getErrorMessage() + "），正在 LLM 修复重试（"
                    + attempt + "/" + MAX_EXEC_REPAIRS + "）…");
            log.info("Agent '{}' 问数执行失败，LLM 修复第 {}/{} 次：{}", agentCode, attempt,
                    MAX_EXEC_REPAIRS, res.getErrorMessage());
            JsonNode fixed;
            try {
                AgentLoopOptions fixOpt = new AgentLoopOptions(MAX_REPAIR_ROUNDS, REPAIR_MEMORY_WINDOW, CATALOG_PINNED_TOOLS);
                fixOpt.setBudgetSeconds(REPAIR_BUDGET_SECONDS);
                fixOpt.setCachedTools(READONLY_CACHE_TOOLS);
                String repairFull = askPrompt("repairBusiness").trim() + "\n\n"
                        + buildRepairPrompt(index, question, res.getErrorMessage(), sqNode);
                promptTrail.put("修复#" + attempt, repairFull);
                AgentLoopResult fixLoop = toolAgentRunner.run(model, null,
                        repairFull,
                        Collections.singletonList(new SemanticCatalogTools(layer)),
                        fixOpt, loopListener(progress));
                fixed = extractSemanticQuery(fixLoop.getText() == null ? "" : fixLoop.getText());
                stampAgent(fixed, agentCode);
                dry = smartQueryService.dryRun(writeJson(fixed));
                if (!dry.isValid()) {
                    progress.accept("修复后的查询未通过校验，收口错误应答");
                    return failRes(dry, "LLM 修复后仍未通过校验：" + issueText(dry));
                }
            } catch (NoticeException e) {
                throw e;
            } catch (Exception e) {
                log.warn("Agent '{}' LLM 修复问数失败（第 {} 次），收口原错误：{}",
                        agentCode, attempt, e.getMessage());
                break;
            }
            sqNode = fixed;
            res = finishAnswer(sqNode, dry, chartMode, usedProposals, progress);
        }
        return res;
    }

    /** 执行抛错（finishAnswer catch 分支，错误文案以固定前缀开头）才走修复；
     *  校验类失败（errors 清单）与执行层结构错误不走修复 */
    private static boolean execFailed(SmartQueryAskRes res) {
        return res.getErrorMessage() != null && res.getErrorMessage().startsWith("数据查询执行失败");
    }

    /** 执行错误修复提示词（模板见 prompts-ask.yml#repairPrompt）：带精简索引与失败现场，
     *  明细经目录工具拉取，只要求输出修正后的 semantic_query */
    private String buildRepairPrompt(String index, String question, String execError, JsonNode sqNode) {
        String err = execError == null ? "" : execError.trim();
        if (err.length() > REPAIR_ERR_MAX_LEN) {
            err = err.substring(0, REPAIR_ERR_MAX_LEN) + "…";
        }
        return askPrompt("repairPrompt")
                .replace("{question}", question)
                .replace("{error}", err)
                .replace("{semantic_query}", writeJson(sqNode))
                .replace("{asset_index}", index);
    }

    /** 校验通过的查询执行作答：内联返回 rows/statistics（临时层答案无法走 statistic 端点二次取数）；
     *  progress 将执行 SQL 透入过程流（思考卡片可见 LLM 最终执行的 SQL，失败后仍留存可排查） */
    private SmartQueryAskRes finishAnswer(JsonNode sqNode, SmartQueryResult dry,
                                          String chartModeReq, boolean usedProposals,
                                          Consumer<String> progress) {
        SmartQueryAskRes res = new SmartQueryAskRes();
        res.setValid(true);
        res.setWarnings(dry.getWarnings());
        res.setSql(dry.getSql());
        res.setParams(dry.getParams());
        res.setNotes(dry.getNotes());
        res.setUsedProposals(usedProposals);
        res.setSemanticQuery(writeJson(sqNode));

        SemanticQuery sq = parser.parse(writeJson(sqNode)).getQuery();
        String chartMode = FuncUtil.isNotEmpty(chartModeReq) ? chartModeReq
                : indicatorDeriver.inferChartMode(sq);
        String engineMode = "rankingBar".equals(chartMode) ? "ranking" : "standard";
        // 执行 SQL 上屏：不知道 LLM 让执行什么 SQL 就无法排查执行失败（如 unknown column）
        progress.accept("执行 SQL：" + String.valueOf(dry.getSql()).replaceAll("\\s+", " ").trim());
        SmartQueryResult r;
        try {
            r = smartQueryService.run(writeJson(sqNode), true, engineMode);
        } catch (Exception e) {
            // 执行失败（如未知列）收口为结构化错误应答（携带 sql/semantic_query），
            // 前端调试区可排查，而非抛异常只剩一句错误文案
            log.warn("smart-query 执行失败（SQL 已随应答留存可排查）：{}", e.getMessage());
            res.setValid(false);
            res.setErrorMessage("数据查询执行失败: " + e.getMessage());
            return res;
        }
        if (!r.isValid()) {
            res.setValid(false);
            res.setErrors(r.getErrors());
            res.setErrorMessage(r.getErrors().isEmpty() ? "执行校验未通过" : r.getErrors().get(0).getMessage());
            return res;
        }
        if (FuncUtil.isNotEmpty(r.getErrorMessage())) {
            res.setErrorMessage(r.getErrorMessage());
            return res;
        }
        if (r.getColumns() != null) {
            r.getColumns().forEach(c -> {
                SmartQueryAskRes.ColumnInfo col = new SmartQueryAskRes.ColumnInfo();
                col.setAlias(c.getAlias());
                col.setKind(c.getKind());
                col.setDisplay(c.getDisplay());
                res.getColumns().add(col);
            });
        }
        if (r.getRows() != null) {
            for (List<Object> row : r.getRows()) {
                Map<String, Object> map = new LinkedHashMap<>();
                for (int i = 0; i < res.getColumns().size() && i < row.size(); i++) {
                    map.put(res.getColumns().get(i).getAlias(), row.get(i));
                }
                res.getRows().add(map);
            }
        }
        res.setStatistics(statisticConverter.convert(r.getPayload()));
        return res;
    }

    /** 维护失败响应：保留最近一次校验的错误清单 + 汇总说明 */
    private SmartQueryAskRes failRes(SmartQueryResult r, String summary) {
        SmartQueryAskRes res = new SmartQueryAskRes();
        res.setValid(false);
        res.setErrors(r.getErrors());
        res.setWarnings(r.getWarnings());
        res.setSql(r.getSql());
        res.setErrorMessage(summary);
        return res;
    }

    // ────────────────────────── LLM 维护 ──────────────────────────

    /** 维护探索业务提示（模板见 prompts-ask.yml#maintainBusiness；原 AiServices 代理的
     *  @SystemMessage 内容平移），拼入 user 提示词首部；含引擎能力口径清单（代码常量必达，
     *  不受 DB 草稿提示词覆盖影响）：跨表算术已支持但必须以 composite 资产形式定义，防直写双 source_tables；
     *  行级权限走 row-policies 资产渲染期注入，不允许指标/过滤器里手工拼用户隔离条件 */

    /** 多轮工具探索维护：通用引擎驱动工具循环（摆脱 AiServices 0.33 内置 10 轮硬上限）；
     *  模型/端点不支持工具或探索超限时引擎内部回落直连（与资产生成链路同策略）。
     *  progress 逐条透出工具循环过程日志（LLM 轮次/工具调用/返回摘要），流式版即前端思考过程时间线，
     *  避免 1-2 分钟探索期用户傻等；同步版传空实现无副作用；
     *  promptTrail 记录本阶段发出提示词全文（前端调试区展示/复制） */
    private String maintainWithTools(ChatLanguageModel model, AgentExploreTools.ConnSupplier connSupplier, String agentCode,
                                    SemanticLayer layer, String prompt, Consumer<String> progress,
                                    List<String> facts, Map<String, String> promptTrail) {
        AgentExploreTools tools = new AgentExploreTools(connSupplier, agentCode, layer.entities(),
                (e, c, l) -> "跳过：编码↔名称配对登记仅用于资产生成阶段，请在 asset_additions.value_domains 中直接给出码值域",
                progress, null, facts::add);
        // 目录检索工具与 DB 探索工具并存：查资产明细走前者，核实真实数据走后者
        AgentLoopOptions maintainOpt = new AgentLoopOptions(MAX_MAINTAIN_ROUNDS, MAINTAIN_MEMORY_WINDOW, CATALOG_PINNED_TOOLS);
        maintainOpt.setBudgetSeconds(MAINTAIN_BUDGET_SECONDS);
        maintainOpt.setCachedTools(READONLY_CACHE_TOOLS);
        String maintainFull = askPrompt("maintainBusiness").trim() + "\n\n" + prompt
                + "\n\n" + askPrompt("toolModeSuffix").trim();
        promptTrail.put("维护", maintainFull);
        AgentLoopResult result = toolAgentRunner.run(model, null,
                maintainFull,
                Arrays.asList(new SemanticCatalogTools(layer), tools), maintainOpt, loopListener(progress));
        if (result.isStopped()) {
            throw new IllegalStateException("维护探索被中断");
        }
        return result.getText();
    }

    /** 兜底 SQL 通道业务提示（模板见 prompts-ask.yml#escapeBusiness；逃生口：语义层协议
     *  表达不了时的最后一条路），拼入 user 提示词首部 */

    /** 兜底 SQL 通道（逃生口）：语义层两轮校验均失败且维护无产出/建议不合法时，让 LLM 基于已核实事实
     *  直接产出守卫审查过的只读 SELECT 作答，不再整单失败。服务端复用 AgentExploreTools 同一守卫执行
     *  （只读校验/表白名单/强制限行），应答 notes 标注「未经语义层认证」；产出或执行失败重试一次，
     *  仍失败返回 null 由调用方回落原失败应答（逃生失败不掩盖原始错误）；
     *  promptTrail 记录本阶段发出提示词全文（前端调试区展示/复制） */
    private SmartQueryAskRes tryEscape(ChatLanguageModel model, AgentExploreTools.ConnSupplier connSupplier, String agentCode,
                                       SemanticLayer layer, String question, List<String> facts,
                                       String reason, Consumer<String> progress,
                                       Map<String, String> promptTrail) {
        try {
            progress.accept("语义层通道未走通，启用 SQL 兜底通道（结果不经语义层认证）…");
            log.info("Agent '{}' 问数进入 SQL 兜底通道：{}", agentCode, reason);
            AgentExploreTools tools = new AgentExploreTools(connSupplier, agentCode, layer.entities(),
                    (e, c, l) -> "跳过：兜底通道不登记码值配对", progress, null, facts::add);
            String basePrompt = buildEscapePrompt(question, facts, reason, layer);
            String prompt = basePrompt;
            String lastError = null;
            for (int attempt = 1; attempt <= 2; attempt++) {
                AgentLoopOptions escOpt = new AgentLoopOptions(MAX_ESCAPE_ROUNDS, ESCAPE_MEMORY_WINDOW, CATALOG_PINNED_TOOLS);
                escOpt.setBudgetSeconds(ESCAPE_BUDGET_SECONDS);
                escOpt.setCachedTools(READONLY_CACHE_TOOLS);
                escOpt.setFailureBreaker(new AgentFailureBreaker(AgentExploreTools.exploreFailureRules()));
                String escapeFull = askPrompt("escapeBusiness").trim() + "\n\n" + prompt;
                promptTrail.put("兜底", escapeFull);
                AgentLoopResult loop = toolAgentRunner.run(model, null,
                        escapeFull,
                        Collections.singletonList(tools), escOpt, loopListener(progress));
                if (loop.isStopped()) {
                    throw new NoticeException("问数已停止");
                }
                JsonNode root;
                try {
                    root = om.readTree(extractJson(loop.getText()));
                } catch (Exception e) {
                    lastError = "兜底输出不是合法 JSON";
                    prompt = basePrompt + "\n\n【上次输出问题】" + lastError + "，请只输出一个 ```json 代码块。";
                    continue;
                }
                String sql = root.path("sql").asText(null);
                if (FuncUtil.isEmpty(sql)) {
                    lastError = "兜底输出缺少 sql 字段";
                    prompt = basePrompt + "\n\n【上次输出问题】" + lastError + "。";
                    continue;
                }
                try {
                    List<Map<String, Object>> rows = tools.runGuardedSelect(sql);
                    progress.accept("SQL 兜底通道执行成功（返回 " + rows.size() + " 行，未经语义层认证）");
                    // 结晶信号采集：兜底成功即入台账（高频重复的 question/sql 形态 = 下一个引擎能力候选）
                    escapeLogService.record(agentCode, question, sql, root.path("note").asText(null));
                    return escapeRes(root, sql, rows);
                } catch (Exception e) {
                    lastError = e.getMessage();
                    log.warn("Agent '{}' 兜底 SQL 第 {} 次执行失败：{}", agentCode, attempt, lastError);
                    prompt = basePrompt + "\n\n【上次产出的 SQL 失败】" + lastError
                            + "\n请修正（表名用 db.tbl 全名、列名先核实）后重新输出完整 JSON。";
                }
            }
            progress.accept("SQL 兜底通道未成功（" + lastError + "），收口失败应答");
            return null;
        } catch (NoticeException e) {
            throw e;
        } catch (Exception e) {
            log.warn("Agent '{}' SQL 兜底通道异常，回落失败应答", agentCode, e);
            return null;
        }
    }

    /** 兜底通道应答组装：以服务端守卫执行的 SQL 实际列序为准（LLM 给的 columns 仅补中文显示名/kind），
     *  notes 标注未经语义层认证与口径说明 */
    private SmartQueryAskRes escapeRes(JsonNode root, String sql, List<Map<String, Object>> rows) {
        SmartQueryAskRes res = new SmartQueryAskRes();
        res.setValid(true);
        res.setSql(sql);
        res.getNotes().add("SQL 兜底通道作答：未经语义层认证，不产生资产提案");
        String note = root.path("note").asText(null);
        if (FuncUtil.isNotEmpty(note)) {
            res.getNotes().add(note);
        }
        List<String> colOrder = rows.isEmpty() ? new ArrayList<>() : new ArrayList<>(rows.get(0).keySet());
        Map<String, JsonNode> colMeta = new LinkedHashMap<>();
        JsonNode cols = root.path("columns");
        if (cols.isArray()) {
            for (JsonNode c : cols) {
                String nm = c.path("name").asText("");
                if (FuncUtil.isNotEmpty(nm)) {
                    colMeta.put(nm, c);
                }
            }
        }
        if (colOrder.isEmpty()) {
            // 0 行结果：列序无从得知，退用 LLM 声明的列清单
            if (cols.isArray()) {
                for (JsonNode c : cols) {
                    SmartQueryAskRes.ColumnInfo ci = new SmartQueryAskRes.ColumnInfo();
                    ci.setAlias(c.path("name").asText("?"));
                    ci.setKind(c.path("kind").asText("dimension"));
                    String display = c.path("display").asText(null);
                    ci.setDisplay(FuncUtil.isEmpty(display) ? ci.getAlias() : display);
                    res.getColumns().add(ci);
                }
            }
        } else {
            for (String col : colOrder) {
                SmartQueryAskRes.ColumnInfo ci = new SmartQueryAskRes.ColumnInfo();
                ci.setAlias(col);
                JsonNode c = colMeta.get(col);
                ci.setKind(c == null ? "dimension" : c.path("kind").asText("dimension"));
                String display = c == null ? null : c.path("display").asText(null);
                ci.setDisplay(FuncUtil.isEmpty(display) ? col : display);
                res.getColumns().add(ci);
            }
        }
        res.getRows().addAll(rows);
        return res;
    }

    /** 兜底通道用户提示（模板见 prompts-ask.yml#escapePrompt）：问题 + 语义层通道失败原因
     *  + 实体字段清单（表引用白名单） + 已核实事实 + 输出协议 */
    private String buildEscapePrompt(String question, List<String> facts, String reason, SemanticLayer layer) {
        StringBuilder entities = new StringBuilder();
        for (EntityDef ent : layer.entities()) {
            StringBuilder fl = new StringBuilder();
            if (ent.getFields() != null) {
                for (EntityDef.EntityFieldDef f : ent.getFields()) {
                    if (fl.length() > 0) {
                        fl.append("、");
                    }
                    fl.append(f.getName()).append('(').append(nvl(f.getType())).append(' ')
                            .append(nvl(f.getDisplayName())).append(')');
                }
            }
            entities.append("- ").append(ent.getTable()).append("（").append(nvl(ent.getDisplayName())).append("）：")
                    .append(fl).append('\n');
        }
        StringBuilder factsBlock = new StringBuilder();
        if (!facts.isEmpty()) {
            factsBlock.append("【已核实事实】（已核实为真直接采信，禁止重复探索核实；[askUser] 行为用户确认口径必须遵守）\n");
            int n = 0;
            for (String f : facts) {
                if (n++ >= FACTS_MAX_LINES) {
                    factsBlock.append("- …（其余略）\n");
                    break;
                }
                factsBlock.append("- ").append(f).append('\n');
            }
        }
        return askPrompt("escapePrompt")
                .replace("{question}", question)
                .replace("{reason}", reason)
                .replace("{entities}", entities.toString().trim())
                .replace("{facts}", factsBlock.toString().trim());
    }

    /** 已核实事实段（注入维护子会话；DB 草稿覆盖提示词时同样在末尾追加生效，事实必达） */
    private String factsSuffix(List<String> facts) {
        if (facts.isEmpty()) {
            return "";
        }
        StringBuilder sb = new StringBuilder("\n\n【解析阶段已核实事实】（已核实为真，直接采信，禁止再调用工具重复核实；")
                .append("其中 [askUser] 行为用户确认口径，必须遵守）\n");
        int n = 0;
        for (String f : facts) {
            if (n++ >= FACTS_MAX_LINES) {
                sb.append("- …（其余略）\n");
                break;
            }
            sb.append("- ").append(f).append('\n');
        }
        return sb.toString();
    }

    /** 事实行压缩截断（压空白 + 截断，台账体量控制） */
    private static String briefFact(String s) {
        if (s == null) {
            return "";
        }
        String one = s.replaceAll("\\s+", " ").trim();
        return one.length() > FACT_LINE_MAX_LEN ? one.substring(0, FACT_LINE_MAX_LEN) + "…" : one;
    }

    private ChatLanguageModel requireModel() {
        ChatLanguageModel model = chatModelProvider.getIfUnique();
        if (model == null) {
            throw new NoticeException("未配置 LLM 模型（llm.* 或系统参数），无法维护问数");
        }
        return model;
    }

    /** Agent 长任务用途流式模型 + 实时进度门面（llm 框架 LiveModelFactory 装配）：AGENT 用途
     *  独立更长超时（系统参数「Agent长任务超时(秒)」默认 600，热生效）——默认用途 120s 对数十秒
     *  至数分钟的单次调用过短，且流式模型同样受 callTimeout 全调用上限截断；静默窗内向前端
     *  live 行推「思考中·已思 N 字」（reasoning_content 实时上屏）与「应答中·已收 N 字」；
     *  流式/同步回落/降级语义详见 LiveModelFactory；无 Provider 时回落同步模型（无 live 进度）。
     *  思考强度按 Agent 配置传值（thinking_budget：空=最强不限制） */
    private ChatLanguageModel buildLiveModel(Consumer<String> live, Integer thinkingBudget) {
        return liveModelFactory.build(DbAwareModelConfigProvider.PURPOSE_AGENT, live, this::requireModel,
                thinkingBudget);
    }

    // ────────────────────────── 自主维护 agent 会话（B3） ──────────────────────────

    /** 自主维护阶段键（与 defineStages 声明一致） */
    private static final String MS_STAGE_PREPARE = "prepare";
    private static final String MS_STAGE_EXPLORE = "explore";
    private static final String MS_STAGE_VERIFY = "verify";
    private static final String MS_STAGE_PROPOSE = "propose";
    private static final String MS_STAGE_ANSWER = "answer";

    /** 自主维护 agent 轮次上限（计划 B3） */
    private static final int AGENT_MAX_ROUNDS = 30;
    /** 结论摘要最大长度 */
    private static final int SUMMARY_MAX_LEN = 600;

    /** 自主维护业务提示（模板见 prompts-ask.yml#agentBusiness）：工具闭环工作流
     *  （探索→组装自纠→真数据验证→提案→作答），拼入 user 提示词首部 */

    /**
     * 自主维护问数 agent 会话体（MaintainQueryAgentDefinition 调用）：一次 LLM 自主规划闭环
     * （探索缺口 → 组装+dryRun 自纠 → 真执行验证 → 提案落待审表 → 结论作答），
     * 阶段随工具调用推进（logSink 按工具类别驱动，边执行边跳动），
     * 暂停/恢复/停止经 ctx（会话层，跨实例可达）；用户停止拋 InterruptedException 由会话层收口 STOPPED
     */
    public String runAgentSession(AgentSessionContext ctx, String agentCode, String question)
            throws InterruptedException {
        InsightAgent agent = insightAgentService.selectOne(
                new QueryWrapper<InsightAgent>().eq("agent_code", agentCode));
        if (agent == null) {
            throw new NoticeException("Agent [" + agentCode + "] 不存在");
        }
        if (!smartAgentMetaService.sensitiveGoverned(agentCode)) {
            throw new NoticeException("敏感治理未就绪：请先在 Agent 管理的「敏感字段」资产中逐表声明后再进行 LLM 问数对话");
        }
        Map<String, String> assets = loadAssets(agentCode);
        if (assets.isEmpty()) {
            throw new NoticeException("Agent [" + agentCode + "] 尚未生成并发布语义资产，请先在 Agent 管理中生成并发布");
        }
        ctx.defineStages(
                AgentStage.of(MS_STAGE_PREPARE, "加载资产与上下文", 10),
                AgentStage.of(MS_STAGE_EXPLORE, "探索数据与资产缺口", 300),
                AgentStage.of(MS_STAGE_VERIFY, "组装查询并真数据验证", 120),
                AgentStage.of(MS_STAGE_PROPOSE, "落待审提案", 30),
                AgentStage.of(MS_STAGE_ANSWER, "结论作答", 30));
        ctx.stageStart(MS_STAGE_PREPARE, null);
        SemanticLayer layer = SemanticLayer.fromContent(assets);
        String index = buildCatalogIndex(layer);
        ChatLanguageModel model = requireModel();
        String batchNo = agentCode + "-agent-" + System.currentTimeMillis();
        ctx.stageDone(MS_STAGE_PREPARE, "实体 " + layer.entities().size()
                + " / 指标 " + layer.metricMap().size()
                + " / 维度 " + layer.dimensionMap().size());

        // 阶段感知日志：按工具类别推进阶段（explore→verify→propose 顺序流转，前段自动置 done）
        Consumer<String> stageLog = line -> {
            ctx.log(line);
            if (line.contains("describe_table") || line.contains("sample_rows")
                    || line.contains("group_by_field") || line.contains("run_sql")) {
                touchStage(ctx, MS_STAGE_EXPLORE);
            } else if (line.contains("build_query") || line.contains("execute_query")) {
                touchStage(ctx, MS_STAGE_VERIFY);
            } else if (line.contains("propose_asset") || line.contains("list_proposals")) {
                touchStage(ctx, MS_STAGE_PROPOSE);
            }
        };

        layers.bind(agentCode);
        DataSource agentPool;
        try {
            try {
                agentPool = dataSourceCacheService.getDataSource(agent.getDsName());
            } catch (Exception e) {
                throw new NoticeException("问数数据源连接失败: " + e.getMessage());
            }
            AgentExploreTools explore = new AgentExploreTools(agentPool::getConnection, agentCode, layer.entities(),
                    (e, c, l) -> "跳过：编码↔名称配对登记仅用于资产生成阶段，请直接在提案中给出码值域",
                    stageLog, ctx::isStopRequested);
            SemanticQueryTools query = new SemanticQueryTools(smartQueryService, agentCode,
                    stageLog, ctx::isStopRequested);
            AssetProposalTools propose = new AssetProposalTools(proposalService, agentCode, batchNo,
                    question, "", layer, stageLog, ctx::isStopRequested);
            // 业务提示拼入 user 首部（system 槽仅供框架级纪律；该链无框架纪律可发，无 system）
            String userPrompt = askPrompt("agentBusiness").trim() + "\n\n" + buildAgentPrompt(index, question);
            // 发出提示词全文留痕（debug）：事件流承载全文（前端预览可展开、复制全体不截断）
            ctx.log("【发出提示词·system】（无：业务提示已并入 user，system 槽仅供框架纪律）");
            ctx.log("【发出提示词·user】（" + userPrompt.length() + " 字）\n" + userPrompt);
            AgentLoopOptions agentOpt = new AgentLoopOptions(AGENT_MAX_ROUNDS, 60);
            agentOpt.setCachedTools(READONLY_CACHE_TOOLS);
            AgentLoopResult result = toolAgentRunner.run(model, null, userPrompt,
                    Arrays.asList(new SemanticCatalogTools(layer), explore, query, propose),
                    agentOpt, ctx.loopListener());
            if (result.isStopped()) {
                throw new InterruptedException("用户停止");
            }
            ctx.stageStart(MS_STAGE_ANSWER, null);
            String text = result.getText() == null ? "" : result.getText().trim();
            String summary = text.length() > SUMMARY_MAX_LEN
                    ? text.substring(0, SUMMARY_MAX_LEN) + "…" : text;
            ctx.setSummary(summary);
            ctx.stageDone(MS_STAGE_ANSWER, null);
            return summary;
        } finally {
            layers.clearOverride();
            layers.clear();
        }
    }

    /** 阶段推进：目标阶段 pending→running，之前的 running 阶段置 done（顺序前推，未走到的阶段由会话层收口置 skipped） */
    private void touchStage(AgentSessionContext ctx, String targetKey) {
        try {
            List<AgentStage> stages = ctx.getState().getStages();
            int targetIdx = -1;
            for (int i = 0; i < stages.size(); i++) {
                if (targetKey.equals(stages.get(i).getKey())) {
                    targetIdx = i;
                    break;
                }
            }
            if (targetIdx < 0) {
                return;
            }
            for (int i = 0; i < targetIdx; i++) {
                AgentStage s = stages.get(i);
                if (AgentStage.RUNNING.equals(s.getStatus())) {
                    ctx.stageDone(s.getKey(), null);
                }
            }
            AgentStage target = stages.get(targetIdx);
            if (AgentStage.PENDING.equals(target.getStatus())) {
                ctx.stageStart(targetKey, null);
            }
        } catch (Exception e) {
            log.debug("阶段推进失败（忽略）: {}", e.getMessage());
        }
    }

    /** 自主维护 agent 用户提示：资产精简索引 + 协议 + 问题（明细经目录检索工具拉取，与解析链协议口径一致） */
    private String buildAgentPrompt(String index, String question) {
        return "【语义层资产精简索引】（仅名称；字段/公式/码值明细用目录检索工具 searchAssets/findValue/describeEntity/metricDetail/dimensionDetail 查询）\n" + index + "\n\n"
                + "【semantic_query 协议】metric 查询：{\"query_type\":\"metric\",\"metrics\":[指标英文名],\"dimensions\":[维度英文名],"
                + "\"filters\":{...},\"time\":{...},\"order_by\":[...],\"limit\":n}；"
                + "list 查询：{\"query_type\":\"list\",\"entity\":\"实体英文名\",\"fields\":[实体字段名],\"filters\":{...},"
                + "\"order_by\":[...],\"limit\":n}。"
                + "filters 为条件树：叶子 {\"dimension\":\"维度英文名\",\"operator\":\"= | != | in | not_in | > | >= | < | <= | between | contains\","
                + "\"value\":值}；分组 {\"operator\":\"AND|OR\",\"conditions\":[...]}。码值过滤优先用码值域中的 code。\n\n"
                + "【用户问题】" + question;
    }

    // ────────────────────────── 提示词 ──────────────────────────

    /** 生效提示词：内置默认（prompts-ask.yml）+ llm-prompts 资产中的 parsePrompt/maintainPrompt 覆盖 */
    private Map<String, String> effectivePrompts(String agentCode) {
        Map<String, String> m = new LinkedHashMap<>();
        m.put("parsePrompt", askPrompt("parsePrompt"));
        m.put("maintainPrompt", askPrompt("maintainPrompt"));
        InsightAgentAsset asset = smartAgentMetaService.getAsset(agentCode,
                SmartAgentAssetGenerateService.PROMPTS_ASSET_TYPE);
        if (asset != null && FuncUtil.isNotEmpty(asset.getContent())) {
            try {
                JsonNode node = om.readTree(asset.getContent());
                String parse = node.path("parsePrompt").asText(null);
                String maintain = node.path("maintainPrompt").asText(null);
                if (FuncUtil.isNotEmpty(parse)) {
                    m.put("parsePrompt", parse);
                }
                if (FuncUtil.isNotEmpty(maintain)) {
                    m.put("maintainPrompt", maintain);
                }
            } catch (Exception e) {
                log.warn("Agent '{}' 提示词模板解析失败，维护问数使用内置默认", agentCode);
            }
        }
        return m;
    }

    private String buildParsePrompt(String agentCode, String index, String question) {
        return injectIndex(effectivePrompts(agentCode).get("parsePrompt"), index)
                .replace(PH_QUESTION, question);
    }

    private String buildMaintainPrompt(String agentCode, String index, String question, String errors) {
        return injectIndex(effectivePrompts(agentCode).get("maintainPrompt"), index)
                .replace(PH_QUESTION, question)
                .replace(PH_ERRORS, errors);
    }

    /** 索引注入（兼容旧草稿）：{asset_index} 新占位符优先；旧草稿的 {catalog_json} 同口径填索引；
     *  两者皆无时在提示词末尾追加索引，保证索引必达（不报错） */
    private String injectIndex(String template, String index) {
        String out = template.replace(PH_INDEX, index).replace(PH_CATALOG_LEGACY, index);
        if (!template.contains(PH_INDEX) && !template.contains(PH_CATALOG_LEGACY)) {
            out = out + "\n\n【资产精简索引】\n" + index;
        }
        return out;
    }

    /** 通用循环监听器：过程日志透传 progress（前端 steps 可见检索过程），不主动停止 */
    private AgentLoopListener loopListener(Consumer<String> progress) {
        return new AgentLoopListener() {
            @Override
            public void log(String line) {
                progress.accept(line);
            }

            @Override
            public boolean shouldStop() {
                return false;
            }
        };
    }

    // ────────────────────────── 资产目录与临时层 ──────────────────────────

    /** 当前生效资产 JSON：已发布缓存优先；仅内置 default Agent 允许回落 classpath /smartquery/，
     *  其余 Agent 只认 DB 发布资产（classpath 不再做回退，新 Agent 必须在管理页生成发布） */
    private Map<String, String> loadAssets(String agentCode) {
        Map<String, String> assets = agentAssetCacheService.assetsFor(agentCode);
        if (!assets.isEmpty()) {
            return assets;
        }
        if (!SemanticLayerRegistry.isDefault(agentCode)) {
            return Collections.emptyMap();
        }
        Map<String, String> m = new LinkedHashMap<>();
        for (String file : ASSET_FILES) {
            try (InputStream in = SmartQueryMaintainService.class.getResourceAsStream("/smartquery/" + file)) {
                if (in != null) {
                    m.put(file, new String(readAll(in), StandardCharsets.UTF_8));
                }
            } catch (Exception e) {
                log.warn("classpath 资产读取失败: /smartquery/{}", file, e);
            }
        }
        return m;
    }

    private byte[] readAll(InputStream in) throws Exception {
        java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
        byte[] buf = new byte[8192];
        int n;
        while ((n = in.read(buf)) > 0) {
            out.write(buf, 0, n);
        }
        return out.toByteArray();
    }

    /** 语义层精简索引（解析/维护/修复提示词常驻）：每资产一行名称级条目
     *  （实体 name|display|table、指标/维度 name|display、概念名），字段/公式/码值等明细
     *  由 SemanticCatalogTools 按需拉取；超 INDEX_MAX_LINES 截断并引导 searchAssets 检索 */
    private String buildCatalogIndex(SemanticLayer layer) {
        List<String> lines = new ArrayList<>();
        for (EntityDef ent : layer.entities()) {
            lines.add("实体 " + nvl(ent.getName()) + "|" + nvl(ent.getDisplayName()) + "|" + nvl(ent.getTable()));
        }
        for (MetricDef md : layer.metricMap().values()) {
            lines.add("指标 " + nvl(md.getName()) + "|" + nvl(md.getDisplayName()));
        }
        for (DimensionDef dd : layer.dimensionMap().values()) {
            if (isNoiseDim(dd.getName())) {
                // 索引降噪：物理 id 类维度无分组/过滤意义，不进常驻索引防 LLM 误选（明细仍可用目录工具拉取）
                continue;
            }
            String line = "维度 " + nvl(dd.getName()) + "|" + nvl(dd.getDisplayName());
            if (dd.getAliases() != null && !dd.getAliases().isEmpty()) {
                line += "|别名:" + String.join("/", dd.getAliases());
            }
            lines.add(line);
        }
        for (String concept : layer.conceptNames()) {
            lines.add("概念 " + nvl(concept));
        }
        if (lines.isEmpty()) {
            return "（无资产）";
        }
        if (lines.size() > INDEX_MAX_LINES) {
            List<String> cut = new ArrayList<>(lines.subList(0, INDEX_MAX_LINES));
            cut.add("…（索引已截断，其余资产请用 searchAssets 工具检索）");
            lines = cut;
        }
        return String.join("\n", lines);
    }

    /** 索引降噪判定：物理 id 类维度（主键/外键数字 id）对用户问数无分组/过滤意义 */
    private static boolean isNoiseDim(String name) {
        if (name == null) {
            return false;
        }
        String lower = name.toLowerCase(java.util.Locale.ROOT);
        return "id".equals(lower) || lower.endsWith("_id");
    }

    private static String nvl(String s) {
        return s == null ? "" : s;
    }

    /** 建议项合入资产 JSON map → 临时语义层素材（数组 append、码值域按键 put、概念/敏感字段数组合入）；
     *  合入后 metrics supported_dimensions 全量重展开（存量指标也跟随新增维度/关系） */
    private Map<String, String> mergeAdditions(Map<String, String> assets, JsonNode additions) {
        Map<String, String> merged = new LinkedHashMap<>(assets);
        fillMetricDefaults(additions.path("metrics"));
        appendToArrayList(merged, "metrics.json", additions.path("metrics"));
        appendToArrayList(merged, "dimensions.json", additions.path("dimensions"));
        appendToArrayList(merged, "relations.json", additions.path("relations"));
        appendToArrayList(merged, "concepts.json", additions.path("concepts"), "concepts",
                "{\"schema_version\":\"1.0\",\"concepts\":[]}");
        appendToArrayList(merged, "sensitive-fields.json", additions.path("sensitive_fields"), "fields",
                "{\"schema_version\":\"1.0\",\"fields\":[]}");
        JsonNode vd = additions.path("value_domains").path("domains");
        if (vd.isObject() && vd.size() > 0) {
            ObjectNode base = readTreeOr(merged.get("value-domains.json"), om.createObjectNode());
            ObjectNode domains = base.has("domains") && base.get("domains").isObject()
                    ? (ObjectNode) base.get("domains") : base.putObject("domains");
            Iterator<Map.Entry<String, JsonNode>> it = vd.fields();
            while (it.hasNext()) {
                Map.Entry<String, JsonNode> e = it.next();
                domains.set(e.getKey(), normalizeDomain(e.getValue()));
            }
            merged.put("value-domains.json", writeJson(base));
        }
        // 全量重展开（存量+新增指标）：旧 expandAdditionMetrics 只展开新增指标，append 新维度后
        // 存量指标清单不跟随，临时层 §6.2.2 必判不走误入兜底（2026-08-26 勘察院问数实测）
        if (!SupportedDimensionSupport.reexpandMergedMetrics(merged)) {
            log.warn("临时语义层 metrics supported_dimensions 重展开失败，保留原资产");
        }
        return merged;
    }

    /** LLM 常把码值域写成 "code:label" 字符串数组（目录简化格式）：归一为 ValueDomainDef 对象结构 */
    private JsonNode normalizeDomain(JsonNode node) {
        if (node == null || node.isMissingNode()) {
            return node;
        }
        if (node.isObject()) {
            JsonNode vals = node.get("values");
            if (vals != null && vals.isArray()) {
                ArrayNode nv = om.createArrayNode();
                for (JsonNode v : vals) {
                    nv.add(normalizeDomainValue(v));
                }
                ((ObjectNode) node).set("values", nv);
            }
            return node;
        }
        if (node.isArray()) {
            ObjectNode obj = om.createObjectNode();
            obj.put("stored_as", "code");
            ArrayNode vals = obj.putArray("values");
            for (JsonNode v : node) {
                vals.add(normalizeDomainValue(v));
            }
            return obj;
        }
        return node;
    }

    /** 单个码值归一："code:label" 字符串 → {code,label} 对象 */
    private JsonNode normalizeDomainValue(JsonNode v) {
        if (v.isTextual()) {
            String s = v.asText();
            ObjectNode o = om.createObjectNode();
            int idx = s.indexOf(':');
            if (idx > 0) {
                o.put("code", s.substring(0, idx).trim());
                o.put("label", s.substring(idx + 1).trim());
            } else {
                o.put("code", s.trim());
                o.put("label", s.trim());
            }
            return o;
        }
        return v;
    }

    /** 新增指标兜底：LLM 常省略 type（POC 生成器仅支持 atomic），缺省补齐 */
    private void fillMetricDefaults(JsonNode metrics) {
        if (metrics == null || !metrics.isArray()) {
            return;
        }
        for (JsonNode m : metrics) {
            if (m.isObject() && !m.hasNonNull("type")) {
                ((ObjectNode) m).put("type", "atomic");
            }
        }
    }

    /** 数组型资产追加（根即数组）：metrics/dimensions/relations */
    private void appendToArrayList(Map<String, String> merged, String file, JsonNode items) {
        appendToArrayList(merged, file, items, null, "[]");
    }

    /** 通用追加：wrapField 为空时根即数组，否则根为对象、追加到 wrapField 子数组 */
    private void appendToArrayList(Map<String, String> merged, String file, JsonNode items,
                                   String wrapField, String defaultJson) {
        if (items == null || !items.isArray() || items.size() == 0) {
            return;
        }
        ObjectNode wrapHolder = null;
        ArrayNode arr;
        JsonNode base = readTreeOr(merged.get(file), null);
        if (wrapField == null) {
            arr = base != null && base.isArray() ? (ArrayNode) base : om.createArrayNode();
        } else {
            ObjectNode obj = base != null && base.isObject() ? (ObjectNode) base
                    : readTreeOr(defaultJson, om.createObjectNode());
            wrapHolder = obj;
            arr = obj.has(wrapField) && obj.get(wrapField).isArray()
                    ? (ArrayNode) obj.get(wrapField) : obj.putArray(wrapField);
        }
        for (JsonNode item : items) {
            arr.add(item);
        }
        merged.put(file, writeJson(wrapField == null ? arr : wrapHolder));
    }

    // ────────────────────────── 提案审批（已拆 ProposalService，审批闭环委托） ──────────────────────────

    /** 审批列表（状态可选） */
    public List<InsightAgentProposal> listProposals(String agentCode, String status) {
        return proposalService.listProposals(agentCode, status);
    }

    /** 各 Agent 待审提案数（管理页行内徽标 + 未处理提示） */
    public Map<String, Long> pendingCounts() {
        return proposalService.pendingCounts();
    }

    /** 合并：待审提案 upsert 进草稿资产，合并后仍为草稿态，需发布+刷新后运行期生效 */
    public int merge(List<Integer> ids) {
        return proposalService.merge(ids);
    }

    /** 驳回：仅待审提案可驳回 */
    public int reject(List<Integer> ids) {
        return proposalService.reject(ids);
    }

    // ────────────────────────── 小工具 ──────────────────────────

    /** LLM 回答 → semantic_query 节点（剥外层包裹） */
    private JsonNode extractSemanticQuery(String answer) {
        JsonNode root;
        try {
            root = om.readTree(extractJson(answer));
        } catch (NoticeException e) {
            throw e;
        } catch (Exception e) {
            throw new NoticeException("LLM 解析输出不是合法 JSON，请重试");
        }
        JsonNode sq = root.has("semantic_query") && root.get("semantic_query").isObject()
                ? root.get("semantic_query") : root;
        if (!sq.isObject() || sq.size() == 0) {
            throw new NoticeException("LLM 解析输出缺少 semantic_query");
        }
        return sq;
    }

    /** 强制 semantic_query.agent 与目标一致（LLM 产物不可信，路由以入口为准）；顺手归一 filters 数组 → 条件树 */
    private void stampAgent(JsonNode sqNode, String agentCode) {
        if (sqNode.isObject()) {
            ObjectNode obj = (ObjectNode) sqNode;
            obj.put("agent", SemanticLayerRegistry.isDefault(agentCode) ? "default" : agentCode);
            normalizeFilterTree(obj, "filters");
            normalizeFilterTree(obj, "having");
        }
    }

    /** LLM 常把 filters/having 写成条件数组：包成 AND 条件树（协议只收对象） */
    private void normalizeFilterTree(ObjectNode obj, String field) {
        JsonNode node = obj.get(field);
        if (node != null && node.isArray()) {
            if (node.size() == 0) {
                obj.remove(field);
            } else if (node.size() == 1) {
                obj.set(field, node.get(0));
            } else {
                ObjectNode wrap = om.createObjectNode();
                wrap.put("operator", "AND");
                wrap.set("conditions", node);
                obj.set(field, wrap);
            }
        }
    }

    /** 校验错误清单文本（供维护提示词） */
    private String issueText(SmartQueryResult r) {
        StringBuilder sb = new StringBuilder();
        for (ValidationResult.Issue issue : r.getErrors()) {
            sb.append("- ").append(issue.getMessage()).append('\n');
        }
        if (sb.length() == 0 && FuncUtil.isNotEmpty(r.getErrorMessage())) {
            sb.append(r.getErrorMessage());
        }
        return sb.length() == 0 ? "（无详细错误）" : sb.toString();
    }

    /** 从模型回答中提取 JSON：优先 ```json 代码块，否则取首尾大括号（同资产生成链路口径） */
    private String extractJson(String answer) {
        if (FuncUtil.isEmpty(answer)) {
            throw new NoticeException("LLM 返回为空");
        }
        java.util.regex.Matcher m = java.util.regex.Pattern
                .compile("```json\\s*([\\s\\S]*?)\\s*```").matcher(answer);
        if (m.find()) {
            return m.group(1);
        }
        int start = answer.indexOf('{');
        int end = answer.lastIndexOf('}');
        if (start >= 0 && end > start) {
            return answer.substring(start, end + 1);
        }
        throw new NoticeException("LLM 回答中未找到 JSON，请重试");
    }

    private String writeJson(Object node) {
        try {
            return om.writeValueAsString(node);
        } catch (Exception e) {
            throw new NoticeException("JSON 序列化失败: " + e.getMessage());
        }
    }

    /** JSON 解析容错：空/非法/节点类型与兜底不匹配 → 兜底节点 */
    private <T extends JsonNode> T readTreeOr(String content, T fallback) {
        if (FuncUtil.isEmpty(content)) {
            return fallback;
        }
        try {
            JsonNode node = om.readTree(content);
            if (fallback != null && node.getNodeType() != fallback.getNodeType()) {
                return fallback;
            }
            @SuppressWarnings("unchecked")
            T typed = (T) node;
            return typed;
        } catch (Exception e) {
            return fallback;
        }
    }
}
