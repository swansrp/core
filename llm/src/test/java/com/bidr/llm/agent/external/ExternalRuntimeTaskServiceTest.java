package com.bidr.llm.agent.external;

import com.bidr.llm.agent.runtime.dto.CancelResult;
import com.bidr.llm.agent.runtime.dto.DeleteResult;
import com.bidr.llm.agent.runtime.dto.SessionInfo;
import com.bidr.llm.agent.runtime.dto.TurnItem;
import com.bidr.llm.agent.runtime.dto.TurnPage;
import com.bidr.llm.agent.runtime.event.RuntimeEvents;
import com.bidr.llm.agent.runtime.spi.AgentRuntimeProvider;
import com.bidr.llm.agent.runtime.spi.RuntimeTurnLink;
import com.bidr.llm.agent.runtime.spi.SessionCreateCmd;
import com.bidr.llm.agent.runtime.spi.TurnOpenCmd;
import com.bidr.llm.agent.session.AgentEvent;
import com.bidr.llm.agent.session.AgentSessionState;
import com.bidr.llm.agent.session.InMemoryAgentSessionStore;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.Assert;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * Title: ExternalRuntimeTaskServiceTest
 * Description: 形态 B 接线的核心契约测试（不依赖网络与真上游）：
 * ① 外部任务被表示成一条 core/llm 会话，规范事件被翻译成 {@code AgentEvent} 落进事件流；
 * ② 工具事件 payload 键位与 core/llm 原生工具事件一致（前端两路共用一个视图模型）；
 * ③ 终局/断流/未接入三种收口都有明确状态，不悬挂。
 *
 * @author sharp
 * @since 2026/9/24
 */
public class ExternalRuntimeTaskServiceTest {

    /** 脚本化上游：按序吐规范事件，然后收流 */
    private static final class ScriptedLink implements RuntimeTurnLink {
        private final List<String> frames;
        private int index;

        ScriptedLink(List<String> frames) {
            this.frames = frames;
        }

        @Override
        public String nextFrame() {
            return index < frames.size() ? frames.get(index++) : null;
        }

        @Override
        public void close() {
            // 测试无需释放
        }
    }

    private static final class FakeProvider implements AgentRuntimeProvider {
        private final List<String> frames;
        private String cancelledTurnId;

        FakeProvider(List<String> frames) {
            this.frames = frames;
        }

        @Override
        public String name() {
            return "fake";
        }

        @Override
        public List<com.bidr.llm.agent.runtime.dto.AgentInfo> listAgents() {
            return Collections.emptyList();
        }

        @Override
        public SessionInfo createSession(SessionCreateCmd cmd) {
            SessionInfo info = new SessionInfo();
            info.setSessionId("up-session-1");
            info.setAgentCode(cmd.getAgentCode());
            return info;
        }

        @Override
        public SessionInfo validateSession(String sessionId) {
            return createSession(new SessionCreateCmd(sessionId, null, null, null, null, null));
        }

        @Override
        public DeleteResult deleteSession(String sessionId) {
            return null;
        }

        @Override
        public TurnPage history(String sessionId, String before, Integer limit) {
            return null;
        }

        @Override
        public TurnItem turn(String sessionId, String turnId) {
            return null;
        }

        @Override
        public CancelResult cancel(String sessionId, String turnId) {
            cancelledTurnId = turnId;
            return null;
        }

        @Override
        public RuntimeTurnLink openTurn(TurnOpenCmd cmd) {
            return new ScriptedLink(frames);
        }

        @Override
        public RuntimeTurnLink attachTurn(String sessionId, String turnId) {
            return new ScriptedLink(Collections.emptyList());
        }
    }

    private static String toolCallFrame() {
        ObjectNode data = RuntimeEvents.newData();
        data.put("tool_name", "terminal");
        data.put("tool_call_id", "c1");
        data.putObject("arguments").put("command", "ls -A");
        return RuntimeEvents.frame(RuntimeEvents.TOOL_CALL, "up-session-1", "t-1", data);
    }

    private static String toolResultFrame() {
        ObjectNode data = RuntimeEvents.newData();
        data.put("tool_name", "terminal");
        data.put("tool_call_id", "c1");
        data.put("output", "3 files");
        return RuntimeEvents.frame(RuntimeEvents.TOOL_RESULT, "up-session-1", "t-1", data);
    }

    private static List<String> happyFrames() {
        return Arrays.asList(
                RuntimeEvents.frame(RuntimeEvents.ACCEPTED, "up-session-1", "t-1", null),
                toolCallFrame(),
                toolResultFrame(),
                RuntimeEvents.deltaFrame(RuntimeEvents.TEXT_DELTA, "up-session-1", "t-1", "答案是 3"),
                doneFrame("答案是 3"));
    }

    private static String doneFrame(String text) {
        ObjectNode data = RuntimeEvents.newData();
        data.put("text", text);
        return RuntimeEvents.frame(RuntimeEvents.DONE, "up-session-1", "t-1", data);
    }

    private static AgentSessionState awaitTerminal(InMemoryAgentSessionStore store, String taskId)
            throws InterruptedException {
        for (int i = 0; i < 100; i++) {
            AgentSessionState state = store.getState(taskId);
            if (state != null && state.isTerminal()) {
                return state;
            }
            Thread.sleep(50L);
        }
        throw new AssertionError("任务未在预期时间内收口");
    }

    @Test
    public void 规范事件被翻译进本地任务会话事件流() throws Exception {
        InMemoryAgentSessionStore store = new InMemoryAgentSessionStore();
        ExternalRuntimeTaskService service =
                new ExternalRuntimeTaskService(store, () -> new FakeProvider(happyFrames()));

        String taskId = service.submit("parent-1", "default", "数一下文件数");
        AgentSessionState state = awaitTerminal(store, taskId);

        Assert.assertEquals("终局应为 FINISHED", AgentSessionState.FINISHED, state.getStatus());
        Assert.assertEquals("结论取终局帧全文", "答案是 3", state.getSummary());
        Assert.assertEquals("agentKey 标记外部来源", "ext:default", state.getAgentKey());
        Assert.assertEquals("父会话回链落在 subject", "parent-1", state.getSubject());
        Assert.assertNull("终态必须清空 live", state.getLive());

        List<AgentEvent> events = store.events(taskId, 0);
        List<AgentEvent> calls = new ArrayList<>();
        List<AgentEvent> results = new ArrayList<>();
        for (AgentEvent ev : events) {
            if (AgentEvent.TOOL_CALL.equals(ev.getType())) {
                calls.add(ev);
            } else if (AgentEvent.TOOL_RESULT.equals(ev.getType())) {
                results.add(ev);
            }
        }
        Assert.assertEquals(1, calls.size());
        Assert.assertEquals(1, results.size());

        Map<?, ?> call = (Map<?, ?>) calls.get(0).getPayload();
        Assert.assertEquals("键位须与 core/llm 原生工具事件一致", "c1", call.get("tool_call_id"));
        Assert.assertEquals("terminal", call.get("tool_name"));
        Assert.assertTrue("arguments 为 JSON 文本", String.valueOf(call.get("arguments")).contains("ls -A"));

        Map<?, ?> ret = (Map<?, ?>) results.get(0).getPayload();
        Assert.assertEquals("c1", ret.get("tool_call_id"));
        Assert.assertEquals("3 files", ret.get("output"));
    }

    @Test
    public void 进度查询按游标推进且结果可取() throws Exception {
        InMemoryAgentSessionStore store = new InMemoryAgentSessionStore();
        ExternalRuntimeTaskService service =
                new ExternalRuntimeTaskService(store, () -> new FakeProvider(happyFrames()));
        String taskId = service.submit(null, null, "任务");
        awaitTerminal(store, taskId);

        Map<String, Object> first = service.progress(taskId, 0, 50);
        Assert.assertEquals(AgentSessionState.FINISHED, first.get("status"));
        long cursor = ((Number) first.get("cursor")).longValue();
        Assert.assertTrue("应读到事件并给出游标", cursor > 0);
        Assert.assertTrue("从首游标再查应为空",
                ((List<?>) service.progress(taskId, cursor, 50).get("newEvents")).isEmpty());

        Map<String, Object> result = service.result(taskId);
        Assert.assertNotNull(result);
        Assert.assertEquals("答案是 3", result.get("summary"));
    }

    @Test
    public void 上游收流但无终局按断流失败收口() throws Exception {
        InMemoryAgentSessionStore store = new InMemoryAgentSessionStore();
        List<String> noTerminal = Arrays.asList(
                RuntimeEvents.frame(RuntimeEvents.ACCEPTED, "up-session-1", "t-1", null),
                toolCallFrame());
        ExternalRuntimeTaskService service =
                new ExternalRuntimeTaskService(store, () -> new FakeProvider(noTerminal));

        String taskId = service.submit(null, "default", "任务");
        AgentSessionState state = awaitTerminal(store, taskId);

        Assert.assertEquals(AgentSessionState.FAILED, state.getStatus());
        Assert.assertTrue("错误须点明断流", state.getError() != null && state.getError().contains("终局"));
    }

    @Test
    public void 未接入上游时明确回绝不静默假成功() {
        InMemoryAgentSessionStore store = new InMemoryAgentSessionStore();
        ExternalRuntimeTaskService service = new ExternalRuntimeTaskService(store, () -> null);
        Assert.assertFalse(service.available());
        try {
            service.submit(null, "default", "任务");
            Assert.fail("未接入时必须抛错而非返回句柄");
        } catch (IllegalStateException expected) {
            Assert.assertTrue(expected.getMessage().contains("未接入"));
        }
    }

    @Test
    public void 未知任务句柄查询报错() {
        InMemoryAgentSessionStore store = new InMemoryAgentSessionStore();
        ExternalRuntimeTaskService service = new ExternalRuntimeTaskService(store, () -> null);
        try {
            service.progress("nope", 0, 20);
            Assert.fail("未知 taskId 应报错");
        } catch (IllegalArgumentException expected) {
            Assert.assertTrue(expected.getMessage().contains("不存在"));
        }
    }
}
