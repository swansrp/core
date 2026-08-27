package com.bidr.insight.smartquery.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.bidr.admin.service.common.AsyncProcessInf;
import com.bidr.insight.smartquery.constant.param.SmartQueryParam;
import com.bidr.insight.smartquery.dao.entity.InsightAgent;
import com.bidr.insight.smartquery.dao.entity.InsightAgentAsset;
import com.bidr.insight.smartquery.dao.entity.InsightAgentTable;
import com.bidr.insight.smartquery.dao.repository.InsightAgentService;
import com.bidr.insight.smartquery.layer.DimensionDef;
import com.bidr.insight.smartquery.layer.EntityDef;
import com.bidr.insight.smartquery.layer.ValueDomainDef;
import com.bidr.insight.smartquery.meta.CertifiedDraftMerger;
import com.bidr.insight.smartquery.meta.ColumnConventions;
import com.bidr.insight.smartquery.meta.CommentValueParser;
import com.bidr.insight.smartquery.meta.SkeletonBuilder;
import com.bidr.insight.smartquery.meta.SupportedDimensionSupport;
import com.bidr.kernel.exception.NoticeException;
import com.bidr.kernel.utils.FuncUtil;
import com.bidr.platform.constant.upload.UploadProgressStep;
import com.bidr.platform.vo.upload.PortalUploadProgressRes;
import com.bidr.forge.datasource.service.DataSourceCacheService;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.bidr.insight.smartquery.flow.AssetGenFlowDefinition;
import com.bidr.llm.agent.AgentAskUserTool;
import com.bidr.llm.agent.AgentFailureBreaker;
import com.bidr.llm.agent.AgentLoopListener;
import com.bidr.llm.agent.AgentLoopOptions;
import com.bidr.llm.agent.AgentLoopResult;
import com.bidr.llm.agent.AgentPlanTools;
import com.bidr.llm.agent.AutonomousSystemPrompt;
import com.bidr.llm.agent.ToolAgentRunner;
import com.bidr.llm.agent.gate.AgentTaskGate;
import com.bidr.llm.agent.session.AgentSessionContext;
import com.bidr.llm.agent.session.AgentStage;
import com.bidr.llm.flow.FlowContext;
import com.bidr.llm.flow.FlowEngine;
import com.bidr.llm.model.LiveModelFactory;
import com.bidr.llm.provider.DbAwareModelConfigProvider;
import com.bidr.llm.sse.SseEventSender;
import com.bidr.platform.redis.service.RedisService;
import com.bidr.platform.service.cache.SysConfigCacheService;
import dev.langchain4j.model.chat.ChatLanguageModel;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.sql.Connection;
import javax.sql.DataSource;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.BooleanSupplier;
import java.util.stream.Collectors;

/**
 * Title: SmartAgentAssetGenerateService
 * Description: 资产草稿异步生成（框架 AsyncProcessInf 协议）：以选表为处理单元——
 * 校验阶段核对表名合法性，处理阶段逐表读取表结构并采样码值，SAVE 阶段先落骨架草稿，
 * 再按生成模式分流：skeleton 仅骨架 / pipeline 固定流水线逐类 LLM 生成 / autonomous
 * AI 自主模式（LLM 大会话持有探索与落库工具，自主决定顺序与拆分粒度，见 AssetGenAgentTools）；
 * 指标多表时逐表生成（formula 严格单表，小上下文+独享探索预算更准，逐表增量落盘支持停止后续作）；
 * 敏感字段不参与 LLM 生成，由管理页逐表人工声明（tables[] 形态）。
 * 任务控制：全局串行闸（llm 框架 AgentTaskGate：全平台同时仅一个生成任务，心跳续期 + 失联强解锁自愈）
 * + Redis 停止键 + 属主实例线程中断 + 心跳/孤儿检测（执行实例失联后可重新发起继续）。
 * 进度存全局 Redis 键（与闸门/停止键同口径，不依赖登录态——会话/@Async 执行线程无 TokenHolder，
 * 接口默认经 TokenService 的存储会抛「登录信息已过期」），前端 AsyncProcess 组件轮询
 * /generate/progress 展示「请求/校验/处理」三段进度
 *
 * @author Sharp
 * @since 2026/8/18
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SmartAgentAssetGenerateService implements AsyncProcessInf<InsightAgentTable> {

    private final SmartAgentMetaService smartAgentMetaService;
    private final SkeletonBuilder skeletonBuilder;
    private final CertifiedDraftMerger certifiedDraftMerger;
    private final InsightAgentService insightAgentService;
    private final DataSourceCacheService dataSourceCacheService;
    private final ObjectProvider<ChatLanguageModel> chatModelProvider;
    private final ObjectProvider<RedisService> redisServiceProvider;
    /** 全局串行闸（llm 框架下沉：SETNX 属主锁 + 心跳续期 + 失联强解锁自愈，无 Redis 时内存实现兑底） */
    private final AgentTaskGate agentTaskGate;
    /** 流式进度模型工厂（llm 框架 Bean：自建 SSE 客户端+同步回落，代理/重试口径随 Bean 固化） */
    private final LiveModelFactory liveModelFactory;
    /** 系统参数缓存（生成/评审思考强度为通用参数，不走 Agent 行） */
    private final SysConfigCacheService sysConfigCacheService;

    /** ask_user 等待作答超时（分钟）：超时引导 LLM 按合理默认继续，不无限占用全局任务锁 */
    @Value("${smartquery.agent.ask-timeout-minutes:5}")
    private long askTimeoutMinutes;

    /** DAG 流程引擎（pipeline/skeleton 编排经 AssetGenFlowDefinition 链执行，画布可调、轨迹可查） */
    private final FlowEngine flowEngine;

    private final ObjectMapper om = new ObjectMapper().setSerializationInclusion(JsonInclude.Include.NON_NULL);

    /** 通用 Agent 工具循环引擎（llm 模块下沉实现：轮次控制/滑窗/触顶收口/回落直连/停止信号） */
    private final ToolAgentRunner toolAgentRunner = new ToolAgentRunner();

    /** 提示词模板存储类型（复用资产表但不属于七类资产：运行期不读取、下载包不含） */
    public static final String PROMPTS_ASSET_TYPE = "llm-prompts";
    /** 模板占位符：生成时由后端以实时骨架替换 */
    private static final String PH_ENTITIES = "{entities_json}";
    private static final String PH_DIMENSIONS = "{dimensions_json}";
    private static final String PH_DOMAIN_KEYS = "{domain_keys}";
    /** 单资产模板专用占位符：{current_content}=该类现有草稿，重生成时供模型参考修订 */
    private static final String PH_CURRENT = "{current_content}";
    /** 配对推断模板占位符：{sensitive_fields}=已声明敏感列清单（防敏感列入配对被 GROUP BY 采样外泄） */
    private static final String PH_SENSITIVE = "{sensitive_fields}";
    /** 表画像段占位符：{table_profiles}=骨架阶段确定性采集的总行数/分区值域/键唯一性文本
     *  （autonomous 与三类单资产模板共用；空画像渲染为（无）不影响旧模板） */
    private static final String PH_TABLE_PROFILES = "{table_profiles}";
    /** 列角色段占位符：{column_roles}=人工确认的每列角色/单位/粒度+业务键/分区结论摘要
     *  （口径同表画像：旧模板无占位符时 replace 零影响；已确认列禁止 LLM 再核实） */
    private static final String PH_COLUMN_ROLES = "{column_roles}";

    /** 支持 LLM 单独重生成的资产类型（人工三类；骨架三类确定性生成不提供；
     *  敏感字段由管理页逐表人工声明，不走 LLM） */
    public static final List<String> REGEN_ASSET_TYPES = Arrays.asList(
            "metrics", "relations", "concepts");

    /** 默认提示词存于 classpath 资源（按功能分文件：生成链 prompts.yml、评审链 prompts-review.yml，
     *  随版本演进）：优先级 = Agent llm-prompts 草稿 > 资源文件默认模板 */
    private static final String PROMPTS_RESOURCE = "smartquery/prompts.yml";
    private static final String PROMPTS_REVIEW_RESOURCE = "smartquery/prompts-review.yml";
    /** 默认模板内存缓存（资源不随运行期变化，首次加载后不再读盘） */
    private static volatile Map<String, String> defaultPromptCache;

    /** 工具模式追加提示（多轮探索约束，不进入前端可调模板） */
    private static final String TOOL_MODE_SUFFIX =
            "\n\n【数据探索】你拥有只读工具：describe_table（字段清单）、sample_rows（采样内容）、"
            + "group_by_field（字段码值分布）、run_sql（执行你自己写的只读 SQL，仅 SELECT/SHOW/DESC/EXPLAIN，"
            + "表名必须用已选表的 db.tbl 全名，最多返回 50 行）、"
            + "register_code_label_pairs（批量登记编码↔名称配对自动生成码值域，同表多个配对一次调用全传）。"
            + "规则：1) 骨架与码值域键中已有的信息直接用，不重复探索；2) 仅在事实不确定时探索——"
            + "指标列是否数值型、概念展开码值的真实取值、疑似编码↔名称配对、敏感列判断；"
            + "3) 探索总轮次控制在 15 轮以内，信息足够后立即输出最终 JSON；"
            + "若工具返回「探索次数已达上限」，禁止再调用任何工具，立刻基于已有信息输出最终 JSON；"
            + "4) 已声明敏感字段（见文末清单）禁止登记为码值配对，也禁止用 group_by_field/sample_rows/run_sql "
            + "以任何方式查看其取值——对敏感列的任何采样（含 GROUP BY 分布统计）都会外泄真实取值；"
            + "名单外的可疑列（姓名/证件号/手机号/薪酬等）同样按敏感对待；"
            + "5) register_code_label_pairs 登记成功返回的 code→label 映射即该字段码值语义的唯一依据，"
            + "后续产出直接引用，不必再采样验证同一映射。";

    /** 工具探索轮次上限（自管循环，突破 AiServices 0.33 写死的 10 轮；超限后强制收口而非报错） */
    private static final int MAX_TOOL_ROUNDS = 30;

    /** 工具循环上下文滑动窗口（条数）：防多轮探索后上下文爆炸 */
    private static final int MEMORY_WINDOW = 60;

    /** 自主模式会话轮次上限（探索+产出同一会话，预算高于单类生成） */
    private static final int AUTONOMOUS_MAX_ROUNDS = 60;

    /** 钉住工具：askUser 的用户已确认口径永不随窗口驱逐遗忘（spec 名即方法名，
     *  非提示词中的 snake_case 写法） */
    private static final Set<String> AUTONOMOUS_PINNED_TOOLS = Collections.singleton("askUser");

    /** 工具探索业务提示（原 AiServices 代理的 @SystemMessage 内容平移；pipeline 单资产链路专用，
     *  该链无 ask_user 工具，不得提及用户提问；自主会话用 AUTONOMOUS_BUSINESS_PROMPT）；
     *  不进 system 槽（system 槽仅供框架级纪律，业务零绑定），由 invokeLlm 拼入 user 提示词首部；
     *  资产建模质量纪律（单位/别名/快照/命名/口径完备/事实先行）为智能问数业务段，
     *  由同包 AssetModelingDiscipline 拼接（数据仓库概念不得下沉 llm 框架层） */
    private static final String EXPLORE_BUSINESS_PROMPT = "你是数据仓库语义层建模专家，可使用提供的只读工具探索数据、"
            + "核实事实后再产出资产；工具报错时自行调整参数重试或放弃该探索。\n\n"
            + AssetModelingDiscipline.MODELING_DISCIPLINE;

    /** 自主会话业务提示（角色定位 + 业务级硬约束 + 资产建模质量纪律）；
     *  不进 system 槽（llm 框架纪律独占，业务零绑定），由 runAutonomousSession 拼进
     *  user 提示词首部；任务目标/骨架上下文/输出 schema 等仍由 buildAutonomousPrompt 组装） */
    private static final String AUTONOMOUS_BUSINESS_PROMPT = "你是数据仓库语义层建模专家，"
            + "正在为用户的语义层 Agent 补齐数据资产（指标/关系/业务概念）。\n"
            + "已声明敏感字段禁止任何采样/配对/取值探索，也禁止进入产出内容；\n"
            + "用户的答复必须落实到资产的 description/notes 字段，不得答复归答复、产出归产出。\n\n"
            + AssetModelingDiscipline.MODELING_DISCIPLINE;

    // ---------------- 任务控制（分布式串行闸 / 停止信号 / 心跳） ----------------

    /** 全局串行闸任务键（全平台同时仅一个生成任务，与进度键同口径；闸机理由 llm AgentTaskGate 承载） */
    private static final String TASK_KEY = "asset-gen";
    /** 闸门占用时的友好提示（抢闸/预检共用） */
    private static final String GATE_BUSY_MESSAGE = "正在生成中，请稍候（或等待执行实例失联后自动解锁）";
    /** 全局停止键：任意实例写入，属主实例轮询消费（同时中断属主线程加速收口） */
    private static final String STOP_KEY = "smart-agent:gen-stop";
    /** 全局进度键：与闸门/停止键同口径（全局单任务，任意线程/实例可读写，不依赖登录态） */
    private static final String PROGRESS_KEY = "smart-agent:gen-progress";
    /** 进度键 TTL（秒）：终态（成功/失败/停止）需留存供前端回看，取 24h */
    private static final int PROGRESS_TTL_SECONDS = 86400;
    /** 闸门锁基础 TTL（秒）：心跳每 20s 续期；失联后闸门强解锁即时生效，TTL 仅极端兜底 */
    private static final int LOCK_TTL_SECONDS = 300;
    /** 停止键 TTL（秒）：防止停止请求无人消费后永久残留 */
    private static final int STOP_TTL_SECONDS = 600;
    /** 心跳周期（秒）：属主实例周期性刷新进度记录 heartbeat/ownerInstance 并续闸门锁 */
    private static final int HEARTBEAT_SECONDS = 20;
    /** 心跳超时阈值（毫秒）：查询侧发现运行中任务心跳超过此时长即判定执行实例失联 */
    private static final long HEARTBEAT_STALE_MS = 90_000L;

    /** 实例标识（JVM 级）：闸门属主令牌与进度记录 ownerInstance */
    private static final String INSTANCE_ID = UUID.randomUUID().toString();

    /** Redis 不可用时的进程内降级进度（本地开发兼容） */
    private volatile PortalUploadProgressRes localProgress;

    /** 本实例当前任务线程（全局单任务；停止请求落到本实例时直接 interrupt 加速收口） */
    private volatile Thread currentTaskThread;

    /** 本实例当前任务上下文（全局单任务经锁串行；停止键/暂停检查点桥接会话层，见 runTask） */
    private volatile GenTaskContext currentCtx;

    /** 心跳定时器（daemon 单线程：全平台单任务，串行复用足够） */
    private static final ScheduledExecutorService HEARTBEAT_TIMER = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "asset-gen-heartbeat");
        t.setDaemon(true);
        return t;
    });

    private volatile ScheduledFuture<?> heartbeatFuture;

    /** 停止键本地短缓存（毫秒/结果）：避免高频检查点反复读 Redis */
    private static final long STOP_CACHE_MS = 2_000L;
    private volatile long stopCheckedAt;
    private volatile boolean stopCached;

    /** 自主任务阶段键（会话层 AgentStage 上报，与 defineStages 声明一致） */
    private static final String STAGE_PREPARE = "prepare";
    private static final String STAGE_SKELETON = "skeleton";
    private static final String STAGE_GENERATE = "generate";
    private static final String STAGE_FINISH = "finish";

    /** 任务被用户停止的收口信号：各检查点（轮头/逐表逐类迭代前/工具前）抛出，由任务入口统一收口 */
    static class GenerationStoppedException extends RuntimeException {
        GenerationStoppedException() {
            super("任务已被用户停止");
        }
    }

    /**
     * 同步前置校验并拿全局串行闸，返回待异步处理的选表清单；
     * 由 Controller 紧接着调用 handleTask（经代理触发 @Async）。
     * mode：skeleton 仅骨架 / pipeline 固定流水线 / autonomous AI 自主（见 GenTaskContext）
     */
    public List<InsightAgentTable> beginGenerate(String agentCode, String mode) {
        requireMode(mode);
        agentTaskGate.acquire(TASK_KEY, INSTANCE_ID, LOCK_TTL_SECONDS, GATE_BUSY_MESSAGE);
        try {
            InsightAgent agent = insightAgentService.selectOne(
                    new QueryWrapper<InsightAgent>().eq("agent_code", agentCode));
            if (agent == null) {
                throw new NoticeException("Agent [" + agentCode + "] 不存在");
            }
            List<InsightAgentTable> tables = smartAgentMetaService.selectedTables(agentCode);
            if (FuncUtil.isEmpty(tables)) {
                throw new NoticeException("请先选择数据表再生成资产");
            }
            // 数据源可用性前置校验（失败直接抛给调用方，不进异步）
            dataSourceCacheService.getDataSource(agent.getDsName());
            return tables;
        } catch (Exception e) {
            agentTaskGate.release(TASK_KEY, INSTANCE_ID);
            throw e;
        }
    }

    /** 模式合法性校验 */
    private void requireMode(String mode) {
        if (!GenTaskContext.MODE_SKELETON.equals(mode) && !GenTaskContext.MODE_PIPELINE.equals(mode)
                && !GenTaskContext.MODE_AUTONOMOUS.equals(mode)) {
            throw new NoticeException("生成模式不合法: " + mode + "（skeleton/pipeline/autonomous）");
        }
    }

    /** Redis 服务（不可用时返回 null，停止/进度退化为进程内实现） */
    private RedisService redis() {
        return redisServiceProvider.getIfAvailable();
    }

    /** 生成在途预检（会话创建前调用：闸门占用直接拒请求带友好提示，
     *  避免「先建会话再抢闸失败」的秒死 FAILED 会话；与 beginGenerate 拿闸同口径，
     *  TOCTOU 窗口由 run 线程内拿闸权威兜底；失联残留锁由闸门自愈清除 */
    public void assertNotGenerating() {
        agentTaskGate.checkFree(TASK_KEY, GATE_BUSY_MESSAGE);
    }

    /** 启动心跳：周期性刷新进度记录 ownerInstance/heartbeat 并续闸门锁（宕机后心跳停止，查询侧判定失联） */
    private void startHeartbeat() {
        stopHeartbeat();
        heartbeatFuture = HEARTBEAT_TIMER.scheduleAtFixedRate(() -> {
            try {
                PortalUploadProgressRes item = getUploadProgress();
                item.setOwnerInstance(INSTANCE_ID);
                item.setHeartbeat(System.currentTimeMillis());
                setUploadProgress(item);
                agentTaskGate.heartbeat(TASK_KEY, INSTANCE_ID, LOCK_TTL_SECONDS);
            } catch (Exception e) {
                log.warn("心跳刷新失败（忽略，下轮重试）: {}", e.getMessage());
            }
        }, HEARTBEAT_SECONDS, HEARTBEAT_SECONDS, TimeUnit.SECONDS);
    }

    private void stopHeartbeat() {
        ScheduledFuture<?> f = heartbeatFuture;
        if (f != null) {
            f.cancel(false);
            heartbeatFuture = null;
        }
    }

    /** 停止请求（任意实例可调）：写 Redis 停止键（属主轮询消费）+ 中断本实例任务线程（属主在本实例时秒级收口）。
     *  返回当前进度供前端展示 */
    public PortalUploadProgressRes stopGenerate() {
        RedisService redis = redis();
        if (redis != null) {
            try {
                redis.set(STOP_KEY, STOP_TTL_SECONDS, INSTANCE_ID);
            } catch (Exception e) {
                log.warn("停止键写入失败: {}", e.getMessage());
            }
        } else {
            stopCached = true;
            stopCheckedAt = System.currentTimeMillis();
        }
        Thread t = currentTaskThread;
        if (t != null) {
            log.info("停止请求已发出：中断本实例任务线程 {}", t.getName());
            t.interrupt();
        }
        // 属主已失联（如后端重启杀任务线程）时无线程可中断，残留锁当场强删，停止即解锁
        agentTaskGate.forceUnlockIfOrphan(TASK_KEY);
        return getUploadProgress();
    }

    /** 停止检查点（2s 本地短缓存防高频读 Redis）：命中抛 GenerationStoppedException 由任务入口收口 */
    private void checkStop() {
        if (stopRequested()) {
            throw new GenerationStoppedException();
        }
    }

    /** 停止键查询（带短缓存；无 Redis 时读本地降级标志；会话层停止键叠加判定） */
    private boolean stopRequested() {
        long now = System.currentTimeMillis();
        if (now - stopCheckedAt < STOP_CACHE_MS) {
            return stopCached;
        }
        RedisService redis = redis();
        boolean stopped = false;
        if (redis != null) {
            try {
                stopped = Boolean.TRUE.equals(redis.hasKey(STOP_KEY));
            } catch (Exception e) {
                log.warn("停止键查询失败（本轮视为未停止）: {}", e.getMessage());
            }
        }
        if (!stopped) {
            stopped = sessionStopRequested();
        }
        stopCached = stopped;
        stopCheckedAt = now;
        return stopped;
    }

    /** 会话层停止键检查（definition 接入会话层时生效；旧链路/非会话驱动任务为 false） */
    private boolean sessionStopRequested() {
        GenTaskContext ctx = currentCtx;
        if (ctx == null || ctx.getSessionCtx() == null) {
            return false;
        }
        try {
            return ctx.getSessionCtx().isStopRequested();
        } catch (Exception e) {
            log.warn("会话停止键查询失败（本轮视为未停止）: {}", e.getMessage());
            return false;
        }
    }

    /** 阶段上报便捷桥（非会话驱动任务静默跳过；写失败由会话层容错不阻断生成） */
    private void stageStart(String key, String detail) {
        AgentSessionContext sc = currentCtx == null ? null : currentCtx.getSessionCtx();
        if (sc != null) {
            sc.stageStart(key, detail);
        }
    }

    private void stageDone(String key, String detail) {
        AgentSessionContext sc = currentCtx == null ? null : currentCtx.getSessionCtx();
        if (sc != null) {
            sc.stageDone(key, detail);
        }
    }

    /** 任务停止后的终态收口：进度置 STOPPED 并列出已完成部分；清理停止键（下次任务干净起步） */
    private void finalizeStopped(GenTaskContext ctx) {
        try {
            RedisService redis = redis();
            if (redis != null) {
                redis.delete(STOP_KEY);
            }
        } catch (Exception ignored) {
            // 清理失败由 TTL 兑底
        }
        PortalUploadProgressRes item = getUploadProgress();
        item.setStep(UploadProgressStep.STOPPED);
        item.setLoaded(item.getTotal() == null ? 0 : item.getTotal());
        List<String> comments = item.getComments() == null ? new ArrayList<>() : item.getComments();
        comments.clear();
        comments.add("已停止：已完成部分保留在草稿，可重新发起生成继续（AI 自主模式将自动补齐缺失资产）");
        comments.addAll(draftStatusSummary(ctx));
        item.setComments(comments);
        setUploadProgress(item);
    }

    /** 各类草稿完成情况摘要（停止终态 comments：骨架/人工三类草稿条目数或无草稿） */
    private List<String> draftStatusSummary(GenTaskContext ctx) {
        List<String> lines = new ArrayList<>();
        try {
            lines.add("骨架：实体 " + ctx.getEntities().size() + " / 维度 " + ctx.getDimensions().size()
                    + " / 码值域 " + ctx.getDomains().size());
            for (String type : REGEN_ASSET_TYPES) {
                InsightAgentAsset asset = smartAgentMetaService.getAsset(ctx.getAgentCode(), type);
                int count = draftItemCount(type, asset);
                lines.add(assetCnName(type) + "：" + (asset == null || FuncUtil.isEmpty(asset.getContent())
                        ? "无草稿" : "已有草稿 " + count + " 项"));
            }
        } catch (Exception e) {
            log.warn("停止终态草稿摘要生成失败（忽略）: {}", e.getMessage());
        }
        return lines;
    }

    /** 草稿条目数（指标/关系按数组、概念按 concepts 数组） */
    private int draftItemCount(String type, InsightAgentAsset asset) {
        if (asset == null || FuncUtil.isEmpty(asset.getContent())) {
            return 0;
        }
        try {
            JsonNode root = om.readTree(asset.getContent());
            JsonNode arr = "concepts".equals(type) ? root.path("concepts") : root;
            return arr.isArray() ? arr.size() : 0;
        } catch (Exception e) {
            return 0;
        }
    }

    /** 进度读写覆写为全局 Redis 键：接口默认实现挂当前登录 token（TokenService/TokenHolder），
     *  会话层 raw 线程与长任务场景无登录态会抛「登录信息已过期」；进度本为全局单任务口径，
     *  与锁/停止键一致，任意线程/实例可读写。Redis 不可用时降级进程内 */
    @Override
    public PortalUploadProgressRes getUploadProgress() {
        RedisService redis = redis();
        if (redis == null) {
            PortalUploadProgressRes local = localProgress;
            return local == null ? new PortalUploadProgressRes(UploadProgressStep.INIT, 0, 0, new ArrayList<>()) : local;
        }
        try {
            String json = redis.get(PROGRESS_KEY, String.class);
            if (FuncUtil.isNotEmpty(json)) {
                return om.readValue(json, PortalUploadProgressRes.class);
            }
        } catch (Exception e) {
            log.warn("生成进度读取失败（回落初始态）: {}", e.getMessage());
        }
        return new PortalUploadProgressRes(UploadProgressStep.INIT, 0, 0, new ArrayList<>());
    }

    @Override
    public void setUploadProgress(PortalUploadProgressRes progress) {
        RedisService redis = redis();
        if (redis == null) {
            localProgress = progress;
            return;
        }
        try {
            redis.set(PROGRESS_KEY, PROGRESS_TTL_SECONDS, om.writeValueAsString(progress));
        } catch (Exception e) {
            log.warn("生成进度写入失败（忽略，下轮重试）: {}", e.getMessage());
        }
    }

    /** 查询侧进度（含孤儿检测）：运行中状态但心跳超时 → 执行实例已失联，
     *  改写 STOPPED 终态提示可重新发起，同时强删残留锁即时解锁（不再干等 300s TTL） */
    public PortalUploadProgressRes queryProgress() {
        PortalUploadProgressRes item = getUploadProgress();
        if (item.getHeartbeat() != null && isRunningStep(item.getStep())
                && System.currentTimeMillis() - item.getHeartbeat() > HEARTBEAT_STALE_MS) {
            log.warn("生成任务心跳超时（owner={}，距上次 {}ms），判定执行实例失联",
                    item.getOwnerInstance(), System.currentTimeMillis() - item.getHeartbeat());
            item.setStep(UploadProgressStep.STOPPED);
            item.setLoaded(item.getTotal() == null ? 0 : item.getTotal());
            List<String> comments = item.getComments() == null ? new ArrayList<>() : item.getComments();
            comments.clear();
            comments.add("执行实例失联（宕机/重启）：已完成部分保留在草稿，可重新发起生成继续");
            item.setComments(comments);
            item.setHeartbeat(null);
            setUploadProgress(item);
            agentTaskGate.forceUnlockIfOrphan(TASK_KEY);
        }
        return item;
    }

    private static boolean isRunningStep(UploadProgressStep step) {
        return step == UploadProgressStep.UPLOAD || step == UploadProgressStep.VALIDATE
                || step == UploadProgressStep.SAVE;
    }

    /** 异步任务入口（@Async 经代理生效，Controller 传入 agentCode/mode 避免成员状态）：
     * 自编排进度三阶段，不调 AsyncProcessInf.super.handle——
     * 其逐表 REQUIRES_NEW 事务对纯元数据读取无意义，且 catch 中 rollback 已提交事务会以
     * "Transaction is already completed"掩盖真实异常；这里直接记录原始异常日志与进度。
     * SAVE 阶段按模式推进：skeleton/骨架 1 步；pipeline 骨架+人工三类各 1 步；autonomous 骨架+自主会话 2 步。
     * pipeline/skeleton 的写死编排已 flow 化（AssetGenFlowDefinition 链：敏感闸→骨架→配对→逐类→收口，
     * skeleton 模式经条件边直通收口）；任务线程/心跳/停止收口在本方法统一管理（finally 释放闸与线程登记） */
    @Async
    public void handleTask(String agentCode, String mode, List<InsightAgentTable> items, String operator) {
        runTask(agentCode, mode, items, operator, null);
    }

    /** 任务同步执行体（旧 @Async 入口与 agent 会话层 AssetGenAgentDefinition 共用）：
     * 返回任务上下文（definition 据此把用户停止转会话 STOPPED 终态而非 FINISHED）；
     * sessionCtx 非空时停止键/暂停检查点/过程日志桥接会话层（跨实例可控、事件流可见） */
    public GenTaskContext runTask(String agentCode, String mode, List<InsightAgentTable> items,
                                  String operator, AgentSessionContext sessionCtx) {
        GenTaskContext ctx = new GenTaskContext(agentCode, mode);
        ctx.setSessionCtx(sessionCtx);
        currentCtx = ctx;
        currentTaskThread = Thread.currentThread();
        startHeartbeat();
        // 会话驱动的自主任务：声明阶段清单（AgentStages 数据源，随执行逐段跳动）
        if (sessionCtx != null && ctx.isAutonomous()) {
            sessionCtx.defineStages(
                    AgentStage.of(STAGE_PREPARE, "选表校验", 10),
                    AgentStage.of(STAGE_SKELETON, "骨架构建", 60),
                    AgentStage.of(STAGE_GENERATE, "AI 自主生成", 900),
                    AgentStage.of(STAGE_FINISH, "收口落盘", 15));
        }
        try {
            if (FuncUtil.isEmpty(items)) {
                uploadProgressFinish();
                return ctx;
            }
            startUploadProgress(items.size());
            startValidateRecord(items.size());
            openContext(ctx);
            stageStart(STAGE_PREPARE, null);
            List<InsightAgentTable> valid = new ArrayList<>();
            int i = 1;
            for (InsightAgentTable item : items) {
                if (validate(item)) {
                    valid.add(item);
                    addUploadProgress(i++);
                }
            }
            stageDone(STAGE_PREPARE, "有效表 " + valid.size() + "/" + items.size() + " 张");
            int saveTotal = GenTaskContext.MODE_AUTONOMOUS.equals(mode) ? 2
                    : (GenTaskContext.MODE_SKELETON.equals(mode) ? 1 : 1 + REGEN_ASSET_TYPES.size());
            startSavePhase(saveTotal);
            if (ctx.isAutonomous()) {
                stageStart(STAGE_SKELETON, null);
                buildSkeletonAndSave(ctx, valid);
                stageDone(STAGE_SKELETON, "实体 " + ctx.getEntities().size()
                        + " / 维度 " + ctx.getDimensions().size()
                        + " / 码值域 " + ctx.getDomains().size());
                advanceTo(2, "AI 自主生成中（LLM 自主决定探索/顺序/拆分，可能需几分钟）…");
                stageStart(STAGE_GENERATE, "LLM 自主探索与逐类落库，轮次上限 " + AUTONOMOUS_MAX_ROUNDS);
                runAutonomousSession(ctx);
                stageDone(STAGE_GENERATE, null);
            } else {
                // pipeline/skeleton 编排交 flow 链（各结点执行器回调本类桥方法，逐类方法体不变）；
                // 停止经 stopSupplier 接引擎每结点检查点，闸门/心跳/进度机制不变
                FlowContext flowCtx = new FlowContext(null);
                flowCtx.setVariable(AssetGenFlowDefinition.VAR_MODE, mode);
                flowCtx.setVariable(AssetGenFlowDefinition.VAR_GEN_CTX, ctx);
                flowCtx.setVariable(AssetGenFlowDefinition.VAR_TABLES, valid);
                flowCtx.setOperator(operator);
                flowCtx.setStopSupplier(() -> stopRequested() || Thread.currentThread().isInterrupted());
                flowEngine.execute(AssetGenFlowDefinition.FLOW_KEY, flowCtx);
                ctx.setTraceId(flowCtx.getTraceId());
                // 引擎停止收口（非 SSE 链路静默退出）：转既有停止终态路径
                checkStop();
                if (!ctx.getFlowFailures().isEmpty()) {
                    // 收口结点已置 FAILED 终态（comments 逐类列出原因）
                    ctx.setFailed(true);
                    ctx.setFailReason(ctx.getFlowFailures().size() + " 类资产生成失败");
                    return ctx;
                }
            }
            stageStart(STAGE_FINISH, null);
            uploadProgressFinish();
            stageDone(STAGE_FINISH, null);
        } catch (GenerationStoppedException e) {
            log.info("Agent '{}' 生成任务被用户停止", agentCode);
            finalizeStopped(ctx);
            ctx.setStoppedByUser(true);
        } catch (Exception e) {
            if (Thread.currentThread().isInterrupted() || stopRequested()) {
                // 会话层停止的 interrupt 信号打断阻塞调用（包装型 RuntimeException）：按用户停止收口
                log.info("Agent '{}' 生成任务被中断停止: {}", agentCode, e.getMessage());
                finalizeStopped(ctx);
                ctx.setStoppedByUser(true);
            } else {
                log.error("Agent '{}' 资产草稿生成失败", agentCode, e);
                uploadProgressException(briefCause(e));
                ctx.setFailed(true);
                ctx.setFailReason(briefCause(e));
            }
        } finally {
            stopHeartbeat();
            currentTaskThread = null;
            currentCtx = null;
            closeContext(ctx);
            agentTaskGate.release(TASK_KEY, INSTANCE_ID);
        }
        return ctx;
    }

    /** SAVE 阶段进度重置：total 切为「骨架+人工四类」口径（框架默认 startSaveRecord 沿用选表数） */
    private void startSavePhase(int total) {
        PortalUploadProgressRes item = getUploadProgress();
        item.setStep(UploadProgressStep.SAVE);
        item.setTotal(total);
        item.setLoaded(0);
        setUploadProgress(item);
    }

    /** 部分类别失败的终态：comments 逐类列出成败原因（骨架草稿已落盘不受影响） */
    private void markProgressFailed(List<String> failures) {
        PortalUploadProgressRes item = getUploadProgress();
        item.setStep(UploadProgressStep.FAILED);
        item.setLoaded(item.getTotal());
        item.getComments().addAll(failures);
        setUploadProgress(item);
    }

    /** 逐类生成进度推进：loaded 前进 + comments 覆写为当前步骤（单行，区别于失败清单的追加） */
    private void advanceTo(int loaded, String stepComment) {
        PortalUploadProgressRes item = getUploadProgress();
        item.setLoaded(loaded);
        List<String> comments = item.getComments();
        if (comments == null) {
            comments = new ArrayList<>();
            item.setComments(comments);
        }
        comments.clear();
        comments.add(stepComment);
        setUploadProgress(item);
    }

    /** 过程日志上限（仅留最近条目，避免进度记录膨胀） */
    private static final int LOG_MAX_LINES = 50;
    private static final DateTimeFormatter LOG_TIME = DateTimeFormatter.ofPattern("HH:mm:ss");

    /** 过程日志上报：追加到进度记录 logs，前端进度窗轮询后滚动展示（让用户看见 LLM 在干活）；
     *  后端日志仅记摘要行（首行+总字数），思考/应答/工具返回全文由前端上屏，后端不再转储；
     *  写失败不阻断生成主链路 */
    private void appendLog(String line) {
        log.info("[资产生成] {}", briefLine(line));
        // 会话驱动任务：过程日志同步上报会话事件流（前端 AgentChat 过程可见）
        AgentSessionContext sc = currentCtx == null ? null : currentCtx.getSessionCtx();
        if (sc != null) {
            try {
                sc.log(line);
            } catch (Exception e) {
                log.warn("会话事件上报失败（忽略）: {}", e.getMessage());
            }
        }
        // 进度记录（Redis 50 行上限）同口径只存摘要行，防思考全文撑爆存储；全文由会话事件流承载
        try {
            PortalUploadProgressRes item = getUploadProgress();
            List<String> logs = item.getLogs();
            if (logs == null) {
                logs = new ArrayList<>();
                item.setLogs(logs);
            }
            logs.add(LocalTime.now().format(LOG_TIME) + " " + briefLine(line));
            while (logs.size() > LOG_MAX_LINES) {
                logs.remove(0);
            }
            setUploadProgress(item);
        } catch (Exception e) {
            log.warn("过程日志写入失败（忽略）: {}", e.getMessage());
        }
    }

    /** 日志摘要行：短单行原样；多行/超长仅取首行（截 200 字）+总字数——
     *  思考/应答/工具返回全文由前端上屏，后端日志与进度记录不再转储全文 */
    private static String briefLine(String line) {
        if (line == null) {
            return "";
        }
        int nl = line.indexOf('\n');
        if (nl < 0 && line.length() <= 200) {
            return line;
        }
        String head = nl < 0 ? line : line.substring(0, nl);
        if (head.length() > 200) {
            head = head.substring(0, 200) + "…";
        }
        return head + "（共 " + line.length() + " 字）";
    }

    /** 资产类型中文名（日志可读性） */
    private static String assetCnName(String type) {
        switch (type) {
            case "metrics":
                return "指标";
            case "relations":
                return "关系";
            case "concepts":
                return "业务概念";
            case "sensitive-fields":
                return "敏感字段";
            default:
                return type;
        }
    }

    /** 异常摘要（进度/失败清单可读）：取根因文案；超时类附操作指引
     *  （提示词带全量骨架上下文，大表多时单次响应可能超默认 120 秒） */
    private static String briefCause(Throwable e) {
        Throwable t = e;
        while (t.getCause() != null && t.getCause() != t) {
            t = t.getCause();
        }
        String msg = t.getMessage() == null ? t.getClass().getSimpleName() : t.getMessage();
        String low = msg.toLowerCase();
        if (t instanceof java.io.InterruptedIOException || low.contains("timeout") || low.contains("timed out")) {
            return "LLM 调用超时，请在系统参数调大「大模型超时(秒)」后重试（当前默认 120）";
        }
        return msg.length() <= 150 ? msg : msg.substring(0, 150) + "…";
    }

    /** 任务上下文就绪：打开 Agent 绑定数据源连接（骨架容器已在 ctx 构造时初始化）；
     *  池引用同步挂入 ctx——LLM 会话期探索工具每次执行借-用-还（不再全程独占单连接） */
    private void openContext(GenTaskContext ctx) {
        InsightAgent agent = insightAgentService.selectOne(
                new QueryWrapper<InsightAgent>().eq("agent_code", ctx.getAgentCode()));
        try {
            DataSource pool = dataSourceCacheService.getDataSource(agent.getDsName());
            ctx.setDataSource(pool);
            ctx.setConn(pool.getConnection());
        } catch (Exception e) {
            throw new NoticeException("打开数据源连接失败: " + e.getMessage());
        }
    }

    /** 校验阶段：表名须为 db.tbl 形式（无任务态，接口契约保留） */
    @Override
    public boolean validate(InsightAgentTable item) {
        return ColumnConventions.splitTableName(item.getTableName()) != null;
    }

    /** 接口契约方法：自管编排后单表处理由 handleTask 内部驱动（带 ctx），不再经此入口 */
    @Override
    public void handle(InsightAgentTable item) {
        throw new UnsupportedOperationException("资产生成自管编排，请经 handleTask 调用");
    }

    /** 处理阶段：单表 → 实体骨架 + 维度骨架 + 码值采样 */
    private void handleTable(InsightAgentTable item, GenTaskContext ctx) {
        String[] parts = ColumnConventions.splitTableName(item.getTableName());
        try {
            skeletonBuilder.buildTableAssets(ctx.getConn(), parts[0], parts[1], item.getTableComment(),
                    ctx.getEntities(), ctx.getDimensions(), ctx.getDomains(),
                    ctx.getEntityNames(), ctx.getDimensionNames());
        } catch (Exception e) {
            throw new IllegalStateException("表 " + item.getTableName() + " 读取失败: " + e.getMessage(), e);
        }
    }

    // ---------------- flow 编排桥接（AssetGenFlowDefinition 各结点执行器回调；逐类方法体不变只换编排层） ----------------

    /** 敏感闸结点：skeleton 模式直通（其「已配置才清理」在骨架结点内）；
     *  其余强制闸（未就绪即收链，骨架不白做）+预载敏感标记供后续强制层使用 */
    public void flowSensitiveGate(GenTaskContext ctx) {
        if (GenTaskContext.MODE_SKELETON.equals(ctx.getMode())) {
            return;
        }
        requireSensitiveFieldsConfigured(ctx.getAgentCode(), null);
        loadSensitiveMarks(ctx);
    }

    /** 骨架结点：逐表构建骨架（读结构+采样）→敏感残留清理（已配置才做；pipeline 已由闸预载）→骨架落盘→进度+1 */
    public void flowSkeleton(GenTaskContext ctx, List<InsightAgentTable> tables) {
        buildSkeletonAndSave(ctx, tables);
    }

    /** 配对推断结点：LLM 逐表推断编码↔名称配对与备注枚举域，后端采样补齐码值域（增强步骤不阻断链路） */
    public void flowPair(GenTaskContext ctx, String promptKey) {
        ChatLanguageModel model = chatModelProvider.getIfUnique();
        if (model == null) {
            throw new NoticeException("未配置 LLM 模型（llm.* 或系统参数），无法 LLM 生成；可改用「仅生成骨架」");
        }
        appendLog("第一步：LLM 推断编码↔名称配对字段，采样补齐码值域");
        // 逐表直连小请求的日志包装（runner 内部自带循环日志，直连链路复用同口径；停止键透传供等待期轮询）
        ChatLanguageModel logged = ToolAgentRunner.loggingModel(model, this::appendLog, this::stopRequested);
        inferAndSampleCodeLabelPairs(logged, ctx, promptKey);
    }

    /** LLM 逐类结点：进入即推进一格（口径=正在处理第几项，避免单类耗时几分钟时进度条停死）；
     *  单类失败不阻断链路（记入任务失败清单，收口结点汇总置终态） */
    public void flowAsset(GenTaskContext ctx, String assetType, String promptKey) {
        checkStop();
        ctx.setSaveStep(ctx.getSaveStep() + 1);
        advanceTo(ctx.getSaveStep(), "正在生成" + assetCnName(assetType) + "（LLM 多轮探索，可能需几分钟）…");
        ChatLanguageModel model = chatModelProvider.getIfUnique();
        if (model == null) {
            throw new NoticeException("未配置 LLM 模型（llm.* 或系统参数），无法 LLM 生成；可改用「仅生成骨架」");
        }
        try {
            generateSingleAsset(model, ctx, assetType, null, promptKey);
        } catch (GenerationStoppedException e) {
            throw e;
        } catch (Exception e) {
            log.error("Agent '{}' 资产 '{}' LLM 生成失败", ctx.getAgentCode(), assetType, e);
            ctx.getFlowFailures().add("资产 " + assetCnName(assetType) + " 生成失败: " + briefCause(e));
        }
    }

    /** 收口结点：补齐落盘骨架（配对/工具新增的码值域不丢，人工四类非空保留不受影响）；
     *  失败清单非空置 FAILED 终态（comments 逐类列出原因） */
    public void flowFinish(GenTaskContext ctx) {
        smartAgentMetaService.saveGeneratedDrafts(ctx.getAgentCode(),
                ctx.getEntities(), ctx.getDimensions(), ctx.getDomains(), ctx.getSensitiveKeys());
        if (!ctx.getFlowFailures().isEmpty()) {
            markProgressFailed(ctx.getFlowFailures());
        }
    }

    /** 骨架构建+落盘（autonomous 链外编排骨架段与 flow 骨架结点共用）：逐表读结构+采样，
     *  敏感已配置则清理码值域残留后落盘（LLM 逐类生成以骨架为上下文，失败也不丢骨架成果） */
    private void buildSkeletonAndSave(GenTaskContext ctx, List<InsightAgentTable> valid) {
        appendLog("开始生成骨架资产（" + valid.size() + " 张选表：读结构+采样码值）");
        for (InsightAgentTable item : valid) {
            checkStop();
            handleTable(item, ctx);
        }
        if (sensitiveFieldsConfigured(ctx.getAgentCode())) {
            loadSensitiveMarks(ctx);
            purgeSensitiveDomains(ctx);
        }
        // 敏感键传入落草稿合并：保留下来的旧认证域若覆盖敏感列也会被统一清理（安全优先）
        smartAgentMetaService.saveGeneratedDrafts(ctx.getAgentCode(),
                ctx.getEntities(), ctx.getDimensions(), ctx.getDomains(), ctx.getSensitiveKeys());
        // 表画像（确定性预探索）：逐表总行数/分区分布/键唯一性，注入后续 LLM 生成上下文——
        // 单表失败仅告警（画像缺失退化为无画像段，生成链行为不变）
        try {
            ctx.setTableProfiles(new TableProfiler().profile(ctx.getConn(), ctx.getEntities()));
            appendLog("表画像采集完成（" + ctx.getTableProfiles().size() + " 张表：总行数/分区值域/键唯一性，已注入生成上下文）");
        } catch (Exception e) {
            log.warn("Agent '{}' 表画像采集失败（忽略）: {}", ctx.getAgentCode(), e.getMessage());
        }
        ctx.setSaveStep(ctx.getSaveStep() + 1);
        addUploadProgress(1);
    }

    /** AI 自主模式会话（autonomous）：LLM 大会话同时持有探索工具（AgentExploreTools）与
     *  产出工具（AssetGenAgentTools），自主决定生成顺序与拆分粒度，逐类/逐表 save_draft 增量落库；
     *  停止三保险（引擎轮头/工具调用前/服务检查点）。会话异常直接报错不静默回落 pipeline
     *  （用户可手选重试；已保存草稿保留，重新发起凭 list_status/get_draft 增量续作） */
    private void runAutonomousSession(GenTaskContext ctx) {
        long startMs = System.currentTimeMillis();
        // 流式进度门面（自建 SSE 客户端）：模型 reasoning_content 思考流实时透出——与问数链同口径：
        // 流式帧（思考中/应答中，每秒一帧携累积全文）走会话 state.live 替换式通道（抽屉实时全文展示），
        // 轮末归档与其他日志走追加式事件流（留痕不膨胀）；同步模型会丢弃思考段故换流式；
        // 无 Provider 时回落同步模型（行为不变）
        java.util.function.Consumer<String> liveSink = line -> {
            AgentSessionContext sessionCtx = ctx.getSessionCtx();
            if (sessionCtx == null || line == null) {
                appendLog(line);
                return;
            }
            // 分流：流式帧（思考/应答状态行）覆盖 live 字段；归档行与引擎轮次日志走事件流留痕
            if (line.startsWith("思考中") || line.startsWith("应答中") || line.startsWith("等待 LLM")
                    || line.startsWith("流式通道")) {
                sessionCtx.pushLive(line);
            } else {
                appendLog(line);
            }
        };
        ChatLanguageModel model = buildLiveModel(liveSink, budgetOf(SmartQueryParam.GENERATE_THINKING_BUDGET));
        if (model == null) {
            throw new NoticeException("未配置 LLM 模型（llm.* 或系统参数），无法 AI 自主生成");
        }
        // 与 pipeline 同前置闸与强制层：敏感未声明不开工、骨架采样残留先清理
        requireSensitiveFieldsConfigured(ctx.getAgentCode(), null);
        loadSensitiveMarks(ctx);
        purgeSensitiveDomains(ctx);
        AgentExploreTools explore = new AgentExploreTools(ctx.getDataSource()::getConnection, ctx.getAgentCode(), ctx.getEntities(),
                (e, c, l) -> registerCodeLabelPair(ctx, e, c, l), this::appendLog, this::stopRequested);
        AssetGenAgentTools genTools = new AssetGenAgentTools(ctx, smartAgentMetaService, certifiedDraftMerger, this::appendLog, this::stopRequested);
        appendLog("AI 自主会话启动（LLM 自主决定探索、顺序与拆分，轮次上限 " + AUTONOMOUS_MAX_ROUNDS + "）");
        // 会话驱动链额外给 ask_user 工具（口径分歧提问等用户选择，禁止猜测）；非会话链 sessionCtx 为空不注册
        List<Object> toolObjects = new ArrayList<>(Arrays.asList(explore, genTools));
        if (ctx.getSessionCtx() != null) {
            toolObjects.add(new AgentAskUserTool(ctx.getSessionCtx(), askTimeoutMinutes * 60_000L));
        }
        // 计划待办工具（框架通用）：会话链挂 ctx.planBoard()，非会话链传 null（调用返回 error 文案，注册不炸）
        toolObjects.add(new AgentPlanTools(
                ctx.getSessionCtx() == null ? null : ctx.getSessionCtx().planBoard(),
                () -> stopRequested() ? "{\"error\":\"任务已被用户停止，禁止再调用任何工具\"}" : null,
                this::appendLog));
        AgentLoopResult result;
        // 计划仪式轮不消耗轮次配额（自主链注册了 AgentPlanTools，见 ROUND_EXEMPT_TOOLS 约定）
        AgentLoopOptions autonomousOpt = new AgentLoopOptions(AUTONOMOUS_MAX_ROUNDS, MEMORY_WINDOW, AUTONOMOUS_PINNED_TOOLS);
        autonomousOpt.setRoundExemptTools(AgentPlanTools.ROUND_EXEMPT_TOOLS);
        // 失败分类熔断：同类探索失败（列不存在/表范围/只读拒绝）累计 3 次注入拉直指令防死磕
        autonomousOpt.setFailureBreaker(new AgentFailureBreaker(AgentExploreTools.exploreFailureRules()));
        // system 槽仅框架规划纪律（llm 业务零绑定，不得沾任何业务内容）；业务段拼进 user 提示词首部
        String systemPrompt = AutonomousSystemPrompt.PLANNING_DISCIPLINE;
        String userPrompt = AUTONOMOUS_BUSINESS_PROMPT + "\n\n" + buildAutonomousPrompt(ctx);
        // 发出提示词全文留痕（debug）：事件流承载全文（前端预览 300 字可展开、复制全体不截断）；
        // 服务端日志与 Redis 进度经 briefLine 仅存摘要行不膨胀（多行取首行+总字数）
        appendLog("【发出提示词·system】（" + systemPrompt.length() + " 字）\n" + systemPrompt);
        appendLog("【发出提示词·user】（" + userPrompt.length() + " 字）\n" + userPrompt);
        try {
            result = toolAgentRunner.run(model, systemPrompt, userPrompt,
                    toolObjects,
                    autonomousOpt,
                    loopListener(genTools));
        } finally {
            // 兜底落盘：会话中探索工具登记的码值域/维度（骨架内存态）不随异常丢失
            try {
                smartAgentMetaService.saveGeneratedDrafts(ctx.getAgentCode(),
                        ctx.getEntities(), ctx.getDimensions(), ctx.getDomains(), ctx.getSensitiveKeys());
            } catch (Exception e) {
                log.warn("Agent '{}' 自主会话骨架兜底落盘失败（忽略，三类草稿已由 save_draft 保存）: {}",
                        ctx.getAgentCode(), e.getMessage());
            }
        }
        // 会话收口：末帧流式内容先归档再清空——替换式通道终态即失，末帧常含最后一轮思考/应答全文，
        // 留痕进事件流保证终态后「复制全体」仍可带走（含用户中途停止场景：轮末归档未必推过）
        if (ctx.getSessionCtx() != null) {
            String lastLive = ctx.getSessionCtx().getState().getLive();
            if (lastLive != null && lastLive.contains("\n")) {
                appendLog("【流式末帧留痕】\n" + lastLive);
            }
            ctx.getSessionCtx().pushLive(null);
        }
        if (result.isStopped()) {
            throw new GenerationStoppedException();
        }
        appendLog("AI 自主会话结束（" + result.getRounds() + " 轮，耗时 " + elapsedText(startMs) + "）"
                + (genTools.isFinished() ? "：LLM 已确认完成" : "：未显式 finish，以会话结论为准"));
        if (FuncUtil.isNotEmpty(genTools.getFinishSummary())) {
            appendLog("任务总结：" + genTools.getFinishSummary());
        }
        log.info("Agent '{}' 自主会话完成：{} 轮，finish={}，耗时 {}", ctx.getAgentCode(), result.getRounds(),
                genTools.isFinished(), elapsedText(startMs));
    }

    // ---------------- AI 评审（只读复核实体认证结论；不注册写工具，报告经 submit_review 工具落盘） ----------------

    /** 评审会话轮次上限（只读复核，预算低于生成） */
    private static final int REVIEW_MAX_ROUNDS = 40;

    /** 评审阶段键（会话层 AgentStage 上报） */
    private static final String STAGE_REVIEW = "review";

    /** 评审业务提示（角色定位 + 只读纪律）；不进 system 槽（llm 框架纪律独占），由 runReviewSession
     *  拼入 user 提示词首部；评审对象与纪律细则由 reviewPrompt 模板承载 */
    private static final String REVIEW_BUSINESS_PROMPT = "你是数据仓库语义层建模专家，"
            + "正在为用户只读评审语义层 Agent 的已有配置（只出评审结论，不改任何配置）。\n"
            + "已声明敏感字段禁止任何采样/配对/取值探索；\n"
            + "用户的答复只影响评审结论怎么写，绝不动手改配置——本链无任何写工具，修正由管理员在实体确认页完成。";

    /** 评审链 system 槽纪律增强（拼在框架规划纪律之后，仅评审链生效，仍属交互机制不沾业务）：
     *  评审是对历史人工结论的人工复核，把默认「拿不准就问」升级为「需要时尽可能向用户确认」——
     *  宁可多问一次，不默默自决；自决事项仍须 report_unconfirmed 逐条登记收口 */
    private static final String REVIEW_CONFIRM_DISCIPLINE =
            "\n\n【评审确认强化（本任务特别纪律）】"
            + "本任务要求尽可能向用户确认：凡口径有多种合理解释、单位/粒度/键/归类拿不准、"
            + "某疑点是否值得商榷摇摆不定时，优先调 ask_user 向管理员确认，而不是默默自决——"
            + "评审是对历史人工结论的复核，多问一次的代价远低于错误结论放行的代价；"
            + "确因超时/跳过而自决的事项，finish 前仍须逐条调 report_unconfirmed 登记。";

    /** AI 评审同步前置校验并拿全局串行闸（评审与生成共闸，防并发抢数据源）：
     *  异常直接抛出由会话层收口 FAILED；闸在 runReview 的 finally 释放 */
    public List<EntityDef> beginReview(String agentCode) {
        List<EntityDef> entities = smartAgentMetaService.loadEntitiesDraft(agentCode);
        if (FuncUtil.isEmpty(entities)) {
            throw new NoticeException("尚无实体草稿：请先选表生成，再进行 AI 评审");
        }
        if (!smartAgentMetaService.entitiesConfirmed(agentCode)) {
            throw new NoticeException("实体列配置未确认：评审对象是已确认结论，请到「实体」tab 逐表确认后再发起 AI 评审");
        }
        if (!sensitiveFieldsConfigured(agentCode)) {
            throw new NoticeException("敏感治理未就绪：评审探索采样按敏感声明脱敏，请先在「敏感字段」资产逐表声明");
        }
        agentTaskGate.acquire(TASK_KEY, INSTANCE_ID, LOCK_TTL_SECONDS, GATE_BUSY_MESSAGE);
        return entities;
    }

    /** AI 评审会话体（AssetReviewAgentDefinition 调用）：开数据源→只读 LLM 评审→报告由工具落盘；
     *  任务控制（闸/心跳/停止）与生成链同口径，评审链无进度记录（会话事件流已承载过程） */
    public GenTaskContext runReview(String agentCode, List<EntityDef> entities, AgentSessionContext sessionCtx) {
        GenTaskContext ctx = new GenTaskContext(agentCode, GenTaskContext.MODE_REVIEW);
        ctx.setSessionCtx(sessionCtx);
        ctx.getEntities().addAll(entities);
        currentCtx = ctx;
        currentTaskThread = Thread.currentThread();
        startHeartbeat();
        if (sessionCtx != null) {
            sessionCtx.defineStages(
                    AgentStage.of(STAGE_PREPARE, "评审预检", 10),
                    AgentStage.of(STAGE_REVIEW, "AI 评审", 900),
                    AgentStage.of(STAGE_FINISH, "收口落盘", 10));
        }
        try {
            stageStart(STAGE_PREPARE, null);
            openContext(ctx);
            stageDone(STAGE_PREPARE, "待评审实体 " + entities.size() + " 张表");
            stageStart(STAGE_REVIEW, "LLM 只读逐表复核，轮次上限 " + REVIEW_MAX_ROUNDS);
            runReviewSession(ctx);
            stageDone(STAGE_REVIEW, null);
            stageStart(STAGE_FINISH, null);
            stageDone(STAGE_FINISH, null);
        } catch (GenerationStoppedException e) {
            log.info("Agent '{}' AI 评审被用户停止", agentCode);
            ctx.setStoppedByUser(true);
        } catch (Exception e) {
            if (Thread.currentThread().isInterrupted() || stopRequested()) {
                log.info("Agent '{}' AI 评审被中断停止: {}", agentCode, e.getMessage());
                ctx.setStoppedByUser(true);
            } else {
                log.error("Agent '{}' AI 评审失败", agentCode, e);
                ctx.setFailed(true);
                ctx.setFailReason(briefCause(e));
            }
        } finally {
            stopHeartbeat();
            currentTaskThread = null;
            currentCtx = null;
            closeContext(ctx);
            agentTaskGate.release(TASK_KEY, INSTANCE_ID);
        }
        return ctx;
    }

    /** AI 评审会话（只读）：探索工具（采样佐证）+ 评审产出工具（累积/落盘报告）+ ask_user + 计划工具；
     *  不注册 AssetGenAgentTools——无 save_draft 等任何写工具，"评审不修改"由结构保证；
     *  配对登记回调置空（评审链不落任何内存态产出，探索工具登记无去处） */
    private void runReviewSession(GenTaskContext ctx) {
        long startMs = System.currentTimeMillis();
        // 流式进度门面与自主链同口径：思考/应答流式帧走 live 替换式通道，归档行走事件流
        java.util.function.Consumer<String> liveSink = line -> {
            AgentSessionContext sessionCtx = ctx.getSessionCtx();
            if (sessionCtx == null || line == null) {
                appendLog(line);
                return;
            }
            if (line.startsWith("思考中") || line.startsWith("应答中") || line.startsWith("等待 LLM")
                    || line.startsWith("流式通道")) {
                sessionCtx.pushLive(line);
            } else {
                appendLog(line);
            }
        };
        ChatLanguageModel model = buildLiveModel(liveSink, budgetOf(SmartQueryParam.REVIEW_THINKING_BUDGET));
        if (model == null) {
            throw new NoticeException("未配置 LLM 模型（llm.* 或系统参数），无法 AI 评审");
        }
        // 与生成链同前置：敏感未声明不开工（采样探索需按声明脱敏）
        requireSensitiveFieldsConfigured(ctx.getAgentCode(), null);
        loadSensitiveMarks(ctx);
        AgentExploreTools explore = new AgentExploreTools(ctx.getDataSource()::getConnection, ctx.getAgentCode(),
                ctx.getEntities(), (e, c, l) -> "评审链只读：码值配对登记已跳过", this::appendLog, this::stopRequested);
        AssetReviewTools reviewTools = new AssetReviewTools(ctx, smartAgentMetaService::saveReviewReport, this::appendLog, this::stopRequested);
        appendLog("AI 评审会话启动（只读复核 " + ctx.getEntities().size() + " 张表实体结论，轮次上限 " + REVIEW_MAX_ROUNDS + "）");
        List<Object> toolObjects = new ArrayList<>(Arrays.asList(explore, reviewTools));
        if (ctx.getSessionCtx() != null) {
            toolObjects.add(new AgentAskUserTool(ctx.getSessionCtx(), askTimeoutMinutes * 60_000L));
        }
        toolObjects.add(new AgentPlanTools(
                ctx.getSessionCtx() == null ? null : ctx.getSessionCtx().planBoard(),
                () -> stopRequested() ? "{\"error\":\"任务已被用户停止，禁止再调用任何工具\"}" : null,
                this::appendLog));
        AgentLoopResult result;
        AgentLoopOptions reviewOpt = new AgentLoopOptions(REVIEW_MAX_ROUNDS, MEMORY_WINDOW, AUTONOMOUS_PINNED_TOOLS);
        reviewOpt.setRoundExemptTools(AgentPlanTools.ROUND_EXEMPT_TOOLS);
        reviewOpt.setFailureBreaker(new AgentFailureBreaker(AgentExploreTools.exploreFailureRules()));
        // system 槽：框架规划纪律 + 评审确认强化（评审链尽可能向用户确认；业务段仍走 user 提示词）
        String systemPrompt = AutonomousSystemPrompt.PLANNING_DISCIPLINE + REVIEW_CONFIRM_DISCIPLINE;
        String userPrompt = REVIEW_BUSINESS_PROMPT + "\n\n" + buildReviewPrompt(ctx);
        appendLog("【发出提示词·system】（" + systemPrompt.length() + " 字）\n" + systemPrompt);
        appendLog("【发出提示词·user】（" + userPrompt.length() + " 字）\n" + userPrompt);
        result = toolAgentRunner.run(model, systemPrompt, userPrompt, toolObjects, reviewOpt,
                loopListener(reviewTools::isFinished, reviewTools::getFinishSummary));
        // 会话收口：末帧流式内容先归档再清空（与自主链同口径，保证终态后复制全体可带走）
        if (ctx.getSessionCtx() != null) {
            String lastLive = ctx.getSessionCtx().getState().getLive();
            if (lastLive != null && lastLive.contains("\n")) {
                appendLog("【流式末帧留痕】\n" + lastLive);
            }
            ctx.getSessionCtx().pushLive(null);
        }
        if (result.isStopped()) {
            throw new GenerationStoppedException();
        }
        appendLog("AI 评审会话结束（" + result.getRounds() + " 轮，耗时 " + elapsedText(startMs) + "）"
                + (reviewTools.isFinished() ? "：LLM 已确认完成" : "：未显式 finish，以会话结论为准"));
        if (FuncUtil.isNotEmpty(reviewTools.getFinishSummary())) {
            appendLog("评审总结：" + reviewTools.getFinishSummary());
        }
        log.info("Agent '{}' 评审会话完成：{} 轮，finish={}，report={}，耗时 {}", ctx.getAgentCode(),
                result.getRounds(), reviewTools.isFinished(), reviewTools.isReportSubmitted(), elapsedText(startMs));
    }

    /** 评审任务提示词：reviewPrompt 模板（前端可调）+ 实体结论全量 JSON（含认证标记）与敏感清单替换占位符 */
    private String buildReviewPrompt(GenTaskContext ctx) {
        return effectivePrompts(ctx.getAgentCode()).get("reviewPrompt")
                .replace(PH_ENTITIES, toCompactJson(ctx.getEntities()))
                .replace(PH_SENSITIVE, sensitiveListText(ctx));
    }

    /** 耗时可读文本（日志/过程上报用） */
    private static String elapsedText(long startMs) {
        long secs = Math.max(0, (System.currentTimeMillis() - startMs) / 1000);
        return secs >= 60 ? (secs / 60) + "分" + (secs % 60) + "秒" : secs + "秒";
    }

    /** Agent 长任务流式进度门面（llm 框架 LiveModelFactory 装配，替换式全文）：
     *  流式帧（状态行+累积全文）经业务侧 sink 分流到会话 state.live 替换式通道（抽屉实时
     *  全文展示，与问数链同口径）；轮末思考全文归档一条；AGENT 用途独立长超时；
     *  网关不支持 stream 时首应答 token 前自动降级同步回落；无 Provider 回落同步模型。
     *  思考强度走通用系统参数（thinking_budget：0/非正=最强不限制） */
    private ChatLanguageModel buildLiveModel(java.util.function.Consumer<String> live, Integer thinkingBudget) {
        return liveModelFactory.build(DbAwareModelConfigProvider.PURPOSE_AGENT, live,
                () -> chatModelProvider.getIfUnique(), thinkingBudget);
    }

    /** 通用参数取思考强度（思考 token 上限）：0/非正/读取失败=最强不限制 */
    private Integer budgetOf(SmartQueryParam param) {
        try {
            return sysConfigCacheService.getParamInt(param);
        } catch (Exception e) {
            log.warn("读取系统参数「{}」失败，思考强度回落最强不限制：{}", param.getTitle(), e.getMessage());
            return 0;
        }
    }

    /** 单类资产生成：单资产模板拼指导语 → 通用工具循环引擎（多轮探索，不支持工具时引擎内部回落直连）
     * → 解析产出覆盖对应草稿。全体生成与单独重生成共用此链路；
     * 指标多表时走逐表子链路（formula 严格单表，小上下文+独享探索预算公式更准） */
    private void generateSingleAsset(ChatLanguageModel model, GenTaskContext ctx, String assetType, String guidance, String promptKey) {
        if ("metrics".equals(assetType) && ctx.getEntities().size() > 1) {
            generateMetricsByTable(model, ctx, guidance);
            return;
        }
        String cn = assetCnName(assetType);
        String prompt = buildAssetPrompt(ctx, assetType, guidance, promptKey);
        appendLog("开始生成" + cn + "（LLM 多轮工具探索模式）");
        String answer = invokeLlm(model, ctx, prompt, cn);
        appendLog(cn + " LLM 输出已收到，解析并写入草稿");
        String json = extractJson(answer);
        JsonNode root;
        try {
            root = om.readTree(json);
        } catch (Exception e) {
            log.warn("LLM 输出解析失败（{}）: {}", assetType, answer);
            throw new NoticeException("资产 " + assetType + " 的 LLM 输出不是合法 JSON，可重试");
        }
        // metrics 单资产路径同逐表/自主链口径走后端展开：模型可能违背「不必输出」
        // 仍自填原始列名当维度清单，落库前统一覆盖消除悬空引用
        if ("metrics".equals(assetType) && root instanceof ArrayNode) {
            SupportedDimensionSupport.expand((ArrayNode) root, ctx.getEntities(), ctx.getDimensions(),
                    ctx.getSensitiveKeys(), relationsDraftForExpand(ctx.getAgentCode()));
        }
        saveLlmDraft(ctx.getAgentCode(), assetType, root);
        appendLog(cn + "生成完成");
        log.info("Agent '{}' 资产 '{}' LLM 生成完成", ctx.getAgentCode(), assetType);
    }

    /** LLM 调用（通用引擎）：探索工具随会话传入，轮次/滑窗/触顶收口/回落直连/停止信号由
     *  ToolAgentRunner 统一处理（平移自原 generateWithTools 自管循环） */
    private String invokeLlm(ChatLanguageModel model, GenTaskContext ctx, String prompt, String cn) {
        AgentExploreTools tools = new AgentExploreTools(ctx.getDataSource()::getConnection, ctx.getAgentCode(), ctx.getEntities(),
                (e, c, l) -> registerCodeLabelPair(ctx, e, c, l), this::appendLog, this::stopRequested);
        // 无 system 槽（该链无框架纪律可发）；业务提示拼入 user 首部（system 槽不沾业务）
        AgentLoopResult result = toolAgentRunner.run(model, null,
                EXPLORE_BUSINESS_PROMPT + "\n\n" + prompt + TOOL_MODE_SUFFIX + sensitiveSuffix(ctx),
                Collections.singletonList(tools),
                withBreaker(new AgentLoopOptions(MAX_TOOL_ROUNDS, MEMORY_WINDOW)), loopListener(null));
        if (result.isStopped()) {
            throw new GenerationStoppedException();
        }
        return result.getText();
    }

    /** 探索失败熔断注册（invokeLlm 同口径复用）：同类探索失败累计 3 次注入拉直指令防死磕 */
    private static AgentLoopOptions withBreaker(AgentLoopOptions opt) {
        opt.setFailureBreaker(new AgentFailureBreaker(AgentExploreTools.exploreFailureRules()));
        return opt;
    }

    /** 引擎监听器：过程日志接进度窗与会话事件流 + 停止检查接 Redis 停止键/会话键/线程中断
     *  （引擎轮头消费）+ 暂停检查点桥接会话层（阻塞至恢复，恢复指导语由引擎注入下一轮）；
     *  genTools 非空时（自主链）额外接业务终止短路：finish 收口后立即结束循环，不再多跑一轮 */
    private AgentLoopListener loopListener(AssetGenAgentTools genTools) {
        return loopListener(genTools == null ? null : genTools::isFinished,
                genTools == null ? null : genTools::getFinishSummary);
    }

    /** 引擎监听器（通用形）：业务终止短路与终态文本由供应器提供（自主链/评审链共用） */
    private AgentLoopListener loopListener(java.util.function.BooleanSupplier shouldTerminate,
                                           java.util.function.Supplier<String> terminalText) {
        return new AgentLoopListener() {
            @Override
            public void log(String line) {
                appendLog(line);
            }

            @Override
            public boolean shouldStop() {
                return stopRequested() || Thread.currentThread().isInterrupted();
            }

            @Override
            public String awaitResumeIfPaused() {
                GenTaskContext ctx = currentCtx;
                return ctx == null || ctx.getSessionCtx() == null
                        ? null : ctx.getSessionCtx().awaitResumeIfPaused();
            }

            @Override
            public boolean shouldTerminate() {
                return shouldTerminate != null && shouldTerminate.getAsBoolean();
            }

            @Override
            public String terminalText() {
                return terminalText == null ? null : terminalText.get();
            }
        };
    }

    /** 指标逐表生成：formula 严格单表（SUM(db.tbl.col)），按表拆分后每表独享小骨架上下文与
     *  工具探索预算，降低长上下文迷失导致的引错列/漏生成；逐表解析后合并即落盘（增量），
     *  停止/失联不丢已完成表，继续时 get_draft/tableMetricsDraft 天然识别本表已有指标。
     *  串行执行，避免多路并发采样压目标库 */
    private void generateMetricsByTable(ChatLanguageModel model, GenTaskContext ctx, String guidance) {
        appendLog("开始生成指标（逐表模式：" + ctx.getEntities().size() + " 张表串行，单表失败不影响其余）");
        ArrayNode merged = om.createArrayNode();
        Set<String> names = new HashSet<>();
        List<String> skipped = new ArrayList<>();
        int idx = 0;
        for (EntityDef entity : ctx.getEntities()) {
            checkStop();
            idx++;
            appendLog("生成表 " + entity.getTable() + " 的指标（" + idx + "/" + ctx.getEntities().size() + "）");
            String prompt = buildTableMetricsPrompt(ctx, entity, guidance);
            String answer;
            try {
                answer = invokeLlm(model, ctx, prompt, "指标-" + entity.getName());
            } catch (GenerationStoppedException e) {
                throw e;
            } catch (RuntimeException e) {
                log.warn("Agent '{}' 逐表指标生成失败，跳过 {}: {}", ctx.getAgentCode(), entity.getTable(), e.getMessage());
                appendLog("表 " + entity.getTable() + " 指标生成失败（" + briefCause(e) + "），跳过继续");
                skipped.add(entity.getTable());
                continue;
            }
            JsonNode arr;
            try {
                arr = om.readTree(extractJson(answer));
            } catch (Exception e) {
                log.warn("逐表指标 LLM 输出解析失败，跳过 {}: {}", entity.getTable(), answer);
                appendLog("表 " + entity.getTable() + " 指标输出不可解析，跳过继续");
                skipped.add(entity.getTable());
                continue;
            }
            for (JsonNode m : arr) {
                String name = m.path("name").asText("").toLowerCase();
                if (FuncUtil.isEmpty(name) || !names.add(name)) {
                    continue; // 无名/重名项不入合并集，交由发布校验暴露
                }
                merged.add(m);
            }
            // supported_dimensions 后端化展开（骨架+关系可达，排除敏感）：与自主链 save_draft 同口径，
            // LLM 输出不必携带也不会漏列；落盘前覆盖，停止/失联时已完成表不丢
            SupportedDimensionSupport.expand(merged, ctx.getEntities(), ctx.getDimensions(),
                    ctx.getSensitiveKeys(), relationsDraftForExpand(ctx.getAgentCode()));
            // 增量落盘：每表完成即写入草稿，停止/失联时已完成表不丢
            saveLlmDraft(ctx.getAgentCode(), "metrics", merged);
        }
        if (merged.size() == 0 && !skipped.isEmpty()) {
            throw new NoticeException("指标逐表生成全部失败：" + String.join(", ", skipped));
        }
        appendLog("指标生成完成：共 " + merged.size() + " 个"
                + (skipped.isEmpty() ? "" : "，跳过失败表：" + String.join(", ", skipped)));
        log.info("Agent '{}' 逐表指标生成完成：共 {} 个，跳过 {} 表",
                ctx.getAgentCode(), merged.size(), skipped.size());
    }

    /** 单表指标提示词：复用指标模板，只注入当前表的骨架切片（单实体/本表维度概要/本实体码值域/
     *  本表现有指标草稿）；supported_dimensions 由后端确定性展开，提示词不要求模型输出 */
    private String buildTableMetricsPrompt(GenTaskContext ctx, EntityDef entity, String guidance) {
        String table = entity.getTable() == null ? "" : entity.getTable().toLowerCase();
        List<DimensionDef> dims = ctx.getDimensions().stream()
                .filter(d -> d.getExpression() != null && d.getExpression().toLowerCase().startsWith(table + "."))
                .collect(Collectors.toList());
        List<String> domainKeys = ctx.getDomains().entrySet().stream()
                .filter(e -> entity.getName().equals(e.getValue().getEntity()))
                .map(Map.Entry::getKey)
                .collect(Collectors.toList());
        String prompt = effectivePrompts(ctx.getAgentCode()).get("metricsPrompt")
                .replace(PH_ENTITIES, toCompactJson(Collections.singletonList(entity)))
                .replace(PH_DIMENSIONS, toCompactJson(dims))
                .replace(PH_DOMAIN_KEYS, domainKeys.isEmpty() ? "（无）" : String.join(", ", domainKeys))
                .replace(PH_CURRENT, tableMetricsDraft(ctx.getAgentCode(), table));
        if (FuncUtil.isNotEmpty(guidance)) {
            prompt += "\n\n【人工指导语】（优先遵循，与上述规则冲突时以指导语为准）\n" + guidance.trim();
        }
        return prompt;
    }

    /** 现有指标草稿的本表切片（source_table 归一匹配；无草稿/无匹配时（无）） */
    private String tableMetricsDraft(String agentCode, String table) {
        InsightAgentAsset asset = smartAgentMetaService.getAsset(agentCode, "metrics");
        if (asset == null || FuncUtil.isEmpty(asset.getContent()) || FuncUtil.isEmpty(table)) {
            return "（无）";
        }
        try {
            JsonNode arr = om.readTree(asset.getContent());
            ArrayNode mine = om.createArrayNode();
            for (JsonNode m : arr) {
                if (table.equals(m.path("source_table").asText("").toLowerCase())) {
                    mine.add(m);
                }
            }
            return mine.size() == 0 ? "（无）" : om.writeValueAsString(mine);
        } catch (Exception e) {
            return "（无）";
        }
    }

    /** 关系草稿解析（维度展开可达性依据）：无草稿/不可解析返 null（回落仅本表维度） */
    private JsonNode relationsDraftForExpand(String agentCode) {
        InsightAgentAsset asset = smartAgentMetaService.getAsset(agentCode, "relations");
        if (asset == null || FuncUtil.isEmpty(asset.getContent())) {
            return null;
        }
        try {
            return om.readTree(asset.getContent());
        } catch (Exception e) {
            log.warn("Agent '{}' relations 草稿解析失败，维度展开仅含本表维度: {}", agentCode, e.getMessage());
            return null;
        }
    }

    /** LLM 推断码值配对：从实体字段清单判断哪些是「编码字段↔业务名称字段」配对，
     * 验证真实存在后全表 GROUP BY 采样真实 code→label 映射合入 domains（含补维度与回填 value_domain）；
     * 补齐属增强步骤：解析失败/调用失败（含超时）仅告警不阻断四类生成 */
    private void inferAndSampleCodeLabelPairs(ChatLanguageModel model, GenTaskContext ctx, String promptKey) {
        // 逐表推断：全量实体一把推断上下文过大易超时，拆成单表小请求（推断逐表，
        // GROUP BY 采样本就是后端登记时逐对执行，不经过 LLM）；单表失败跳过不阻断
        appendLog("开始逐表推断编码↔名称配对（" + ctx.getEntities().size() + " 张表，推断后由后端采样补齐码值域）");
        int added = 0;
        List<String> skipped = new ArrayList<>();
        int idx = 0;
        for (EntityDef entity : ctx.getEntities()) {
            checkStop();
            idx++;
            String answer;
            try {
                answer = model.generate(buildPairPrompt(ctx, entity, promptKey));
            } catch (RuntimeException e) {
                log.warn("Agent '{}' 表 {} 配对推断失败，跳过: {}", ctx.getAgentCode(), entity.getTable(), e.getMessage());
                appendLog("表 " + entity.getTable() + " 配对推断失败（" + briefCause(e) + "），跳过继续（" + idx + "/" + ctx.getEntities().size() + "）");
                skipped.add(entity.getTable());
                continue;
            }
            JsonNode root;
            try {
                root = om.readTree(extractJson(answer));
            } catch (Exception e) {
                log.warn("表 {} 配对推断输出解析失败，跳过: {}", entity.getTable(), answer);
                appendLog("表 " + entity.getTable() + " 配对推断输出不可解析，跳过继续（" + idx + "/" + ctx.getEntities().size() + "）");
                skipped.add(entity.getTable());
                continue;
            }
            int tableAdded = 0;
            for (JsonNode p : root.path("pairs")) {
                String result = registerCodeLabelPair(ctx, p.path("entity").asText(null),
                        p.path("code_field").asText(null), p.path("label_field").asText(null));
                if (result.startsWith("已登记")) {
                    tableAdded++;
                }
            }
            // 备注枚举域：同表无 code↔name 配对时，从列备注挖出的码值直接出域（不采样）
            int commentAdded = 0;
            for (JsonNode cd : root.path("comment_domains")) {
                String result = registerCommentDomain(ctx, cd.path("entity").asText(null),
                        cd.path("field").asText(null), cd.path("stored_as").asText(null), cd.path("values"));
                if (result.startsWith("已登记")) {
                    commentAdded++;
                }
            }
            added += tableAdded + commentAdded;
            appendLog("表 " + entity.getTable() + " 配对推断完成（" + idx + "/" + ctx.getEntities().size()
                    + "），新增码值域 " + (tableAdded + commentAdded) + " 个（配对 " + tableAdded
                    + "/备注枚举 " + commentAdded + "）");
        }
        appendLog("码值配对推断完成，新增 " + added + " 个码值域"
                + (skipped.isEmpty() ? "" : "，跳过失败表：" + String.join(", ", skipped)));
        log.info("Agent '{}' LLM 逐表推断码值配对新增 {} 个码值域，跳过 {} 表",
                ctx.getAgentCode(), added, skipped.size());
    }

    /** 登记一对编码↔名称配对：防幻觉校验 + GROUP BY 采样 + 合入 domains（含补维度与回填 value_domain）。
     * 供两条链路共用：配对推断提示词批量产出、探索工具 register_code_label_pair 逐对登记；
     * 返回值描述结果（「已登记」开头=成功，供工具回传 LLM） */
    private String registerCodeLabelPair(GenTaskContext ctx, String entityName, String codeField, String labelField) {
        if (FuncUtil.isEmpty(entityName) || FuncUtil.isEmpty(codeField)
                || FuncUtil.isEmpty(labelField) || codeField.equals(labelField)) {
            return "跳过：参数缺失或编码列与名称列相同";
        }
        EntityDef entity = ctx.getEntities().stream()
                .filter(e -> entityName.equals(e.getName())).findFirst().orElse(null);
        if (entity == null) {
            return "跳过：实体 " + entityName + " 不存在";
        }
        // 防幻觉：两字段须真实存在于实体
        EntityDef.EntityFieldDef code = entity.getFields().stream()
                .filter(f -> codeField.equals(f.getName())).findFirst().orElse(null);
        boolean hasLabel = entity.getFields().stream()
                .anyMatch(f -> labelField.equals(f.getName()));
        if (code == null || !hasLabel) {
            return "跳过：字段 " + codeField + "/" + labelField + " 不存在于实体 " + entityName;
        }
        // 出向代码强制层：配对任一列已标记敏感则拒绝采样（不依赖 LLM 自律，两条链路共用）
        if (isSensitiveField(ctx, entityName, codeField)) {
            return "跳过：" + codeField + " 已标记为敏感字段，禁止采样其真实取值";
        }
        if (isSensitiveField(ctx, entityName, labelField)) {
            return "跳过：" + labelField + " 已标记为敏感字段，禁止采样其真实取值";
        }
        // 骨架确定性配对已处理的跳过
        boolean exists = ctx.getDomains().values().stream().anyMatch(d ->
                entityName.equals(d.getEntity()) && codeField.equals(d.getField()));
        if (exists) {
            return "跳过：" + entityName + "." + codeField + " 已有码值域";
        }
        List<ValueDomainDef.DomainValue> vals = skeletonBuilder
                .sampleCodeLabelPairs(ctx.getConn(), entity.getTable(), codeField, labelField);
        if (vals.isEmpty()) {
            return "跳过：采样为空或基数超过上限（非枚举列）";
        }
        // 维度 key：优先复用该列已有维度（表达式=table.col），没有则补建（Integer 编码列骨架无维度）
        String expression = entity.getTable() + "." + codeField;
        String dimName = ctx.getDimensions().stream()
                .filter(d -> expression.equals(d.getExpression()))
                .map(DimensionDef::getName).findFirst().orElse(null);
        if (dimName == null) {
            String base = codeField;
            dimName = base;
            int i = 1;
            while (ctx.getDimensionNames().contains(dimName)) {
                dimName = base + "_" + i++;
            }
            DimensionDef dim = new DimensionDef();
            dim.setName(dimName);
            dim.setDisplayName(code.getDisplayName());
            dim.setExpression(expression);
            List<String> aliases = CommentValueParser.extractAliases(code.getDisplayName(), code.getDisplayName());
            if (!aliases.isEmpty()) {
                dim.setAliases(aliases);
            }
            ctx.getDimensions().add(dim);
            ctx.getDimensionNames().add(dimName);
        }
        ValueDomainDef domain = new ValueDomainDef();
        domain.setEntity(entityName);
        domain.setField(codeField);
        // 与骨架配对同口径：域锚定码列，storedAs=code（过滤名称自动转码、分组码自动转名）
        domain.setStoredAs("code");
        domain.setValues(vals);
        ctx.getDomains().put(dimName, domain);
        code.setValueDomain(dimName);
        return "已登记：维度 " + dimName + "（采样 " + vals.size() + " 组真实映射）";
    }

    /** 备注枚举域登记上限（防 LLM 把长清单全搬进来，码值域定位是小基数枚举） */
    private static final int COMMENT_DOMAIN_VALUE_LIMIT = 50;

    /** 登记一个备注推断的码值域：防幻觉校验（实体/字段存在）+ 敏感拦截 + 已有域跳过，
     *  值集取自列备注解析（不经采样）；补齐维度与回填 value_domain 与配对登记同口径。
     *  与骨架规则层互补：正则只能识别标准码值模式（0=启用），不规范备注（在建/完工枚举、
     *  分隔符变体）由 LLM 挖出 */
    private String registerCommentDomain(GenTaskContext ctx, String entityName, String field, String storedAs, JsonNode values) {
        if (FuncUtil.isEmpty(entityName) || FuncUtil.isEmpty(field)) {
            return "跳过：参数缺失";
        }
        EntityDef entity = ctx.getEntities().stream()
                .filter(e -> entityName.equals(e.getName())).findFirst().orElse(null);
        if (entity == null) {
            return "跳过：实体 " + entityName + " 不存在";
        }
        EntityDef.EntityFieldDef fd = entity.getFields().stream()
                .filter(f -> field.equals(f.getName())).findFirst().orElse(null);
        if (fd == null) {
            return "跳过：字段 " + field + " 不存在于实体 " + entityName;
        }
        if (isSensitiveField(ctx, entityName, field)) {
            return "跳过：" + field + " 已标记为敏感字段，禁止建立码值域";
        }
        boolean exists = ctx.getDomains().values().stream().anyMatch(d ->
                entityName.equals(d.getEntity()) && field.equals(d.getField()));
        if (exists) {
            return "跳过：" + entityName + "." + field + " 已有码值域";
        }
        List<ValueDomainDef.DomainValue> vals = new ArrayList<>();
        Set<String> codes = new HashSet<>();
        if (values != null && values.isArray()) {
            for (JsonNode v : values) {
                String code = v.path("code").asText("").trim();
                String label = v.path("label").asText("").trim();
                if (FuncUtil.isEmpty(code) || FuncUtil.isEmpty(label) || !codes.add(code.toLowerCase())) {
                    continue;
                }
                ValueDomainDef.DomainValue dv = new ValueDomainDef.DomainValue();
                dv.setCode(code);
                dv.setLabel(label);
                vals.add(dv);
                if (vals.size() >= COMMENT_DOMAIN_VALUE_LIMIT) {
                    break;
                }
            }
        }
        if (vals.isEmpty()) {
            return "跳过：" + entityName + "." + field + " 备注枚举值为空";
        }
        // 维度复用/补建与配对登记同口径（Integer 枚举列骨架可能无维度）
        String expression = entity.getTable() + "." + field;
        String dimName = ctx.getDimensions().stream()
                .filter(d -> expression.equals(d.getExpression()))
                .map(DimensionDef::getName).findFirst().orElse(null);
        if (dimName == null) {
            dimName = field;
            int i = 1;
            while (ctx.getDimensionNames().contains(dimName)) {
                dimName = field + "_" + i++;
            }
            DimensionDef dim = new DimensionDef();
            dim.setName(dimName);
            dim.setDisplayName(fd.getDisplayName());
            dim.setExpression(expression);
            List<String> aliases = CommentValueParser.extractAliases(fd.getDisplayName(), fd.getDisplayName());
            if (!aliases.isEmpty()) {
                dim.setAliases(aliases);
            }
            ctx.getDimensions().add(dim);
            ctx.getDimensionNames().add(dimName);
        }
        ValueDomainDef domain = new ValueDomainDef();
        domain.setEntity(entityName);
        domain.setField(field);
        // 列物理存码则 code（名称过滤自动转码/分组转名）；存可读文本则 label——以 LLM 对列类型的判断为准
        domain.setStoredAs("label".equalsIgnoreCase(storedAs) ? "label" : "code");
        domain.setValues(vals);
        ctx.getDomains().put(dimName, domain);
        fd.setValueDomain(dimName);
        return "已登记：维度 " + dimName + "（备注枚举 " + vals.size() + " 项）";
    }

    // ---------------- 敏感字段前置闸与强制层 ----------------

    /** 敏感字段治理是否就绪（统一口径见 SmartAgentMetaService#sensitiveGoverned：
     *  fields 非空或显式确认无敏感列） */
    private boolean sensitiveFieldsConfigured(String agentCode) {
        return smartAgentMetaService.sensitiveGoverned(agentCode);
    }

    /** 前置闸：LLM 生成前两道人工闸——① 实体列配置逐表确认（角色/单位/键/分区是生成原料，
     *  未确认时 LLM 会自己重推一遍这些判断）；② 逐表敏感声明（配对推断/工具探索会采样真实映射，
     *  否则项目编码↔项目名称这类列会被当配对采样，真实取值落入码值域资产）。
     *  regenType 非空时为单资产重生成（敏感字段类自身重生不受闸——它就是配置入口） */
    private void requireSensitiveFieldsConfigured(String agentCode, String regenType) {
        if ("sensitive-fields".equals(regenType)) {
            return;
        }
        if (!smartAgentMetaService.entitiesConfirmed(agentCode)) {
            throw new NoticeException("实体列配置未确认：请到「实体」tab 逐表过目列角色/单位/业务键/分区并点「确认本表」"
                    + "（懒人路径可「一键采用全部预选」），全部表确认后再执行 AI 生成；"
                    + "也可先用「仅生成骨架」，确认后再补 LLM 生成");
        }
        if (!sensitiveFieldsConfigured(agentCode)) {
            throw new NoticeException("敏感治理未就绪：请在「敏感字段」资产中逐表声明——"
                    + "每张表或标记敏感列（可配替换列）或确认「该表无敏感字段」，"
                    + "全部表处理完后再执行 LLM 生成；也可先用「仅生成骨架」，配置后再补 LLM 生成");
        }
    }

    /** 加载敏感列标记（任务上下文）：优先新形态 tables[]（按表声明，fields[] 的 field 拼 entity.field），
     *  兼容旧形态顶层 fields[]；键小写归一 */
    private void loadSensitiveMarks(GenTaskContext ctx) {
        Set<String> keys = new HashSet<>();
        InsightAgentAsset sf = smartAgentMetaService.getAsset(ctx.getAgentCode(), "sensitive-fields");
        if (sf != null && FuncUtil.isNotEmpty(sf.getContent())) {
            try {
                JsonNode root = om.readTree(sf.getContent());
                JsonNode tables = root.path("tables");
                if (tables.isArray() && tables.size() > 0) {
                    for (JsonNode t : tables) {
                        String entity = t.path("entity").asText("").trim();
                        for (JsonNode f : t.path("fields")) {
                            addSensitiveKey(keys, entity, f.path("field").asText(""));
                        }
                    }
                } else {
                    for (JsonNode f : root.path("fields")) {
                        addSensitiveKey(keys, f.path("entity").asText(""), f.path("field").asText(""));
                    }
                }
            } catch (Exception e) {
                log.warn("Agent '{}' 敏感字段草稿解析失败，按未标记处理: {}", ctx.getAgentCode(), e.getMessage());
            }
        }
        ctx.setSensitiveKeys(keys);
    }

    /** entity.field 键归一入集（空值防护：点两侧都非空才有效） */
    private void addSensitiveKey(Set<String> keys, String entity, String field) {
        if (FuncUtil.isEmpty(entity) || FuncUtil.isEmpty(field)) {
            return;
        }
        keys.add((entity + "." + field).toLowerCase());
    }

    /** 某列是否已标记敏感（标记未加载时一律放行——骨架首次生成时还没有敏感配置） */
    private boolean isSensitiveField(GenTaskContext ctx, String entityName, String field) {
        return ctx.getSensitiveKeys() != null
                && ctx.getSensitiveKeys().contains((entityName + "." + field).toLowerCase());
    }

    /** 清理码值域中覆盖敏感列的条目：骨架确定性配对采样可能已把项目编码↔名称等真实映射
     *  落入 domains，敏感字段配置后须在 LLM 生成前清除，避免随草稿发布外泄 */
    private void purgeSensitiveDomains(GenTaskContext ctx) {
        if (ctx.getSensitiveKeys() == null || ctx.getSensitiveKeys().isEmpty()) {
            return;
        }
        Iterator<Map.Entry<String, ValueDomainDef>> it = ctx.getDomains().entrySet().iterator();
        int removed = 0;
        while (it.hasNext()) {
            ValueDomainDef d = it.next().getValue();
            if (isSensitiveField(ctx, d.getEntity(), d.getField())) {
                it.remove();
                removed++;
            }
        }
        // 同步解除实体字段上的 value_domain 引用（指向已删码值域，否则发布校验报码值域缺失）
        for (EntityDef e : ctx.getEntities()) {
            for (EntityDef.EntityFieldDef f : e.getFields()) {
                if (f.getValueDomain() != null && isSensitiveField(ctx, e.getName(), f.getName())) {
                    f.setValueDomain(null);
                }
            }
        }
        if (removed > 0) {
            appendLog("已清理 " + removed + " 个覆盖敏感列的码值域（防真实取值外泄）");
            log.info("Agent '{}' 清理覆盖敏感列的码值域 {} 个", ctx.getAgentCode(), removed);
        }
    }

    // 工具循环引擎（原 generateWithTools/trimToolMemory/LoggingModel/isToolUnsupported/isToolLoopExceeded）
    // 已下沉至 llm 模块 ToolAgentRunner 统一实现，业务侧仅经 invokeLlm 调用（见 com.bidr.llm.agent）

    /** 默认提示词模板（前端「恢复默认」与未保存时兜底同源）：classpath 资源 prompts.yml，
     *  首次读取后缓存；资源缺失/解析失败属打包问题，直接抛异常阻断生成 */
    public Map<String, String> defaultPrompts() {
        return new LinkedHashMap<>(loadDefaultPrompts());
    }

    private static Map<String, String> loadDefaultPrompts() {
        Map<String, String> cache = defaultPromptCache;
        if (cache == null) {
            synchronized (SmartAgentAssetGenerateService.class) {
                cache = defaultPromptCache;
                if (cache == null) {
                    // 按功能分文件：生成链 + 评审链（键集不相交，合并后与单文件时同构）
                    cache = new LinkedHashMap<>();
                    for (String resource : new String[]{PROMPTS_RESOURCE, PROMPTS_REVIEW_RESOURCE}) {
                        cache.putAll(loadYamlPrompts(resource));
                    }
                    defaultPromptCache = cache;
                }
            }
        }
        return cache;
    }

    /** 单个提示词模板资源加载：顶层键值对（值为多行文本）；缺失/为空属打包问题，抛异常阻断；
     *  包内可见供回归测试校验资源完备性 */
    static Map<String, String> loadYamlPrompts(String resource) {
        try (java.io.InputStream in = SmartAgentAssetGenerateService.class
                .getClassLoader().getResourceAsStream(resource)) {
            if (in == null) {
                throw new IllegalStateException("提示词模板资源缺失: " + resource);
            }
            Map<String, Object> raw = new org.yaml.snakeyaml.Yaml().load(in);
            if (raw == null || raw.isEmpty()) {
                throw new IllegalStateException("提示词模板资源为空: " + resource);
            }
            Map<String, String> m = new LinkedHashMap<>();
            for (Map.Entry<String, Object> e : raw.entrySet()) {
                if (e.getValue() != null) {
                    m.put(e.getKey(), String.valueOf(e.getValue()));
                }
            }
            return m;
        } catch (java.io.IOException e) {
            throw new IllegalStateException("提示词模板资源读取失败: " + resource, e);
        }
    }

    /** 生效提示词：读取 llm-prompts 草稿（前端调优保存），缺省项以内置默认补齐 */
    public Map<String, String> effectivePrompts(String agentCode) {
        Map<String, String> m = defaultPrompts();
        InsightAgentAsset asset = smartAgentMetaService.getAsset(agentCode, PROMPTS_ASSET_TYPE);
        if (asset != null && FuncUtil.isNotEmpty(asset.getContent())) {
            try {
                JsonNode node = om.readTree(asset.getContent());
                for (String key : m.keySet()) {
                    String saved = node.path(key).asText(null);
                    if (FuncUtil.isNotEmpty(saved)) {
                        m.put(key, saved);
                    }
                }
            } catch (Exception e) {
                log.warn("Agent '{}' 提示词模板解析失败，使用内置默认", agentCode);
            }
        }
        return m;
    }

    /** 保存提示词模板（下次生成立即生效，无需重启）：入参键与 defaultPrompts 一致；
     *  合并写回——同一 llm-prompts 资产还存维护问数的 parsePrompt/maintainPrompt，不可整体覆盖丢失 */
    public void savePrompts(String agentCode, Map<String, String> prompts) {
        ObjectNode node = om.createObjectNode();
        InsightAgentAsset existed = smartAgentMetaService.getAsset(agentCode, PROMPTS_ASSET_TYPE);
        if (existed != null && FuncUtil.isNotEmpty(existed.getContent())) {
            try {
                JsonNode old = om.readTree(existed.getContent());
                if (old.isObject()) {
                    node.setAll((ObjectNode) old);
                }
            } catch (Exception e) {
                log.warn("Agent '{}' 旧提示词模板解析失败，仅保存本次键", agentCode);
            }
        }
        for (String key : defaultPrompts().keySet()) {
            String value = prompts == null ? null : prompts.get(key);
            node.put(key, value == null ? "" : value);
        }
        try {
            smartAgentMetaService.saveAssetDraft(agentCode, PROMPTS_ASSET_TYPE, om.writeValueAsString(node));
        } catch (Exception e) {
            throw new NoticeException("提示词模板保存失败: " + e.getMessage());
        }
    }

    /** 已声明敏感列清单文本：配对推断提示词注入 + 工具探索后缀共用；
     *  未声明时给语义判断提示（骨架首生等场景） */
    private String sensitiveListText(GenTaskContext ctx) {
        if (ctx.getSensitiveKeys() == null || ctx.getSensitiveKeys().isEmpty()) {
            return "（暂无已声明敏感字段；仍须按字段语义谨慎判断——姓名/证件号/手机号/编码名称对等涉密列不得入配对采样）";
        }
        return String.join(", ", new java.util.TreeSet<>(ctx.getSensitiveKeys()));
    }

    /** 工具探索后缀追加的敏感清单段：模板不含占位符也能把声明清单带给 LLM */
    private String sensitiveSuffix(GenTaskContext ctx) {
        return "\n\n【已声明敏感字段】（禁止任何采样/配对/取值探索）：" + sensitiveListText(ctx);
    }

    /** 配对推断提示词（单表版）：模板（前端可调）+ 单实体骨架/敏感清单替换占位符；
     *  逐表小请求避免全量骨架上下文过大导致超时 */
    private String buildPairPrompt(GenTaskContext ctx, EntityDef entity, String promptKey) {
        return effectivePrompts(ctx.getAgentCode()).get(promptKey)
                .replace(PH_ENTITIES, toCompactJson(Collections.singletonList(entity)))
                .replace(PH_SENSITIVE, sensitiveListText(ctx));
    }

    /** LLM 输出节点落草稿：缺省节点跳过；认证语义合并（已认证现存项保留不删、未认证清空，
     *  集中 mergeLlmDraftWithCertified，null 回落直接覆盖） */
    private void saveLlmDraft(String agentCode, String assetType, JsonNode node) {
        if (node == null || node.isMissingNode() || node.isNull()) {
            return;
        }
        try {
            JsonNode merged = certifiedDraftMerger.mergeLlmDraftWithCertified(agentCode, assetType, node);
            smartAgentMetaService.saveAssetDraft(agentCode, assetType,
                    om.writerWithDefaultPrettyPrinter().writeValueAsString(merged != null ? merged : node));
        } catch (Exception e) {
            throw new NoticeException("资产 '" + assetType + "' 序列化失败: " + e.getMessage());
        }
    }

    /** 单类资产生成提示词：单资产模板（前端可调）+ 运行时骨架/现有草稿替换占位符 + 按需拼接人工指导语 */
    private String buildAssetPrompt(GenTaskContext ctx, String assetType, String guidance, String promptKey) {
        String template = effectivePrompts(ctx.getAgentCode()).get(promptKey);
        String prompt = template
                .replace(PH_ENTITIES, toCompactJson(ctx.getEntities()))
                .replace(PH_DIMENSIONS, toCompactJson(ctx.getDimensions()))
                .replace(PH_DOMAIN_KEYS, ctx.getDomains().isEmpty() ? "（无）" : String.join(", ", ctx.getDomains().keySet()))
                .replace(PH_TABLE_PROFILES, tableProfilesText(ctx))
                .replace(PH_COLUMN_ROLES, columnRolesText(ctx))
                .replace(PH_CURRENT, currentDraftContent(ctx.getAgentCode(), assetType));
        if (FuncUtil.isNotEmpty(guidance)) {
            prompt += "\n\n【人工指导语】（优先遵循，与上述规则冲突时以指导语为准）\n" + guidance.trim();
        }
        return prompt;
    }

    /** 自主模式任务提示词：模板（前端可调）+ 骨架压缩 JSON/敏感清单替换占位符；
     *  模板含继续生成语义（已有草稿非失败不重做），LLM 凭 list_status/get_draft 增量续作；
     *  维度段只注概要（逐表计数+示例名），明细按需 get_dimensions 工具查——
     *  全量维度名注入曾是思考膨胀燃料，指标 supported_dimensions 后端展开后模型更无需全量清单 */
    private String buildAutonomousPrompt(GenTaskContext ctx) {
        return effectivePrompts(ctx.getAgentCode()).get("autonomousPrompt")
                .replace(PH_ENTITIES, toCompactJson(ctx.getEntities()))
                .replace(PH_DIMENSIONS, dimensionsSummaryText(ctx))
                .replace(PH_DOMAIN_KEYS, ctx.getDomains().isEmpty() ? "（无）" : String.join(", ", ctx.getDomains().keySet()))
                .replace(PH_TABLE_PROFILES, tableProfilesText(ctx))
                .replace(PH_COLUMN_ROLES, columnRolesText(ctx))
                .replace(PH_SENSITIVE, sensitiveListText(ctx));
    }

    /** 【维度骨架】概要段渲染：逐表一行（维度数 + 前 3 个示例名），明细由 get_dimensions 工具按需查；
     *  同名维度按 expression 归属各自实体的规则不变，仅展示形态从全量 JSON 收为概要 */
    private static String dimensionsSummaryText(GenTaskContext ctx) {
        if (ctx.getDimensions() == null || ctx.getDimensions().isEmpty()) {
            return "（无）";
        }
        Map<String, List<String>> byTable = new LinkedHashMap<>();
        for (DimensionDef d : ctx.getDimensions()) {
            String expr = d.getExpression();
            String table = FuncUtil.isEmpty(expr) ? "（其他）" : expr;
            int dot2 = expr == null ? -1 : expr.indexOf('.', expr.indexOf('.') + 1);
            if (dot2 > 0) {
                table = expr.substring(0, dot2);
            }
            byTable.computeIfAbsent(table, k -> new ArrayList<>()).add(d.getName());
        }
        List<String> lines = new ArrayList<>();
        for (Map.Entry<String, List<String>> en : byTable.entrySet()) {
            List<String> names = en.getValue();
            String sample = String.join(", ", names.subList(0, Math.min(3, names.size())));
            lines.add("- " + en.getKey() + "：" + names.size() + " 个维度（如 " + sample
                    + (names.size() > 3 ? " 等" : "") + "）");
        }
        lines.add("共 " + ctx.getDimensions().size() + " 个；明细（显示名/表达式/粒度）用 get_dimensions 工具按表或关键词查，不必全量枚举");
        return String.join("\n", lines);
    }

    /** 【列角色】段渲染：逐实体一行（度量带单位/维度带时间粒度/忽略列计数 + 业务键 + 分区列），
     *  已人工确认的实体标注（人工已确认，禁止再核实）——人工结论传递给 LLM 的通道，
     *  单位/角色/键这些最贵猜测由人 10 秒钉死，LLM 不再花轮次重推 */
    private static String columnRolesText(GenTaskContext ctx) {
        if (ctx.getEntities() == null || ctx.getEntities().isEmpty()) {
            return "（无）";
        }
        List<String> lines = new ArrayList<>();
        for (EntityDef e : ctx.getEntities()) {
            List<String> metrics = new ArrayList<>();
            List<String> dims = new ArrayList<>();
            int ignored = 0;
            for (EntityDef.EntityFieldDef f : e.getFields() == null
                    ? Collections.<EntityDef.EntityFieldDef>emptyList() : e.getFields()) {
                // 禁用列不参与 LLM 输入：不进度量/维度/忽略任何一类统计（与 toCompactJson 剔除同口径）
                if (Boolean.TRUE.equals(f.getDisabled())) {
                    continue;
                }
                if ("metric".equalsIgnoreCase(f.getRole())) {
                    metrics.add(f.getName() + (FuncUtil.isNotEmpty(f.getUnit()) ? "(" + f.getUnit() + ")" : ""));
                } else if ("dimension".equalsIgnoreCase(f.getRole())) {
                    dims.add(f.getName() + ("Date".equals(f.getType()) && FuncUtil.isNotEmpty(f.getGranularity())
                            ? "(" + f.getGranularity() + ")" : ""));
                } else if ("ignore".equalsIgnoreCase(f.getRole())) {
                    ignored++;
                }
            }
            StringBuilder sb = new StringBuilder("- ").append(e.getName());
            if (!metrics.isEmpty()) {
                sb.append("：度量 ").append(String.join("/", metrics));
            }
            if (!dims.isEmpty()) {
                sb.append("；维度 ").append(String.join("/", dims));
            }
            if (ignored > 0) {
                sb.append("；忽略 ").append(ignored).append(" 列");
            }
            if (e.getPrimaryKey() != null && !e.getPrimaryKey().isEmpty()) {
                sb.append("；业务键 ").append(String.join("+", e.getPrimaryKey()));
            }
            if (FuncUtil.isNotEmpty(e.getPartitionColumn())) {
                sb.append("；分区列 ").append(e.getPartitionColumn());
            }
            if (FuncUtil.isNotEmpty(e.getSnapshotType())) {
                sb.append("；快照 ").append(e.getSnapshotType());
            }
            sb.append(Boolean.TRUE.equals(e.getConfirmed())
                    ? "（人工已确认，禁止再核实）" : "（未确认，可用工具自行核实）");
            lines.add(sb.toString());
        }
        return String.join("\n", lines);
    }

    /** 表画像段渲染：逐行拼接（每表一行），未采集/全部失败时（无）——旧模板不含
     *  占位符时 replace 无匹配零影响，行为与画像前完全一致 */
    private static String tableProfilesText(GenTaskContext ctx) {
        return ctx.getTableProfiles().isEmpty() ? "（无）" : String.join("\n", ctx.getTableProfiles());
    }

    /** 资产类型 → 提示词模板键 */
    private String promptKeyOf(String assetType) {
        switch (assetType) {
            case "metrics":
                return "metricsPrompt";
            case "relations":
                return "relationsPrompt";
            case "concepts":
                return "conceptsPrompt";
            default:
                throw new NoticeException("资产类型不支持 LLM 生成: " + assetType);
        }
    }

    /** 该类现有草稿内容（供 {current_content} 占位符；无草稿时（无）） */
    private String currentDraftContent(String agentCode, String assetType) {
        InsightAgentAsset asset = smartAgentMetaService.getAsset(agentCode, assetType);
        if (asset == null || FuncUtil.isEmpty(asset.getContent())) {
            return "（无）";
        }
        return asset.getContent();
    }

    /** 骨架压缩：只保留提示词需要的字段（字段列表只送 name/display_name/type）；
     *  实体清单先经 EntityDef.forLlmInput 剔除禁用列（禁用列不参与 LLM 输入） */
    @SuppressWarnings("unchecked")
    private String toCompactJson(List<?> list) {
        try {
            List<?> items = (!list.isEmpty() && list.get(0) instanceof EntityDef)
                    ? EntityDef.forLlmInput((List<EntityDef>) list) : list;
            ArrayNode compact = om.createArrayNode();
            for (Object item : items) {
                ObjectNode node = om.valueToTree(item);
                node.remove("aliases");
                node.remove("base_entity");
                node.remove("display_fields");
                node.remove("default_filters");
                compact.add(node);
            }
            return om.writeValueAsString(compact);
        } catch (Exception e) {
            throw new IllegalStateException("骨架序列化失败", e);
        }
    }

    /** 从模型回答中提取 JSON：优先 ```json 代码块，否则取首尾大括号/中括号（单资产产出可为数组） */
    private String extractJson(String answer) {
        if (FuncUtil.isEmpty(answer)) {
            throw new NoticeException("LLM 返回为空");
        }
        java.util.regex.Matcher m = java.util.regex.Pattern
                .compile("```json\\s*([\\s\\S]*?)\\s*```").matcher(answer);
        if (m.find()) {
            return m.group(1);
        }
        int brace = answer.indexOf('{');
        int bracket = answer.indexOf('[');
        boolean array = bracket >= 0 && (brace < 0 || bracket < brace);
        int start = array ? bracket : brace;
        int end = array ? answer.lastIndexOf(']') : answer.lastIndexOf('}');
        if (start >= 0 && end > start) {
            return answer.substring(start, end + 1);
        }
        throw new NoticeException("LLM 回答中未找到 JSON，可重试");
    }

    // ---------------- 单资产重生成（与全体生成共用全局闸门/心跳/停止收口与 generateSingleAsset 链路） ----------------

    /** 单资产重生成同步前置校验：占全局闸门，类型/骨架草稿/数据源/模型就绪才放行异步执行 */
    public void beginRegenerate(String agentCode, String assetType) {
        if (!REGEN_ASSET_TYPES.contains(assetType)) {
            throw new NoticeException("仅指标/关系/概念支持 LLM 单独重生成（敏感字段请逐表人工声明）");
        }
        agentTaskGate.acquire(TASK_KEY, INSTANCE_ID, LOCK_TTL_SECONDS, GATE_BUSY_MESSAGE);
        try {
            InsightAgent agent = insightAgentService.selectOne(
                    new QueryWrapper<InsightAgent>().eq("agent_code", agentCode));
            if (agent == null) {
                throw new NoticeException("Agent [" + agentCode + "] 不存在");
            }
            if (chatModelProvider.getIfUnique() == null) {
                throw new NoticeException("未配置 LLM 模型（llm.* 或系统参数），无法 LLM 生成");
            }
            // 骨架上下文须已生成（单独重生成以草稿骨架为提示词上下文）——此处仅同步校验存在性，完整解析在异步任务
            InsightAgentAsset entAsset = smartAgentMetaService.getAsset(agentCode, "entities");
            if (entAsset == null || FuncUtil.isEmpty(entAsset.getContent())) {
                throw new NoticeException("尚无骨架资产，请先执行「LLM 生成草稿」或「仅生成骨架」");
            }
            // 前置闸：除敏感字段自身重生（它就是配置入口）外，须先配置敏感字段才放行
            requireSensitiveFieldsConfigured(agentCode, assetType);
            dataSourceCacheService.getDataSource(agent.getDsName());
        } catch (Exception e) {
            agentTaskGate.release(TASK_KEY, INSTANCE_ID);
            throw e;
        }
    }

    /** 单资产重生成异步执行（@Async 经代理生效）：进度 total=1，完成后前端轮询同一进度接口；
     *  与全体生成共用全局闸门/心跳/停止收口（可中途停止，已完成部分草稿保留） */
    @Async
    public void handleRegenerate(String agentCode, String assetType, String guidance) {
        GenTaskContext ctx = new GenTaskContext(agentCode, GenTaskContext.MODE_PIPELINE);
        currentTaskThread = Thread.currentThread();
        startHeartbeat();
        try {
            startRegenProgress(assetType);
            loadSkeletonContext(ctx);
            // 与全体生成同链路：敏感标记强制层 + 草稿残留清理
            loadSensitiveMarks(ctx);
            purgeSensitiveDomains(ctx);
            openContext(ctx);
            // 与全体生成同链路：包日志代理，重生成过程同样可见 LLM 输出（停止键透传，等待期轮询快速收口）
            ChatLanguageModel model = ToolAgentRunner.loggingModel(chatModelProvider.getIfUnique(),
                    this::appendLog, this::stopRequested);
            generateSingleAsset(model, ctx, assetType, guidance, promptKeyOf(assetType));
            uploadProgressFinish();
        } catch (GenerationStoppedException e) {
            log.info("Agent '{}' 资产 '{}' 重生成被用户停止", agentCode, assetType);
            finalizeStopped(ctx);
        } catch (Exception e) {
            log.error("Agent '{}' 资产 '{}' 重生成失败", agentCode, assetType, e);
            uploadProgressException(briefCause(e));
        } finally {
            stopHeartbeat();
            currentTaskThread = null;
            closeContext(ctx);
            agentTaskGate.release(TASK_KEY, INSTANCE_ID);
        }
    }

    // ---------------- 资产编辑页 AI 补全（同步轻量：不占全局闸门/不写草稿/不启任务态） ----------------

    /** 资产编辑页单条表单 AI 补全：用户手动新增时先填会填的确定项，LLM 按该资产类型专属提示词
     * +骨架上下文补齐空缺字段，返回补全后的完整行对象由前端填回表单（不落盘）；用户确认后走正常
     * 保存草稿链路（stampManualCertified 盖章=已认证，LLM 重生不覆盖）。已填字段经提示词强制保留 */
    public JsonNode aiCompleteAsset(String agentCode, String assetType, JsonNode form, String guidance) {
        return aiCompleteAsset(agentCode, assetType, form, guidance, null);
    }

    /** 流式重载：live 回调收 token 增量（null=同步直调语义）；流式走 buildLiveModel
     * （自建 SSE 客户端+同步回落，网关不支持 stream 时自动降级，无 Provider 回落同步模型无进度） */
    public JsonNode aiCompleteAsset(String agentCode, String assetType, JsonNode form, String guidance,
            java.util.function.Consumer<String> live) {
        if (!REGEN_ASSET_TYPES.contains(assetType)) {
            throw new NoticeException("仅指标/关系/概念支持 AI 补全（该三类才有专属提示词模板）");
        }
        if (form == null || !form.isObject()) {
            throw new NoticeException("当前表单内容不是 JSON 对象");
        }
        ChatLanguageModel model = buildLiveModel(live == null ? text -> { } : live,
                budgetOf(SmartQueryParam.GENERATE_THINKING_BUDGET));
        if (model == null) {
            throw new NoticeException("未配置 LLM 模型（llm.* 或系统参数），无法 AI 补全");
        }
        GenTaskContext ctx = new GenTaskContext(agentCode, GenTaskContext.MODE_PIPELINE);
        loadSkeletonContext(ctx);
        // 复用该类资产的生成模板（字段语义/规则知识源）+ 骨架占位符替换；{current_content} 语义改写为单条补全
        String template = effectivePrompts(agentCode).get(promptKeyOf(assetType));
        String prompt = template
                .replace(PH_ENTITIES, toCompactJson(ctx.getEntities()))
                .replace(PH_DIMENSIONS, toCompactJson(ctx.getDimensions()))
                .replace(PH_DOMAIN_KEYS, ctx.getDomains().isEmpty() ? "（无）" : String.join(", ", ctx.getDomains().keySet()))
                .replace(PH_CURRENT, "（单条补全模式：不参考现有草稿，只为下方用户表单补齐空缺字段）")
                + "\n\n【单条补全模式】（输出协议覆盖上方模板的其余要求）\n"
                + "用户已在表单填写了部分字段，原样如下（字段缺失=未填）：\n" + form + "\n"
                + "规则：① 用户已填的非空字段值必须原样保留，禁止改写/翻译/规范化；\n"
                + "② 仅补齐空缺字段（按模板字段规则与骨架上下文推断，字段名与模板一致，不得发明新字段）；\n"
                + "③ 只输出补全后的单个 JSON 对象（不是数组、不加说明文字）。"
                + (FuncUtil.isNotEmpty(guidance)
                ? "\n\n【人工指导语】（优先遵循，与上述规则冲突时以指导语为准）\n" + guidance.trim()
                : "");
        String answer = model.generate(prompt);
        try {
            JsonNode node = om.readTree(extractJson(answer));
            if (!node.isObject()) {
                throw new NoticeException("AI 补全返回的不是 JSON 对象，请重试");
            }
            return node;
        } catch (NoticeException e) {
            throw e;
        } catch (Exception e) {
            throw new NoticeException("AI 补全结果解析失败，请重试: " + e.getMessage());
        }
    }

    /** AI 补全流式链路执行器：编辑页单条补全低频小任务，不占全局闸门；daemon 随应用退出 */
    private final ExecutorService aiCompleteExecutor = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "asset-ai-complete");
        t.setDaemon(true);
        return t;
    });

    /** 资产编辑页 AI 补全（SSE 流式版，主入口）：tick 心跳 + delta token 增量实时下推，
     * 前端可分辨“死了还是想着”；done 携带补全后 JSON 全文，error 携带失败文案。
     * 心跳与线程安全写入已下沉 SseEventSender（连接收口/断开自动停跳）；本链路短
     * （单次补全）不设中断点，生成完自然收口 */
    public void aiCompleteAssetStream(String agentCode, String assetType, JsonNode form, String guidance,
            SseEmitter emitter) {
        SseEventSender sender = new SseEventSender(emitter);
        long begin = System.currentTimeMillis();
        // 活性心跳：思考类模型首应答 token 可达 10s+，期间生成线程阻塞在网关读上无法自发信号
        sender.startHeartbeat(2, () -> String.valueOf((System.currentTimeMillis() - begin) / 1000));
        aiCompleteExecutor.submit(() -> {
            try {
                JsonNode node = aiCompleteAsset(agentCode, assetType, form, guidance,
                        partial -> sender.send(SseEventSender.EVENT_DELTA, partial));
                sender.send(SseEventSender.EVENT_DONE, node.toString());
            } catch (Exception e) {
                log.warn("Agent '{}' 资产 AI 补全失败", agentCode, e);
                sender.send(SseEventSender.EVENT_ERROR,
                        e.getMessage() == null ? "AI 补全失败" : e.getMessage());
            } finally {
                sender.complete();
            }
        });
    }

    /** 单资产重生成进度初始化：SAVE 步 total=1，comments 标注正在重生成的资产 */
    private void startRegenProgress(String assetType) {
        PortalUploadProgressRes item = new PortalUploadProgressRes(
                UploadProgressStep.SAVE, 1, 0, new ArrayList<>());
        item.getComments().add("正在重生成资产: " + assetType);
        setUploadProgress(item);
        appendLog("开始重生成" + assetCnName(assetType) + "（加载骨架上下文与数据源连接）");
    }

    /** 从草稿加载骨架上下文到任务上下文（单独重生成/自主模式续作用）：
     *  entities/dimensions/value-domains 缺一不可 */
    private void loadSkeletonContext(GenTaskContext ctx) {
        InsightAgentAsset entAsset = smartAgentMetaService.getAsset(ctx.getAgentCode(), "entities");
        if (entAsset == null || FuncUtil.isEmpty(entAsset.getContent())) {
            throw new NoticeException("尚无骨架资产，请先执行「LLM 生成草稿」或「仅生成骨架」");
        }
        try {
            ctx.setEntities(new ArrayList<>(Arrays.asList(om.readValue(entAsset.getContent(), EntityDef[].class))));
        } catch (Exception e) {
            throw new NoticeException("entities 草稿解析失败，请重新生成骨架: " + e.getMessage());
        }
        Set<String> entityNames = new HashSet<>();
        ctx.getEntities().forEach(e -> entityNames.add(e.getName()));
        ctx.setEntityNames(entityNames);
        try {
            InsightAgentAsset dimAsset = smartAgentMetaService.getAsset(ctx.getAgentCode(), "dimensions");
            ctx.setDimensions(dimAsset == null || FuncUtil.isEmpty(dimAsset.getContent()) ? new ArrayList<>()
                    : new ArrayList<>(Arrays.asList(om.readValue(dimAsset.getContent(), DimensionDef[].class))));
        } catch (Exception e) {
            throw new NoticeException("dimensions 草稿解析失败，请重新生成骨架: " + e.getMessage());
        }
        Set<String> dimensionNames = new HashSet<>();
        ctx.getDimensions().forEach(d -> dimensionNames.add(d.getName()));
        ctx.setDimensionNames(dimensionNames);
        Map<String, ValueDomainDef> domains = new LinkedHashMap<>();
        try {
            InsightAgentAsset domAsset = smartAgentMetaService.getAsset(ctx.getAgentCode(), "value-domains");
            if (domAsset != null && FuncUtil.isNotEmpty(domAsset.getContent())) {
                JsonNode doms = om.readTree(domAsset.getContent()).path("domains");
                java.util.Iterator<Map.Entry<String, JsonNode>> it = doms.fields();
                while (it.hasNext()) {
                    Map.Entry<String, JsonNode> entry = it.next();
                    domains.put(entry.getKey(), om.treeToValue(entry.getValue(), ValueDomainDef.class));
                }
            }
        } catch (Exception e) {
            throw new NoticeException("value-domains 草稿解析失败，请重新生成骨架: " + e.getMessage());
        }
        ctx.setDomains(domains);
    }

    /** 关闭任务上下文持有的数据源连接（全体生成/单资产重生成/自主模式共用收尾） */
    private void closeContext(GenTaskContext ctx) {
        if (ctx.getConn() != null) {
            try {
                ctx.getConn().close();
            } catch (Exception ignored) {
                // 关闭失败不影响任务终态
            }
            ctx.setConn(null);
        }
    }
}
