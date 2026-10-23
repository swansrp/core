package com.bidr.llm.agent.session;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import javax.annotation.PreDestroy;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Title: AgentSessionStreamHub
 * Description: agent 会话 SSE 推送通道（M18 棒2）——按 sessionId 管理多条 SSE 连接
 * （同会话多标签页各自收帧、互不顶替），live/event/status/done 四类帧投递：
 * <ul>
 *     <li><b>只投递、绝不落库</b>：事件流与快照的单写者仍是 run 线程（store 契约），
 *         hub 不追加事件、不覆盖快照——断流≠取消，任何 SSE 异常不改会话状态机；</li>
 *     <li><b>总线扇出</b>：{@link StreamBus}（生产＝Redisson RTopic）把帧信封
 *         {@code {"sid":..,"k":"live|event|status|done","d":..}} 广播到全部实例，
 *         本实例经 {@link #onBusMessage} 回声统一投递（发布侧不直接投本地连接，
 *         两形态帧序一致）；bus 缺席（null）＝仅本地投递（单实例退化）；</li>
 *     <li><b>建连回放</b>：{@link #open} 先 touchView（SSE 建连＝客户端可控在场信号）、
 *         再回放 status 全量帧＋events(sinceSeq) 逐条帧（SSE id=seq，只在建连/重连回放时
 *         调 eventsReader，运行期不回读事件流）；终态会话直接 done＋complete 不注册连接；</li>
 *     <li><b>异步有界投递（R-7 红线）</b>：全部本地发送（deliverLocal 的 send/complete、
 *         ping 的 comment）一律经单 delivery 线程（daemon，agent-stream-deliver）＋有界队列
 *         （512，满则 DiscardPolicy 整帧丢弃）执行——run 线程/LLM 流回调线程/总线监听线程/
 *         ping 线程只入队，绝不直接碰 SseEmitter.send，慢客户端（后台标签页 throttling 等）
 *         阻塞不住它们；live 帧另有按连接的 CAS 忙丢件（在途即丢本帧）——注意生产形态
 *         （单 delivery 线程）下投递本就串行、忙位同帧内置复，该件不会触发，真正的背压
 *         保护＝有界队列＋DiscardPolicy（多线程化投递时才启用忙丢，见 deliverFrame）；
 *         status/events/done 无条件入队（队列满被丢时由前端 sinceSeq 补拉/25s 保活兜底）；
 *         建连回放是客户端
 *         自己的 HTTP 请求线程，同步发送不属于被保护线程面；</li>
 *     <li><b>注释心跳</b>：共享 daemon 单线程调度器每 15s 遍历全部连接入队 {@code : ping}
 *         防反代空闲断连（ping 线程只入队不直发，慢连接不队头阻塞其他会话），
 *         发送失败摘连接。</li>
 * </ul>
 * SSE 线上形态：事件名＝k（live/events/status/done，注意 events 是复数）；
 * events 帧 id=seq＋data=AgentEvent JSON，其余无 id。
 * 所有发送/总线操作 try-catch 只记 warn——SSE 任何异常不得改变会话状态机。
 *
 * @author Sharp
 * @since 2026/10/23
 */
@Slf4j
public class AgentSessionStreamHub {

    /** 总线帧 kind：LLM 流式实时内容（d={text,chars,phase}） */
    public static final String KIND_LIVE = "live";

    /** 总线帧 kind：过程事件（d=AgentEvent 形状；SSE 事件名为复数 events） */
    public static final String KIND_EVENT = "event";

    /** 总线帧 kind：状态快照（d=AgentSessionState 全量 JSON） */
    public static final String KIND_STATUS = "status";

    /** 总线帧 kind：终态收口（d=AgentSessionState 全量 JSON，投递后关该会话全部连接） */
    public static final String KIND_DONE = "done";

    /** SSE 事件名（事件帧复数形态，与总线 kind 区分） */
    private static final String SSE_EVENTS = "events";

    /** 注释心跳周期（秒） */
    private static final long PING_SECONDS = 15;

    /** live 状态行字数解析（「已思 N 字」/「已收 N 字」，与发射端文案同源） */
    private static final Pattern STATUS_CHARS = Pattern.compile("已[思收] (\\d+) 字");

    private final ObjectMapper objectMapper;

    /** sessionId → connId(UUID) → 连接条目（同会话多连接各自收帧；条目包 sender＋live 忙位） */
    private final Map<String, Map<String, Conn>> conns = new ConcurrentHashMap<>();

    /** 跨实例帧总线（可空＝仅本地投递）；生产由 Redisson RTopic 实现，测试用直连总线 */
    private volatile StreamBus bus;

    /** 投递执行器（I4 背压）：所有本地发送经它执行，调用线程只入队不碰 SseEmitter.send；
     *  测试接缝＝注入同线程执行器（Runnable::run）保序保同步断言 */
    private final Executor deliver;

    /** 自建的投递执行器（shutdown 时关；注入形态为 null——执行器归调用方管） */
    private final ThreadPoolExecutor ownedDeliver;

    /** 注释心跳调度器（共享 daemon 单线程：只入队 ping 任务，多连接串行复用足够） */
    private final ScheduledExecutorService pingScheduler =
            Executors.newSingleThreadScheduledExecutor(runnable -> {
                Thread thread = new Thread(runnable, "agent-stream-ping");
                thread.setDaemon(true);
                return thread;
            });

    /** 投递队列容量（有界）：满则 DiscardPolicy 整帧丢弃——慢消费不放大内存，丢帧兜底见类头 */
    private static final int DELIVER_QUEUE_CAPACITY = 512;

    public AgentSessionStreamHub(ObjectMapper objectMapper) {
        this(objectMapper, null);
    }

    /** 全参构造器（测试接缝）：deliverExecutor 传 null＝默认单 delivery 线程有界形态；
     *  测试传同线程执行器（如 {@code Runnable::run}）保序保同步断言 */
    public AgentSessionStreamHub(ObjectMapper objectMapper, Executor deliverExecutor) {
        this.objectMapper = objectMapper;
        if (deliverExecutor != null) {
            this.deliver = deliverExecutor;
            this.ownedDeliver = null;
        } else {
            // 单 daemon 线程（agent-stream-deliver）＋有界队列 512＋DiscardPolicy：
            // 慢客户端最多拖住 delivery 线程自己，队列满后新帧整帧丢弃不积压；
            // 调用线程（run/LLM 流回调/总线监听/ping）入队即返，R-7 红线落地
            ThreadPoolExecutor executor = new ThreadPoolExecutor(1, 1, 60L, TimeUnit.SECONDS,
                    new ArrayBlockingQueue<>(DELIVER_QUEUE_CAPACITY),
                    runnable -> {
                        Thread thread = new Thread(runnable, "agent-stream-deliver");
                        thread.setDaemon(true);
                        return thread;
                    },
                    new DiscardPolicyWithLog());
            executor.allowCoreThreadTimeOut(true);
            this.deliver = executor;
            this.ownedDeliver = executor;
        }
        pingScheduler.scheduleAtFixedRate(this::pingAll, PING_SECONDS, PING_SECONDS, TimeUnit.SECONDS);
    }

    /** 跨实例帧总线接线（装配期一次性调用；传 null＝退回本地投递） */
    public void setBus(StreamBus bus) {
        this.bus = bus;
    }

    // ==================== 建连（Controller 入口 + 可测接缝） ====================

    /**
     * 建连（Controller 用）：新建无超时 SseEmitter 并完成回放/注册，
     * 断开/超时/出错只摘连接（断流≠取消，不改会话状态）
     */
    public SseEmitter open(AgentSessionState state, long sinceSeq,
                           Function<Long, List<AgentEvent>> eventsReader, Runnable touchView) {
        SseEmitter emitter = new SseEmitter(0L);
        SseFrameSender sender = new SseFrameSender(emitter);
        // 摘连接回调按 sender 身份移除（终端态不注册时为无的放矢的 no-op）
        Runnable close = () -> removeSender(state.getSessionId(), sender);
        emitter.onCompletion(close);
        emitter.onTimeout(close);
        emitter.onError(error -> close.run());
        open(sender, state, sinceSeq, eventsReader, touchView);
        return emitter;
    }

    /**
     * 建连（可测接 seam）：touchView → 回放 status 全量帧＋events(sinceSeq) 逐条帧
     * （SSE id=seq）→ 终态会话发 done＋complete 不注册，否则注册连接
     */
    public void open(FrameSender sender, AgentSessionState state, long sinceSeq,
                     Function<Long, List<AgentEvent>> eventsReader, Runnable touchView) {
        String sessionId = state.getSessionId();
        try {
            touchView.run();
        } catch (Exception e) {
            log.warn("SSE 建连在场信号刷新失败（忽略）, sessionId={}, error={}", sessionId, e.getMessage());
        }
        sendQuietly(sender, KIND_STATUS, toJson(state), null);
        try {
            for (AgentEvent event : eventsReader.apply(sinceSeq)) {
                sendQuietly(sender, SSE_EVENTS, toJson(event), event.getSeq());
            }
        } catch (Exception e) {
            log.warn("SSE 建连事件回放失败（忽略）, sessionId={}, error={}", sessionId, e.getMessage());
        }
        if (state.isTerminal()) {
            sendQuietly(sender, KIND_DONE, toJson(state), null);
            completeQuietly(sender);
            return;
        }
        conns.computeIfAbsent(sessionId, key -> new ConcurrentHashMap<>())
                .put(UUID.randomUUID().toString().replace("-", ""), new Conn(sender));
    }

    // ==================== 帧发布（run 线程 / HTTP 线程调用） ====================

    /**
     * live 帧发布（高频、不落事件流）：d={text,chars,phase}——phase 按前缀推导
     * （思考中→thinking／应答中→answering／等待 LLM→waiting／流式通道→degraded／其他→other），
     * chars 取状态行内 N 字解析值、解析不到取 text.length()。bus 可用走 bus（本实例
     * 监听器统一投递），否则本地投递
     */
    public void publishLive(String sessionId, String text) {
        Map<String, Object> d = new LinkedHashMap<>();
        d.put("text", text);
        d.put("chars", parseChars(text));
        d.put("phase", phaseOf(text));
        publishFrame(sessionId, KIND_LIVE, d);
    }

    /** 事件帧发布（d=AgentEvent 形状；seq≤0 落库失败不发） */
    public void publishEvent(String sessionId, long seq, String type, Object payload, long time) {
        if (seq <= 0) {
            return;
        }
        Map<String, Object> d = new LinkedHashMap<>();
        d.put("seq", seq);
        d.put("type", type);
        d.put("payload", payload);
        d.put("time", time);
        publishFrame(sessionId, KIND_EVENT, d);
    }

    /** 状态帧发布（语义变更点：阶段/计划/问答/确认等持久后随帧下发全量快照） */
    public void publishStatus(AgentSessionState state) {
        publishFrame(state.getSessionId(), KIND_STATUS, state);
    }

    /**
     * 终态收口帧：本地发 done 帧并 complete＋清空该会话全部连接，再上总线（本实例连接已清，
     * 总线回声落在空表上不产生二次 done 帧；远端实例投递后关各自连接）
     */
    public void publishDone(AgentSessionState state) {
        String sessionId = state.getSessionId();
        try {
            deliverLocal(sessionId, KIND_DONE, objectMapper.valueToTree(state));
            completeSession(sessionId);
        } catch (Exception e) {
            log.warn("SSE done 帧本地投递失败（忽略）, sessionId={}, error={}", sessionId, e.getMessage());
        }
        StreamBus current = bus;
        if (current != null) {
            try {
                current.publish(envelope(sessionId, KIND_DONE, state));
            } catch (Exception e) {
                log.warn("SSE done 帧总线发布失败（忽略）, sessionId={}, error={}", sessionId, e.getMessage());
            }
        }
    }

    /**
     * 总线消息入口（Redisson 监听器/直连总线测试调用）：按 sid 投递本地连接；
     * done 投递后关该会话全部连接
     */
    public void onBusMessage(String json) {
        if (json == null || json.trim().isEmpty()) {
            return;
        }
        try {
            JsonNode node = objectMapper.readTree(json);
            String sessionId = node.path("sid").asText(null);
            String kind = node.path("k").asText(null);
            JsonNode d = node.path("d");
            if (sessionId == null || kind == null) {
                return;
            }
            if (KIND_DONE.equals(kind)) {
                deliverLocal(sessionId, KIND_DONE, d);
                completeSession(sessionId);
            } else if (KIND_EVENT.equals(kind)) {
                long seq = d.path("seq").asLong(0);
                deliverLocal(sessionId, SSE_EVENTS, d, seq > 0 ? seq : null);
            } else if (KIND_LIVE.equals(kind) || KIND_STATUS.equals(kind)) {
                deliverLocal(sessionId, kind, d);
            }
        } catch (Exception e) {
            log.warn("SSE 总线消息处理失败（忽略）: {}", e.getMessage());
        }
    }

    // ==================== 内部投递（I4：异步有界，调用线程只入队） ====================

    /** 统一发布路径：bus 可用走 bus（本实例回声统一投递），失败/缺席回落本地投递 */
    private void publishFrame(String sessionId, String kind, Object data) {
        String json;
        try {
            json = envelope(sessionId, kind, data);
        } catch (Exception e) {
            log.warn("SSE 帧信封序列化失败（忽略）, sessionId={}, k={}, error={}", sessionId, kind, e.getMessage());
            return;
        }
        StreamBus current = bus;
        if (current != null) {
            try {
                current.publish(json);
                return;
            } catch (Exception e) {
                log.warn("SSE 总线发布失败，回落本地投递, sessionId={}, k={}, error={}",
                        sessionId, kind, e.getMessage());
            }
        }
        onBusMessage(json);
    }

    /** 本地连接投递（kind→SSE 事件名，event 复数形态）：序列化后入 delivery 队列，
     *  调用线程（run/LLM 流回调/总线监听）不碰 SseEmitter.send；队列满整帧被
     *  DiscardPolicy 丢弃——status/events/done 低频，丢帧由前端 sinceSeq 补拉/25s 保活兜底，
     *  live 高频本就可丢。conns 在任务执行时才查表：入队后才注册的连接照样收帧 */
    private void deliverLocal(String sessionId, String kind, Object data) {
        deliverLocal(sessionId, kind, data, null);
    }

    private void deliverLocal(String sessionId, String kind, Object data, Long id) {
        String payload;
        try {
            payload = data instanceof String ? (String) data : objectMapper.writeValueAsString(data);
        } catch (Exception e) {
            log.warn("SSE 帧数据序列化失败（忽略）, sessionId={}, k={}, error={}", sessionId, kind, e.getMessage());
            return;
        }
        deliver.execute(() -> deliverFrame(sessionId, kind, payload, id));
    }

    /** delivery 线程上的实际投递：live 帧按连接 CAS 忙丢（忙位被占＝该连接上一条 live
     *  还在途 ⇒ 丢本帧，finally 复位）——生产单 delivery 线程下投递串行、忙位永远先复位
     *  再投下一帧，此件不会触发（防多线程化投递时的重复在途，属预置防线）；真实背压
     *  ＝外层有界队列＋DiscardPolicy。status/events/done 无条件投递；单连接失败摘连接不外溢 */
    private void deliverFrame(String sessionId, String kind, String payload, Long id) {
        Map<String, Conn> senders = conns.get(sessionId);
        if (senders == null || senders.isEmpty()) {
            return;
        }
        boolean live = KIND_LIVE.equals(kind);
        String name = KIND_EVENT.equals(kind) ? SSE_EVENTS : kind;
        for (Map.Entry<String, Conn> entry : senders.entrySet()) {
            Conn conn = entry.getValue();
            if (live && !conn.liveBusy.compareAndSet(false, true)) {
                continue;
            }
            try {
                conn.sender.event(name, payload, id);
            } catch (Exception e) {
                log.warn("SSE 帧投递失败摘连接, sessionId={}, conn={}, error={}",
                        sessionId, entry.getKey(), e.getMessage());
                senders.remove(entry.getKey());
            } finally {
                if (live) {
                    conn.liveBusy.set(false);
                }
            }
        }
    }

    /** complete 该会话全部连接并清表（done 收口）。摘表与 complete 都在 delivery 线程上
     *  执行：与先入队的 done 帧同队列 FIFO 保序（帧先发、连接后关），调用线程不碰 emitter */
    private void completeSession(String sessionId) {
        deliver.execute(() -> {
            Map<String, Conn> senders = conns.remove(sessionId);
            if (senders == null) {
                return;
            }
            for (Map.Entry<String, Conn> entry : senders.entrySet()) {
                try {
                    entry.getValue().sender.complete();
                } catch (Exception e) {
                    log.warn("SSE 连接 complete 失败（忽略）, sessionId={}, conn={}, error={}",
                            sessionId, entry.getKey(), e.getMessage());
                }
            }
        });
    }

    /** 按 sender 身份摘连接（onCompletion/onTimeout/onError/发送失败）。
     *  摘空后<b>不删</b> session map（I1 竞态）：removeIf 判空与整表 remove 之间 open 可
     *  经 computeIfAbsent 复用同 map put 新连接——条件整表 remove（按 map 身份比对）会把
     *  这条新连接一并摘掉，成为收不到任何帧的孤儿；终态清整表已由 completeSession 兜底，
     *  空 map 由下次 open 原地复用，无泄漏放大。（包级＝测试接缝：I1 回归腿直接摘连） */
    void removeSender(String sessionId, FrameSender sender) {
        Map<String, Conn> senders = conns.get(sessionId);
        if (senders == null) {
            return;
        }
        senders.values().removeIf(conn -> conn.sender == sender);
    }

    /** 注释心跳：遍历全部连接入队 ping 任务（ping 线程只入队不直发——单线程调度器
     *  不被慢连接队头阻塞跨会话拖串），delivery 线程上发送失败摘连接（空 map 不删，同上） */
    private void pingAll() {
        for (Map.Entry<String, Map<String, Conn>> sessionEntry : conns.entrySet()) {
            String sessionId = sessionEntry.getKey();
            Map<String, Conn> senders = sessionEntry.getValue();
            for (Map.Entry<String, Conn> entry : senders.entrySet()) {
                String connId = entry.getKey();
                Conn conn = entry.getValue();
                deliver.execute(() -> {
                    try {
                        conn.sender.comment("ping");
                    } catch (Exception e) {
                        log.debug("SSE 心跳失败摘连接, sessionId={}, conn={}", sessionId, connId);
                        senders.remove(connId);
                    }
                });
            }
        }
    }

    private String envelope(String sessionId, String kind, Object data) throws Exception {
        Map<String, Object> env = new LinkedHashMap<>();
        env.put("sid", sessionId);
        env.put("k", kind);
        env.put("d", data);
        return objectMapper.writeValueAsString(env);
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception e) {
            return null;
        }
    }

    private void sendQuietly(FrameSender sender, String name, String data, Long id) {
        try {
            sender.event(name, data, id);
        } catch (Exception e) {
            log.warn("SSE 回放帧发送失败（忽略）, name={}, error={}", name, e.getMessage());
        }
    }

    private void completeQuietly(FrameSender sender) {
        try {
            sender.complete();
        } catch (Exception e) {
            log.warn("SSE 连接 complete 失败（忽略）, error={}", e.getMessage());
        }
    }

    /** live 状态行字数解析：取「已思/已收 N 字」的 N，解析不到取全文长度 */
    private static int parseChars(String text) {
        if (text == null) {
            return 0;
        }
        Matcher matcher = STATUS_CHARS.matcher(text);
        return matcher.find() ? Integer.parseInt(matcher.group(1)) : text.length();
    }

    /** live 帧阶段推导（前缀词汇表与 StreamingProgressChatModel#isLiveStatusLine 同源镜像） */
    private static String phaseOf(String text) {
        if (text == null) {
            return "other";
        }
        if (text.startsWith("思考中")) {
            return "thinking";
        }
        if (text.startsWith("应答中")) {
            return "answering";
        }
        if (text.startsWith("等待 LLM")) {
            return "waiting";
        }
        if (text.startsWith("流式通道")) {
            return "degraded";
        }
        return "other";
    }

    @PreDestroy
    public void shutdown() {
        pingScheduler.shutdownNow();
        if (ownedDeliver != null) {
            ownedDeliver.shutdownNow();
        }
    }

    // ==================== 接缝（测试替身挂点） ====================

    /** 测试接缝（包级）：取该会话首个连接的 live 忙位（无连接返回 null）——
     *  I4 忙丢回归腿用它占位模拟在途慢发送 */
    AtomicBoolean liveBusyForTest(String sessionId) {
        Map<String, Conn> senders = conns.get(sessionId);
        if (senders == null || senders.isEmpty()) {
            return null;
        }
        return senders.values().iterator().next().liveBusy;
    }

    /** 连接条目（FrameSender 包装处）：live 忙位＝该连接 live 帧按 CAS 忙丢的判据
     *  （被占＝在途未完，新 live 帧直接丢，status/events/done 不受影响）——单 delivery
     *  线程形态下不会触发，多线程化投递时才生效（见 deliverFrame 注释） */
    static final class Conn {
        final FrameSender sender;
        final AtomicBoolean liveBusy = new AtomicBoolean();

        Conn(FrameSender sender) {
            this.sender = sender;
        }
    }

    /** DiscardPolicy ＋ debug 日志形态：队列满整帧静默丢弃不抛（保调用线程），留一条
     *  debug 痕迹供排查（丢帧兜底＝前端 sinceSeq 补拉/25s 保活，见类头） */
    private static final class DiscardPolicyWithLog extends ThreadPoolExecutor.DiscardPolicy {
        @Override
        public void rejectedExecution(Runnable runnable, ThreadPoolExecutor executor) {
            log.debug("SSE 投递队列满（{} 帧），整帧丢弃（前端 sinceSeq/保活兜底）", DELIVER_QUEUE_CAPACITY);
            super.rejectedExecution(runnable, executor);
        }
    }

    /**
     * SSE 帧落点抽象：生产实现 {@link SseFrameSender} 包 SseEmitter
     * （无容器的裸 send 会抛异常，测试须用记录型实现）
     */
    public interface FrameSender {

        /** 发一帧命名事件（id 非空时携带 SSE id） */
        void event(String name, String data, Long id);

        /** 发注释行（心跳） */
        void comment(String text);

        /** 收流（连接完成） */
        void complete();
    }

    /** 跨实例帧总线（生产＝Redisson RTopic；测试用直连总线串多个 hub） */
    public interface StreamBus {

        /** 发布一帧信封 JSON（{@code {"sid":..,"k":..,"d":..}}） */
        void publish(String json);
    }

    /** FrameSender 的 SseEmitter 适配（onCompletion/onTimeout/onError 由 open 挂摘连接回调） */
    static class SseFrameSender implements FrameSender {

        private final SseEmitter emitter;

        SseFrameSender(SseEmitter emitter) {
            this.emitter = emitter;
        }

        @Override
        public void event(String name, String data, Long id) {
            SseEmitter.SseEventBuilder builder = SseEmitter.event().name(name).data(data);
            if (id != null) {
                builder.id(Long.toString(id));
            }
            try {
                emitter.send(builder);
            } catch (IOException e) {
                // 接缝不声明受检异常：包非受检上抛，调用方 catch(Exception) 统一摘连接
                throw new UncheckedIOException(e);
            }
        }

        @Override
        public void comment(String text) {
            try {
                emitter.send(SseEmitter.event().comment(text));
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        }

        @Override
        public void complete() {
            emitter.complete();
        }
    }
}
