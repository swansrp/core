package com.bidr.llm.agent.session;

import com.bidr.llm.agent.AutonomousAgentDefinition;
import com.bidr.llm.agent.DetachPolicy;
import com.bidr.llm.agent.sandbox.AgentSandbox;
import com.bidr.llm.flow.StaticObjectProvider;
import org.junit.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.ObjectProvider;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * Title: AgentSessionServiceSandboxHookTest
 * Description: 会话终态沙箱销毁钩子钉桩（Cube 清扫对齐棒3，plain JUnit4 + Mockito，
 * 不起 Spring 容器）——{@code finalizeStages} 是三路终态汇聚单点，沙箱 {@code destroy}
 * 挂此处一处即全覆盖，本测试按三条入口分别钉死：
 * <ul>
 *     <li><b>stop 收口</b>：「建后未起跑」会话 stop（{@code finishDeferredStart} 路径）后 destroy 被调；</li>
 *     <li><b>run 线程收口</b>：正常执行完成（run 主收口路径）后 destroy 被调且终态照常落库；</li>
 *     <li><b>查询侧失联</b>：status 心跳超时改写 STOPPED（HTTP 线程路径）后 destroy 被调；</li>
 *     <li><b>异常不阻断</b>：destroy 抛错只吞不外溢——stop 方法不抛、停止事件照常入流（收口走完）；</li>
 *     <li><b>端口缺席</b>：sandboxProvider 为 null 或 bean 缺席（getIfAvailable 为 null）
 *         都静默跳过，终态流程零变化。</li>
 * </ul>
 * 说明：本模块 surefire 走 JUnit4 provider（见 AgentSessionStartPausedTest 同款说明），
 * 写成 JUnit5 会被静默跳过（Tests run: 0）。
 *
 * @author Sharp
 * @since 2026/11/17
 */
public class AgentSessionServiceSandboxHookTest {

    private static final String AGENT_KEY = "probe";

    private final InMemoryAgentSessionStore store = new InMemoryAgentSessionStore();

    /** 立即完成的探针执行体：start 到来即计数并返回（run 线程随即走终态收口） */
    private final ProbeDefinition probe = new ProbeDefinition();

    @Test
    public void stopDeferredSessionDestroysSandbox() throws Exception {
        AgentSandbox sandbox = Mockito.mock(AgentSandbox.class);
        AgentSessionService service = service(new StaticObjectProvider<>(sandbox));

        AgentSessionState state = service.start(AGENT_KEY, payload(), "alice",
                DetachPolicy.KEEP_RUNNING, true);
        service.stop(state.getSessionId());

        Mockito.verify(sandbox, Mockito.times(1)).destroy(state.getSessionId());
        assertEquals("stop 收口后快照即终态", AgentSessionState.STOPPED,
                store.getState(state.getSessionId()).getStatus());
        assertTrue("停止事件照常入流（收口完整）",
                hasEvent(state.getSessionId(), AgentEvent.STOPPED));
        assertFalse("run 线程从未起跑", probe.started.await(200, TimeUnit.MILLISECONDS));
    }

    @Test
    public void runCompletionDestroysSandbox() throws Exception {
        CountDownLatch destroyed = new CountDownLatch(1);
        AgentSandbox sandbox = Mockito.mock(AgentSandbox.class);
        Mockito.doAnswer(invocation -> {
            destroyed.countDown();
            return null;
        }).when(sandbox).destroy(Mockito.anyString());
        AgentSessionService service = service(new StaticObjectProvider<>(sandbox));

        AgentSessionState state = service.start(AGENT_KEY, payload(), "alice");

        assertTrue("run 线程终态收口应触发沙箱销毁", destroyed.await(3, TimeUnit.SECONDS));
        Mockito.verify(sandbox, Mockito.times(1)).destroy(state.getSessionId());
        assertTrue("销毁发生在 saveState 之前，FINISH 事件随后照常入流（收口未受阻）",
                awaitEvent(state.getSessionId(), AgentEvent.FINISH, 3000));
        assertEquals(AgentSessionState.FINISHED, store.getState(state.getSessionId()).getStatus());
    }

    @Test
    public void orphanStatusDestroysSandbox() {
        AgentSandbox sandbox = Mockito.mock(AgentSandbox.class);
        AgentSessionService service = service(new StaticObjectProvider<>(sandbox));

        AgentSessionState state = service.start(AGENT_KEY, payload(), "alice",
                DetachPolicy.KEEP_RUNNING, true);
        // InMemory 存储按引用直存：把快照心跳拨老即触发查询侧失联分支（HTTP 线程路径）
        store.getState(state.getSessionId())
                .setHeartbeat(System.currentTimeMillis() - 200_000L);

        service.status(state.getSessionId());

        Mockito.verify(sandbox, Mockito.times(1)).destroy(state.getSessionId());
        assertEquals("失联判定后快照改写 STOPPED", AgentSessionState.STOPPED,
                store.getState(state.getSessionId()).getStatus());
    }

    @Test
    public void destroyFailureDoesNotBlockFinalization() {
        AgentSandbox sandbox = Mockito.mock(AgentSandbox.class);
        Mockito.doThrow(new RuntimeException("沙箱底座已失联"))
                .when(sandbox).destroy(Mockito.anyString());
        AgentSessionService service = service(new StaticObjectProvider<>(sandbox));

        AgentSessionState state = service.start(AGENT_KEY, payload(), "alice",
                DetachPolicy.KEEP_RUNNING, true);
        // 不得抛：终态收口路径任何异常都可能污染会话落库，钩子必须全吞
        service.stop(state.getSessionId());

        Mockito.verify(sandbox, Mockito.times(1)).destroy(state.getSessionId());
        assertEquals("销毁失败后终态照常落库", AgentSessionState.STOPPED,
                store.getState(state.getSessionId()).getStatus());
        assertTrue("停止事件照常入流（saveState 与事件链未受阻）",
                hasEvent(state.getSessionId(), AgentEvent.STOPPED));
    }

    @Test
    public void nullSandboxProviderKeepsTerminalFlow() {
        // provider 本身为 null（便利构造桥接口径）
        AgentSessionService noProvider = service(null);
        AgentSessionState state = noProvider.start(AGENT_KEY, payload(), "alice",
                DetachPolicy.KEEP_RUNNING, true);
        noProvider.stop(state.getSessionId());
        assertEquals(AgentSessionState.STOPPED, store.getState(state.getSessionId()).getStatus());
        assertTrue(hasEvent(state.getSessionId(), AgentEvent.STOPPED));

        // provider 在场但 bean 缺席（getIfAvailable 返回 null，Spring 装配常态）
        AgentSessionService emptyProvider = service(new StaticObjectProvider<AgentSandbox>(null));
        AgentSessionState state2 = emptyProvider.start(AGENT_KEY, payload(), "alice",
                DetachPolicy.KEEP_RUNNING, true);
        emptyProvider.stop(state2.getSessionId());
        assertEquals(AgentSessionState.STOPPED, store.getState(state2.getSessionId()).getStatus());
        assertTrue(hasEvent(state2.getSessionId(), AgentEvent.STOPPED));
    }

    private AgentSessionService service(ObjectProvider<AgentSandbox> sandboxProvider) {
        return new AgentSessionService(store, Collections.singletonList(probe),
                new StaticObjectProvider<>(null), new StaticObjectProvider<>(null), sandboxProvider);
    }

    private boolean hasEvent(String sessionId, String type) {
        return store.events(sessionId, 0).stream().anyMatch(e -> type.equals(e.getType()));
    }

    /** 轮询等事件入流（run 线程异步收口，destroy 先于 FINISH 事件） */
    private boolean awaitEvent(String sessionId, String type, long timeoutMillis) throws InterruptedException {
        long deadline = System.currentTimeMillis() + timeoutMillis;
        while (System.currentTimeMillis() < deadline) {
            if (hasEvent(sessionId, type)) {
                return true;
            }
            Thread.sleep(20);
        }
        return hasEvent(sessionId, type);
    }

    private static Map<String, Object> payload() {
        Map<String, Object> payload = new HashMap<>();
        payload.put("instruction", "生成今天的监理日志");
        return payload;
    }

    /** 最小执行体探针：记录 start 调用即返回（让 run 线程自然走 FINISHED 收口） */
    private static class ProbeDefinition implements AutonomousAgentDefinition {

        private final CountDownLatch started = new CountDownLatch(1);

        @Override
        public String agentKey() {
            return AGENT_KEY;
        }

        @Override
        public String displayName() {
            return "探针";
        }

        @Override
        public String skillCode() {
            return null;
        }

        @Override
        public void start(AgentSessionContext ctx, Map<String, Object> payload) {
            started.countDown();
        }
    }
}
