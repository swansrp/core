package com.bidr.llm.agent.session;

import com.bidr.llm.agent.AgentLoopListener;
import org.junit.Assert;
import org.junit.Test;

import java.util.HashMap;
import java.util.List;

/**
 * Title: AgentSessionContextRecallTest
 * Description: 会话链句柄回捞契约测试（计划 §6.6，先证明「事件流能按 id 捞回原文」）：
 * 锁死 I4（事件流是全文唯一真源）与 I13（回捞只读，不追加任何事件）
 *
 * @author sharp
 * @since 2026/9/25
 */
public class AgentSessionContextRecallTest {

    /** 被测上下文 + 其背后的内存 store（事件流核验用同一实例，不侵入被测类） */
    private static final class Fixture {
        final AgentSessionContext ctx;
        final InMemoryAgentSessionStore store;

        Fixture(String sessionId) {
            AgentSessionState state = new AgentSessionState();
            state.setSessionId(sessionId);
            state.setAgentKey("probe");
            state.setStatus(AgentSessionState.RUNNING);
            store = new InMemoryAgentSessionStore();
            store.saveState(state);
            ctx = new AgentSessionContext(state, store, new HashMap<>());
        }

        void emitResult(String id, String output) {
            ctx.loopListener().onToolResult(id, "bigReport", output);
        }
    }

    /** I4：按 id 命中返回事件流中的原文全文（逐字符等价） */
    @Test
    public void 按id命中应返回原文全文() {
        Fixture f = new Fixture("s-recall-1");
        String big = "【解析报告】" + repeat("中文内容", 3000) + "KEY-A-8888";
        f.emitResult("call-1", "短结果");
        f.emitResult("call-2", big);

        AgentLoopListener listener = f.ctx.loopListener();
        Assert.assertEquals(big, listener.recallToolResult("call-2"));
        Assert.assertEquals("短结果", listener.recallToolResult("call-1"));
    }

    /** 同 id 多条（cachedTools 重调等场景）：返回最后一条 */
    @Test
    public void 同id多条时应返回最后一条() {
        Fixture f = new Fixture("s-recall-2");
        f.emitResult("call-1", "第一次结果");
        f.emitResult("call-1", "第二次结果");
        Assert.assertEquals("第二次结果", f.ctx.loopListener().recallToolResult("call-1"));
    }

    @Test
    public void 未知句柄应返回null() {
        Fixture f = new Fixture("s-recall-3");
        f.emitResult("call-1", "结果");
        Assert.assertNull(f.ctx.loopListener().recallToolResult("call-404"));
        Assert.assertNull(f.ctx.loopListener().recallToolResult(null));
        Assert.assertNull(f.ctx.loopListener().recallToolResult("  "));
    }

    /** 历史 String 型 payload 事件（旧格式 TOOL_RESULT 直接存文本）：静默跳过不抛 */
    @Test
    public void payload为字符串的历史事件应跳过不抛() {
        Fixture f = new Fixture("s-recall-4");
        f.store.appendEvent("s-recall-4", AgentEvent.TOOL_RESULT, "旧格式纯文本结果");
        f.emitResult("call-1", "新格式结果");
        Assert.assertEquals("新格式结果", f.ctx.loopListener().recallToolResult("call-1"));
        Assert.assertNull(f.ctx.loopListener().recallToolResult("call-0"));
    }

    /** output 为 null 的 Map payload：返回 null 不抛 NPE */
    @Test
    public void output为null时应返回null不抛NPE() {
        Fixture f = new Fixture("s-recall-5");
        java.util.Map<String, Object> payload = new java.util.LinkedHashMap<>();
        payload.put("tool_call_id", "call-1");
        payload.put("tool_name", "bigReport");
        payload.put("output", null);
        f.ctx.emit(AgentEvent.TOOL_RESULT, payload);
        Assert.assertNull(f.ctx.loopListener().recallToolResult("call-1"));
    }

    /** I5 判据：会话链 true；AgentLoopListener.NONE（轻链路）false */
    @Test
    public void 会话链supports应为true且NONE默认false() {
        Assert.assertTrue(new Fixture("s-recall-6").ctx.loopListener().supportsToolResultRecall());
        Assert.assertFalse(AgentLoopListener.NONE.supportsToolResultRecall());
        Assert.assertNull(AgentLoopListener.NONE.recallToolResult("any"));
    }

    /** I13：回捞只读——recall 前后事件流大小与末条 seq 完全不变（绝不 appendEvent） */
    @Test
    public void 回捞不得新增任何事件() {
        Fixture f = new Fixture("s-recall-7");
        f.emitResult("call-1", "全文结果");
        int sizeBefore = f.store.events("s-recall-7", 0).size();
        long lastSeqBefore = f.store.events("s-recall-7", 0).get(sizeBefore - 1).getSeq();

        Assert.assertEquals("全文结果", f.ctx.loopListener().recallToolResult("call-1"));
        f.ctx.loopListener().recallToolResult("call-miss");

        List<AgentEvent> after = f.store.events("s-recall-7", 0);
        Assert.assertEquals("回捞是只读操作,事件流不得增长", sizeBefore, after.size());
        Assert.assertEquals(lastSeqBefore, after.get(after.size() - 1).getSeq());
    }

    /** 会话隔离：同一 store 两个 session 各含同名 tool_call_id="call-1" 不同 output，
     *  A 的回捞只得 A 全文、B 侧独有能力，跨会话 id 在 A 返回 null（扫描域按 sessionId 硬隔离） */
    @Test
    public void 同名句柄跨会话应隔离不串味() {
        AgentSessionState stateA = new AgentSessionState();
        stateA.setSessionId("sA");
        stateA.setAgentKey("probe");
        stateA.setStatus(AgentSessionState.RUNNING);
        AgentSessionState stateB = new AgentSessionState();
        stateB.setSessionId("sB");
        stateB.setAgentKey("probe");
        stateB.setStatus(AgentSessionState.RUNNING);
        InMemoryAgentSessionStore shared = new InMemoryAgentSessionStore();
        shared.saveState(stateA);
        shared.saveState(stateB);
        AgentSessionContext ctxA = new AgentSessionContext(stateA, shared, new HashMap<>());
        AgentSessionContext ctxB = new AgentSessionContext(stateB, shared, new HashMap<>());

        ctxA.loopListener().onToolResult("call-1", "bigReport", "A 的全文-唯 A 可见");
        ctxB.loopListener().onToolResult("call-1", "bigReport", "B 的全文-唯 B 可见");

        Assert.assertEquals("A 只得 A 的全文", "A 的全文-唯 A 可见",
                ctxA.loopListener().recallToolResult("call-1"));
        Assert.assertEquals("B 只得 B 的全文", "B 的全文-唯 B 可见",
                ctxB.loopListener().recallToolResult("call-1"));

        // 仅在 B 存在的句柄，A 侧扫描不到（跨会话不串味）
        ctxB.loopListener().onToolResult("call-only-b", "bigReport", "B 独有");
        Assert.assertNull("B 独有的 id 在 A 侧应返回 null",
                ctxA.loopListener().recallToolResult("call-only-b"));
        Assert.assertEquals("B 独有", ctxB.loopListener().recallToolResult("call-only-b"));
    }

    private static String repeat(String s, int n) {
        StringBuilder sb = new StringBuilder(s.length() * n);
        for (int i = 0; i < n; i++) {
            sb.append(s);
        }
        return sb.toString();
    }
}
