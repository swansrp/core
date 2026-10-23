package com.bidr.llm.agent.session;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.After;
import org.junit.Assert;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

/**
 * Title: AgentSessionStreamHubTest
 * Description: SSE 推送通道 hub 契约测试（M18 棒2）——锁死七条腿：
 * ①双实例总线扇出（publishLive/publishEvent 经直连总线抵达对端持连 sender，
 * events 帧 SSE id=seq）；②同会话双连接各自收帧；③publishDone 后连接 complete
 * 且不再收帧；④无总线（bus null）本地投递仍达；⑤open() 建连回放
 * （status 全量帧＋events sinceSeq 过滤＋终态会话直接 done＋complete 不注册）；
 * ⑥I4 忙丢——live 忙位被占（模拟在途慢发送）时 live 帧被丢、status 帧仍达、
 * 释放后恢复；⑦I1 竞态回归——摘连（摘空不删 map）后 open 新连接仍收帧。
 * 直连总线＝两个 hub 共用一个 StreamBus（publish 时同步回调两实例 onBusMessage，
 * 模拟 Redis pub/sub 的发布者回声语义）；FrameSender 用记录型实现
 * （SseEmitter 无容器 send 会抛异常，一律走接缝）；hub 一律注入同线程执行器
 * （Runnable::run）保同步断言（I4 测试接缝，生产＝单 delivery 线程有界队列）。
 *
 * @author Sharp
 * @since 2026/10/23
 */
public class AgentSessionStreamHubTest {

    private static final ObjectMapper OM = new ObjectMapper();

    private final List<AgentSessionStreamHub> hubs = new ArrayList<>();

    @After
    public void tearDown() {
        hubs.forEach(AgentSessionStreamHub::shutdown);
    }

    private AgentSessionStreamHub newHub() {
        // 同线程执行器（Runnable::run）：入队即执行，保序保同步断言（I4 测试接缝；
        // 生产形态＝单 delivery 线程＋有界队列，异步投递不适合逐帧同步断言）
        AgentSessionStreamHub hub = new AgentSessionStreamHub(OM, Runnable::run);
        hubs.add(hub);
        return hub;
    }

    /** 直连总线：publish 同步回声给两个 hub（模拟 Redis pub/sub 发布者自身也在订阅端） */
    private AgentSessionStreamHub.StreamBus directBus(AgentSessionStreamHub a, AgentSessionStreamHub b) {
        AgentSessionStreamHub.StreamBus bus = json -> {
            a.onBusMessage(json);
            b.onBusMessage(json);
        };
        a.setBus(bus);
        b.setBus(bus);
        return bus;
    }

    /** 记录型 FrameSender：帧名/data/id、注释与 complete 全留痕 */
    private static final class RecordingSender implements AgentSessionStreamHub.FrameSender {
        final List<String> names = new ArrayList<>();
        final List<String> datas = new ArrayList<>();
        final List<Long> ids = new ArrayList<>();
        final List<String> comments = new ArrayList<>();
        boolean completed;

        @Override
        public void event(String name, String data, Long id) {
            names.add(name);
            datas.add(data);
            ids.add(id);
        }

        @Override
        public void comment(String text) {
            comments.add(text);
        }

        @Override
        public void complete() {
            completed = true;
        }

        long count(String name) {
            return names.stream().filter(name::equals).count();
        }
    }

    private static AgentSessionState state(String sessionId, String status) {
        AgentSessionState state = new AgentSessionState();
        state.setSessionId(sessionId);
        state.setAgentKey("probe");
        state.setStatus(status);
        state.setStartedAt(System.currentTimeMillis());
        return state;
    }

    // ---- 腿①：双实例总线扇出 ----

    @Test
    public void 双实例经总线扇出_live与事件帧抵达对端连接() throws Exception {
        AgentSessionStreamHub hubA = newHub();
        AgentSessionStreamHub hubB = newHub();
        directBus(hubA, hubB);
        RecordingSender senderB = new RecordingSender();
        hubB.open(senderB, state("s1", AgentSessionState.RUNNING), 0, since -> java.util.Collections.emptyList(), () -> { });

        hubA.publishLive("s1", "思考中·已思 12 字\n思考全文");
        hubA.publishEvent("s1", 5, "log", "一行过程", 123L);

        Assert.assertEquals(1, senderB.count("live"));
        JsonNode live = OM.readTree(senderB.datas.get(senderB.names.indexOf("live")));
        Assert.assertEquals("思考中·已思 12 字\n思考全文", live.path("text").asText());
        Assert.assertEquals(12, live.path("chars").asInt());
        Assert.assertEquals("thinking", live.path("phase").asText());

        Assert.assertEquals(1, senderB.count("events"));
        int eventIdx = senderB.names.indexOf("events");
        Assert.assertEquals(Long.valueOf(5L), senderB.ids.get(eventIdx));
        JsonNode event = OM.readTree(senderB.datas.get(eventIdx));
        Assert.assertEquals(5, event.path("seq").asLong());
        Assert.assertEquals("log", event.path("type").asText());
        Assert.assertEquals("一行过程", event.path("payload").asText());
        Assert.assertEquals(123L, event.path("time").asLong());
    }

    // ---- 腿②：同会话双连接各自收帧 ----

    @Test
    public void 同会话双连接_各自收到同一帧() {
        AgentSessionStreamHub hub = newHub();
        RecordingSender first = new RecordingSender();
        RecordingSender second = new RecordingSender();
        AgentSessionState running = state("s2", AgentSessionState.RUNNING);
        hub.open(first, running, 0, since -> java.util.Collections.emptyList(), () -> { });
        hub.open(second, running, 0, since -> java.util.Collections.emptyList(), () -> { });

        hub.publishLive("s2", "应答中·已收 8 字\n答案");

        Assert.assertEquals(1, first.count("live"));
        Assert.assertEquals(1, second.count("live"));
        Assert.assertEquals(first.datas.get(first.names.indexOf("live")),
                second.datas.get(second.names.indexOf("live")));
    }

    // ---- 腿③：publishDone 后两连接均 complete 且不再收帧 ----

    @Test
    public void publishDone后连接收帧complete且后续不再收() throws Exception {
        AgentSessionStreamHub hub = newHub();
        RecordingSender first = new RecordingSender();
        RecordingSender second = new RecordingSender();
        AgentSessionState running = state("s3", AgentSessionState.RUNNING);
        hub.open(first, running, 0, since -> java.util.Collections.emptyList(), () -> { });
        hub.open(second, running, 0, since -> java.util.Collections.emptyList(), () -> { });

        AgentSessionState finished = state("s3", AgentSessionState.FINISHED);
        hub.publishDone(finished);

        Assert.assertTrue(first.completed);
        Assert.assertTrue(second.completed);
        Assert.assertEquals(1, first.count("done"));
        Assert.assertEquals(1, second.count("done"));
        Assert.assertEquals("FINISHED", OM.readTree(first.datas.get(first.names.indexOf("done")))
                .path("status").asText());

        // done 之后连接已清：后续帧不投递（也验证连接表已清不残留）
        hub.publishLive("s3", "不应再收到");
        hub.publishEvent("s3", 9, "log", "x", 1L);
        Assert.assertEquals(0, first.count("live"));
        Assert.assertEquals(0, first.count("events"));
        Assert.assertEquals(0, second.count("live"));
    }

    // ---- 腿④：无总线（bus null）本地投递仍达 ----

    @Test
    public void 无总线时本地投递仍达() {
        AgentSessionStreamHub hub = newHub();
        RecordingSender sender = new RecordingSender();
        hub.open(sender, state("s4", AgentSessionState.RUNNING), 0, since -> java.util.Collections.emptyList(), () -> { });

        hub.publishLive("s4", "等待 LLM 流式响应… 已等 5s");
        hub.publishEvent("s4", 3, "stage", java.util.Collections.singletonMap("key", "k1"), 77L);

        Assert.assertEquals(1, sender.count("live"));
        Assert.assertEquals(1, sender.count("events"));
        Assert.assertEquals(Long.valueOf(3L), sender.ids.get(sender.names.indexOf("events")));
    }

    // ---- 腿⑤：open() 建连回放 ----

    @Test
    public void open回放_status帧加事件过滤加SSEid() throws Exception {
        InMemoryAgentSessionStore store = new InMemoryAgentSessionStore();
        String sessionId = "s5";
        store.saveState(state(sessionId, AgentSessionState.RUNNING));
        store.appendEvent(sessionId, AgentEvent.LOG, "第1条");
        store.appendEvent(sessionId, AgentEvent.LOG, "第2条");
        store.appendEvent(sessionId, AgentEvent.LOG, "第3条");
        AgentSessionStreamHub hub = newHub();
        RecordingSender sender = new RecordingSender();
        List<Long> askedSince = new ArrayList<>();
        boolean[] touched = {false};

        hub.open(sender, store.getState(sessionId), 1,
                since -> {
                    askedSince.add(since);
                    return store.events(sessionId, since);
                },
                () -> touched[0] = true);

        // 在场信号已刷、eventsReader 只在建连回放时以 sinceSeq 调一次
        Assert.assertTrue(touched[0]);
        Assert.assertEquals(1, askedSince.size());
        Assert.assertEquals(Long.valueOf(1L), askedSince.get(0));
        // 帧序：status 全量帧在前，随后 seq>sinceSeq 的事件逐条（id=seq）
        Assert.assertEquals("status", sender.names.get(0));
        Assert.assertEquals(sessionId, OM.readTree(sender.datas.get(0)).path("sessionId").asText());
        Assert.assertEquals(2, sender.count("events"));
        Assert.assertEquals(Long.valueOf(2L), sender.ids.get(1));
        Assert.assertEquals(Long.valueOf(3L), sender.ids.get(2));
        Assert.assertEquals("第2条", OM.readTree(sender.datas.get(1)).path("payload").asText());
        // 非终态：不 complete、已注册（后续帧可达）
        Assert.assertFalse(sender.completed);
        hub.publishLive(sessionId, "流式通道不可用，降级同步模型…");
        Assert.assertEquals(1, sender.count("live"));
    }

    @Test
    public void open回放_终态会话直接done并complete不注册() throws Exception {
        InMemoryAgentSessionStore store = new InMemoryAgentSessionStore();
        String sessionId = "s6";
        AgentSessionState terminal = state(sessionId, AgentSessionState.FINISHED);
        store.saveState(terminal);
        store.appendEvent(sessionId, AgentEvent.FINISH, "会话完成");
        AgentSessionStreamHub hub = newHub();
        RecordingSender sender = new RecordingSender();

        hub.open(sender, terminal, 0, since -> store.events(sessionId, since), () -> { });

        Assert.assertEquals("status", sender.names.get(0));
        Assert.assertEquals(1, sender.count("events"));
        Assert.assertEquals(1, sender.count("done"));
        Assert.assertTrue(sender.completed);
        // 不注册连接：后续帧不再抵达
        hub.publishLive(sessionId, "终态后不应收到");
        Assert.assertEquals(0, sender.count("live"));
    }

    // ---- 腿⑥（I4）：慢连接 live 忙位被占——live 帧被丢、status 帧仍达、释放后恢复 ----

    @Test
    public void live忙位被占时_live帧丢弃_status帧仍达() {
        AgentSessionStreamHub hub = newHub();
        RecordingSender sender = new RecordingSender();
        hub.open(sender, state("s7", AgentSessionState.RUNNING), 0, since -> java.util.Collections.emptyList(), () -> { });
        long statusBefore = sender.count("status");

        java.util.concurrent.atomic.AtomicBoolean busy = hub.liveBusyForTest("s7");
        Assert.assertNotNull("注册后应能取到 live 忙位", busy);
        Assert.assertTrue("占位模拟在途慢发送", busy.compareAndSet(false, true));

        hub.publishLive("s7", "思考中·已思 5 字\n慢客户端在途");
        Assert.assertEquals("忙位被占：本连接 live 帧被丢（可丢语义）", 0, sender.count("live"));
        hub.publishStatus(state("s7", AgentSessionState.RUNNING));
        Assert.assertEquals("status 帧无条件投递不受 live 忙位影响", statusBefore + 1, sender.count("status"));

        busy.set(false); // 在途发送结束（finally 复位语义由测试代行）
        hub.publishLive("s7", "应答中·已收 2 字\n恢复");
        Assert.assertEquals("忙位释放后 live 帧恢复投递（忙丢是瞬时不是永久）", 1, sender.count("live"));
    }

    // ---- 腿⑦（I1 回归）：连接摘除瞬间 open 新连接——新连接仍收帧（摘空不删空 map） ----

    @Test
    public void 摘连后open新连接_新连接仍收帧() {
        AgentSessionStreamHub hub = newHub();
        RecordingSender first = new RecordingSender();
        RecordingSender second = new RecordingSender();
        AgentSessionState running = state("s8", AgentSessionState.RUNNING);
        hub.open(first, running, 0, since -> java.util.Collections.emptyList(), () -> { });

        // 摘除唯一连接（onCompletion/onTimeout 路径；摘空后不删 session map）
        hub.removeSender("s8", first);
        // 摘除瞬间（空 map 尚存）open 新连接：computeIfAbsent 复用同 map——若摘连路径
        // 误删整表，此窗口内的新连接会成为收不到任何帧的孤儿
        hub.open(second, running, 0, since -> java.util.Collections.emptyList(), () -> { });

        hub.publishLive("s8", "应答中·已收 3 字\nabc");
        long statusBefore = second.count("status"); // open 建连回放已含 1 条 status
        hub.publishStatus(running);
        Assert.assertEquals("新连接应收到 live 帧（不被摘连竞态孤儿化）", 1, second.count("live"));
        Assert.assertEquals("新连接应收到 status 帧（回放之外再 +1）", statusBefore + 1, second.count("status"));
        Assert.assertEquals("旧连接摘除后不再收帧", 0, first.count("live"));
    }
}
