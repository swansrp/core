package com.bidr.llm.agent.session;

import com.bidr.llm.agent.AutonomousAgentDefinition;
import com.bidr.llm.agent.DetachPolicy;
import com.bidr.llm.agent.file.FileRef;
import org.junit.After;
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
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/**
 * Title: AgentSessionStartPausedTest
 * Description: 「建后未起跑」（{@code session/start?startPaused=true}）协议钉桩
 * （plain JUnit4 + Mockito，不起 Spring 容器）：
 * <ul>
 *     <li><b>不起跑</b>：startPaused 只落 PAUSED 快照，业务执行体在 resume 前一次都不调用；</li>
 *     <li><b>附件可先传</b>：暂停窗口内 {@code attachFile} 正常追加事件（首轮提示词读它）；</li>
 *     <li><b>resume 起跑</b>：resume 才创建 run 线程，执行体运行、快照回 RUNNING；</li>
 *     <li><b>stop 收口</b>：未 resume 直接 stop → STOPPED，执行体永不运行（不留悬挂会话）；</li>
 *     <li><b>回归</b>：不带 startPaused 的 start 仍立即起跑（既有调用点零行为变化）。</li>
 * </ul>
 * 说明：本模块 surefire 走 JUnit4 provider（见 AgentSessionFileAttachmentTest 同款说明），
 * 写成 JUnit5 会被静默跳过（Tests run: 0）。
 *
 * @author Sharp
 * @since 2026/11/16
 */
public class AgentSessionStartPausedTest {

    private static final String AGENT_KEY = "probe";

    private final InMemoryAgentSessionStore store = new InMemoryAgentSessionStore();

    @After
    public void tearDown() {
        probe.release.countDown();
    }

    /** 探针执行体：start 到来即计数，随后阻塞到 release（便于断言 RUNNING 快照） */
    private final ProbeDefinition probe = new ProbeDefinition();

    @Test
    public void startPausedDefersRunUntilResume() throws Exception {
        AgentSessionService service = service();

        AgentSessionState state = service.start(AGENT_KEY, payload(), "alice",
                DetachPolicy.KEEP_RUNNING, true);

        assertNotNull(state.getSessionId());
        assertEquals("建后未起跑：快照直接是 PAUSED", AgentSessionState.PAUSED, state.getStatus());
        assertEquals(AgentSessionState.PAUSED, store.getState(state.getSessionId()).getStatus());
        assertFalse("resume 前业务执行体不得被调用", probe.started.await(300, TimeUnit.MILLISECONDS));

        // 暂停窗口内先传附件：事件流写入正常，首轮提示词由 resume 时的 run 线程组装
        long seq = service.attachFile(state.getSessionId(),
                new FileRef("f-1", "素材.docx", 12L, "application/vnd.openxmlformats-officedocument.wordprocessingml.document", null));
        assertTrue("暂停窗口内附件事件应正常入流", seq > 0);

        service.resume(state.getSessionId(), null);

        assertTrue("resume 后应创建 run 线程并调用执行体", probe.started.await(3, TimeUnit.SECONDS));
        assertEquals(AgentSessionState.RUNNING, store.getState(state.getSessionId()).getStatus());
        assertTrue("附件在起跑前已落事件流（首轮提示词可见）",
                store.events(state.getSessionId(), 0).stream()
                        .anyMatch(e -> AgentEvent.FILE_ATTACHED.equals(e.getType())));
    }

    /** 预解析通过后登记：payload 带 parse_chars（前端文件卡据此从「已上传」点亮为「已解析」） */
    @Test
    @SuppressWarnings("unchecked")
    public void attachFileCarriesParseCharsWhenPreParsed() throws Exception {
        AgentSessionService service = service();
        AgentSessionState state = service.start(AGENT_KEY, payload(), "alice",
                DetachPolicy.KEEP_RUNNING, true);

        service.attachFile(state.getSessionId(),
                new FileRef("f-2", "台账.xlsx", 8L, null, null), 1234);

        Map<String, Object> attached = store.events(state.getSessionId(), 0).stream()
                .filter(e -> AgentEvent.FILE_ATTACHED.equals(e.getType()))
                .map(e -> (Map<String, Object>) e.getPayload())
                .findFirst().orElseThrow(AssertionError::new);
        assertEquals("预解析通过＝事件带正文字符数", 1234, attached.get("parse_chars"));
        assertEquals("既有键口径不漂移", "f-2", attached.get("file_id"));
    }

    /** 运行期直传（未经预解析）：不声明 parse_chars，payload 仍是纯引用四键 */
    @Test
    @SuppressWarnings("unchecked")
    public void attachFileWithoutParseKeepsPayloadMinimal() throws Exception {
        AgentSessionService service = service();
        AgentSessionState state = service.start(AGENT_KEY, payload(), "alice",
                DetachPolicy.KEEP_RUNNING, true);

        service.attachFile(state.getSessionId(),
                new FileRef("f-3", "现场照片.jpg", 9L, "image/jpeg", null));

        Map<String, Object> attached = store.events(state.getSessionId(), 0).stream()
                .filter(e -> AgentEvent.FILE_ATTACHED.equals(e.getType()))
                .map(e -> (Map<String, Object>) e.getPayload())
                .findFirst().orElseThrow(AssertionError::new);
        assertFalse("未经预解析不得声明已解析", attached.containsKey("parse_chars"));
    }

    @Test
    public void stopBeforeResumeFinalizesStopped() throws Exception {
        AgentSessionService service = service();
        AgentSessionState state = service.start(AGENT_KEY, payload(), "alice",
                DetachPolicy.KEEP_RUNNING, true);

        service.stop(state.getSessionId());

        assertEquals(AgentSessionState.STOPPED, store.getState(state.getSessionId()).getStatus());
        assertFalse("未 resume 即 stop：执行体永不运行", probe.started.await(300, TimeUnit.MILLISECONDS));
        assertTrue("停止事件应入流", store.events(state.getSessionId(), 0).stream()
                .anyMatch(e -> AgentEvent.STOPPED.equals(e.getType())));
    }

    @Test
    public void plainStartStillRunsImmediately() throws Exception {
        AgentSessionService service = service();
        AgentSessionState state = service.start(AGENT_KEY, payload(), "alice", DetachPolicy.KEEP_RUNNING);

        assertEquals(AgentSessionState.RUNNING, state.getStatus());
        assertTrue("既有调用点（不带 startPaused）零行为变化", probe.started.await(3, TimeUnit.SECONDS));
    }

    /**
     * 终态续跑：STOPPED 会话 resume ⇒ 同一 sessionId 追加新 run（业务探针第二次被调用），
     * 首轮参数快照落库（续跑重建依据），事件流追加 RESUMED。
     */
    @Test
    public void resumeStoppedSessionLaunchesNewRun() throws Exception {
        AgentSessionService service = service();
        AgentSessionState state = service.start(AGENT_KEY, payload(), "alice", DetachPolicy.KEEP_RUNNING);
        String id = state.getSessionId();
        assertTrue(probe.started.await(3, TimeUnit.SECONDS));
        assertEquals("首轮参数快照落库（续跑重建依据）", "生成今天的监理日志",
                store.getState(id).getPayload().get("instruction"));

        service.stop(id);
        waitStatus(id, AgentSessionState.STOPPED);

        service.resume(id, "继续完成剩余工作");

        long deadline = System.currentTimeMillis() + 3000;
        while (probe.calls.get() < 2 && System.currentTimeMillis() < deadline) {
            Thread.sleep(20);
        }
        assertEquals("终态续跑应再起一个 run（执行体第二次被调用）", 2, probe.calls.get());
        assertTrue("续跑指令应入事件流", store.events(id, 0).stream()
                .anyMatch(e -> AgentEvent.RESUMED.equals(e.getType())));
    }

    /** 已完成（FINISHED）不走续跑：继续＝修订，应开新会话——resume 明确拒绝 */
    @Test
    public void resumeFinishedSessionRejected() throws Exception {
        AgentSessionService service = service();
        probe.release.countDown();
        AgentSessionState state = service.start(AGENT_KEY, payload(), "alice", DetachPolicy.KEEP_RUNNING);
        waitStatus(state.getSessionId(), AgentSessionState.FINISHED);

        try {
            service.resume(state.getSessionId(), "继续");
            org.junit.Assert.fail("已完成会话不应允许续跑");
        } catch (com.bidr.kernel.exception.ServiceException e) {
            assertTrue("提示应指引开新会话: " + e.getMessage(),
                    e.getMessage().contains("已完成") || e.getMessage().contains("新会话"));
        }
    }

    /** 轮询等状态落定（run 线程收口异步；终态断言专用） */
    private void waitStatus(String sessionId, String expected) throws InterruptedException {
        long deadline = System.currentTimeMillis() + 3000;
        while (System.currentTimeMillis() < deadline) {
            AgentSessionState current = store.getState(sessionId);
            if (current != null && expected.equals(current.getStatus())) {
                return;
            }
            Thread.sleep(20);
        }
        throw new AssertionError("状态未在超时内变为 " + expected
                + "，当前=" + (store.getState(sessionId) == null ? null : store.getState(sessionId).getStatus()));
    }

    private AgentSessionService service() {
        return new AgentSessionService(store, Collections.singletonList(probe),
                Mockito.mock(ObjectProvider.class));
    }

    private static Map<String, Object> payload() {
        Map<String, Object> payload = new HashMap<>();
        payload.put("instruction", "生成今天的监理日志");
        return payload;
    }

    /** 最小执行体探针：记录 start 调用并阻塞到测试放行（避免线程立刻收口干扰状态断言） */
    private static class ProbeDefinition implements AutonomousAgentDefinition {

        private final CountDownLatch started = new CountDownLatch(1);
        /** 执行体启动次数（终态续跑用例断言「又起了一个 run」） */
        private final java.util.concurrent.atomic.AtomicInteger calls =
                new java.util.concurrent.atomic.AtomicInteger();
        private final CountDownLatch release = new CountDownLatch(1);

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
        public void start(AgentSessionContext ctx, Map<String, Object> payload) throws Exception {
            calls.incrementAndGet();
            started.countDown();
            release.await(5, TimeUnit.SECONDS);
        }
    }
}