package com.bidr.llm.agent.session;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.After;
import org.junit.Assert;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;

/**
 * Title: AgentSessionContextStreamTest
 * Description: 会话上下文与 SSE 通道的分层契约测试（M18 棒2）——锁死三条腿：
 * ①pushLive 高频（连续多次 &lt;1s 间隔）→ saveState 恰好 1 次（300ms 级 live 帧
 * 不随帧数放大 Redis 写）且每帧都经 hub 发布（SSE 全量逐帧可达）；
 * ②高频帧后的下一写点（emit()/askQuestion()）触发 dirty flush——最终帧进快照；
 * ③emit() 落库成功后事件帧发布（seq/type/payload 对，SSE id=seq）。
 * 记录型 sender 挂真实 hub（bus null＝本地投递形态）；store 用计数壳包
 * InMemoryAgentSessionStore（saveState 次数即 Redis 写次数的机器判据）。
 *
 * @author Sharp
 * @since 2026/10/23
 */
public class AgentSessionContextStreamTest {

    private static final ObjectMapper OM = new ObjectMapper();

    private final List<AgentSessionStreamHub> hubs = new ArrayList<>();

    @After
    public void tearDown() {
        hubs.forEach(AgentSessionStreamHub::shutdown);
    }

    /** 记录型 FrameSender（同 AgentSessionStreamHubTest，本类独立持有一份防跨类耦合） */
    private static final class RecordingSender implements AgentSessionStreamHub.FrameSender {
        final List<String> names = new ArrayList<>();
        final List<String> datas = new ArrayList<>();
        final List<Long> ids = new ArrayList<>();
        boolean completed;

        @Override
        public void event(String name, String data, Long id) {
            names.add(name);
            datas.add(data);
            ids.add(id);
        }

        @Override
        public void comment(String text) {
        }

        @Override
        public void complete() {
            completed = true;
        }

        long count(String name) {
            return names.stream().filter(name::equals).count();
        }
    }

    /** 计数壳：saveState 次数＝快照持久次数（live 节流断言的机器判据） */
    private static final class CountingStore extends InMemoryAgentSessionStore {
        int saves;

        @Override
        public void saveState(AgentSessionState state) {
            saves++;
            super.saveState(state);
        }
    }

    private static final class Fixture {
        final String sessionId;
        final CountingStore store = new CountingStore();
        final AgentSessionStreamHub hub;
        final RecordingSender sender = new RecordingSender();
        final AgentSessionContext ctx;

        Fixture(String sessionId, List<AgentSessionStreamHub> registry) {
            this.sessionId = sessionId;
            AgentSessionState state = new AgentSessionState();
            state.setSessionId(sessionId);
            state.setAgentKey("probe");
            state.setStatus(AgentSessionState.RUNNING);
            state.setStartedAt(System.currentTimeMillis());
            store.saveState(state);
            store.saves = 0; // 播种那笔不计入（断言口径＝ctx 触发的持久次数）
            hub = new AgentSessionStreamHub(OM, Runnable::run); // 同线程执行器：同步断言（I4 测试接缝）
            registry.add(hub);
            hub.open(sender, state, 0, since -> Collections.emptyList(), () -> { });
            ctx = new AgentSessionContext(state, store, new HashMap<>(), hub);
        }
    }

    // ---- 腿①：pushLive 高频——快照持久不随帧数放大，live 帧逐帧发布 ----

    @Test
    public void pushLive高频_快照只落一次且每帧都发布() {
        Fixture f = new Fixture("s-live-1", hubs);
        int pushes = 5;
        for (int i = 1; i <= pushes; i++) {
            f.ctx.pushLive("应答中·已收 " + (i * 7) + " 字\n累积第" + i + "段");
        }

        // 5 次推送全部经 hub 发布（SSE 全量逐帧，300ms 级不打折）
        Assert.assertEquals(pushes, f.sender.count("live"));
        // 首帧触发节流持久，其余 4 帧节流窗口内只置 dirty：saveState 恰好 1 次
        Assert.assertEquals(1, f.store.saves);
    }

    // ---- 腿②：dirty flush——高频帧后的下一写点把最终帧补进快照 ----

    @Test
    public void 高频帧后emit触发flush_快照live为最后一次全文() throws Exception {
        Fixture f = new Fixture("s-flush-1", hubs);
        f.ctx.pushLive("思考中·已思 3 字\n第一段");
        f.ctx.pushLive("思考中·已思 6 字\n第一段第二段");
        f.ctx.pushLive("思考中·已思 9 字\n第一段第二段第三段");
        Assert.assertEquals(1, f.store.saves);

        long seq = f.ctx.emit(AgentEvent.LOG, "突发结束");
        // flush 触发：第 2 次持久，且快照 live＝最后一次推送全文（Redis 客户端不丢尾）
        Assert.assertEquals(2, f.store.saves);
        Assert.assertEquals("思考中·已思 9 字\n第一段第二段第三段",
                f.store.getState(f.sessionId).getLive());
        // flush 之后 emit 的事件帧照常发布
        Assert.assertTrue(seq > 0);
        Assert.assertEquals(1, f.sender.count("events"));
    }

    @Test
    public void 高频帧后askQuestion触发flush并发布status帧() {
        Fixture f = new Fixture("s-flush-2", hubs);
        long statusBefore = f.sender.count("status");
        f.ctx.pushLive("应答中·已收 2 字\n片段一");
        f.ctx.pushLive("应答中·已收 4 字\n片段一片段二");
        Assert.assertEquals(1, f.store.saves);

        f.ctx.askQuestion("选哪个口径？", Arrays.asList("口径A", "口径B"));
        // flush 触发（第 2 次持久）＋语义变更点发 status 帧（建连回放那张之外再 +1）
        Assert.assertEquals(2, f.store.saves);
        Assert.assertEquals(statusBefore + 1, f.sender.count("status"));
        // 问题事件帧也发布（askQuestion 的 emit 走统一通道）
        Assert.assertEquals(1, f.sender.count("events"));
    }

    // ---- 腿③：emit() 后事件帧发布（seq/type/payload 对） ----

    @Test
    public void emit后事件帧发布_seq与type对齐() throws Exception {
        Fixture f = new Fixture("s-emit-1", hubs);
        long seq = f.ctx.emit(AgentEvent.LOG, "hello 事件");

        Assert.assertEquals(1L, seq);
        Assert.assertEquals(1, f.sender.count("events"));
        int idx = f.sender.names.indexOf("events");
        Assert.assertEquals(Long.valueOf(seq), f.sender.ids.get(idx));
        JsonNode frame = OM.readTree(f.sender.datas.get(idx));
        Assert.assertEquals(seq, frame.path("seq").asLong());
        Assert.assertEquals(AgentEvent.LOG, frame.path("type").asText());
        Assert.assertEquals("hello 事件", frame.path("payload").asText());
    }

    /** 终态清空（pushLive(null)）不走节流直接持久——run 线程收口前的 live 归零立即可见 */
    @Test
    public void pushLiveNull不走节流直接持久() {
        Fixture f = new Fixture("s-live-null", hubs);
        f.ctx.pushLive("应答中·已收 5 字\n尾声");
        f.ctx.pushLive("应答中·已收 9 字\n尾声补全");
        f.ctx.pushLive(null);
        // 首帧 1 次＋null 直持久 1 次（中间帧节流跳过）
        Assert.assertEquals(2, f.store.saves);
        Assert.assertNull(f.store.getState(f.sessionId).getLive());
        Assert.assertEquals(3, f.sender.count("live"));
    }
}
