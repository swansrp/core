package com.bidr.llm.agent;

import com.bidr.llm.agent.session.AgentEvent;
import com.bidr.llm.agent.session.AgentSessionContext;
import com.bidr.llm.agent.session.AgentSessionState;
import com.bidr.llm.agent.session.InMemoryAgentSessionStore;
import org.junit.Assert;
import org.junit.Test;

import java.util.HashMap;
import java.util.List;

/**
 * Title: NextStepToolsTest
 * Description: 下一步候选工具离线单测（随类自墨枢 writer 下沉迁回，JUnit4 重写）：
 * 候选必须<b>原样</b>进事件流（用户点哪条就把那句原文发回模型，服务端不得改写），
 * 条数与字数在服务端收口，坏 JSON/空数组一律拒绝且不落事件——前端宁缺不编。
 *
 * @author sharp
 * @since 2026/10/18
 */
public class NextStepToolsTest {

    private final AgentSessionState state = new AgentSessionState();

    private final InMemoryAgentSessionStore store = new InMemoryAgentSessionStore();

    private final AgentSessionContext ctx;

    public NextStepToolsTest() {
        state.setSessionId("s1");
        state.setAgentKey("probe");
        state.setStatus(AgentSessionState.RUNNING);
        store.saveState(state);
        ctx = new AgentSessionContext(state, store, new HashMap<>());
    }

    @SuppressWarnings("unchecked")
    private List<String> emittedSteps() {
        List<AgentEvent> events = store.events("s1", 0);
        for (AgentEvent event : events) {
            if (NextStepTools.EVENT_NEXT_STEPS.equals(event.getType())) {
                return (List<String>) event.getPayload();
            }
        }
        return null;
    }

    @Test
    public void 候选原样进事件流不改写() {
        String reply = new NextStepTools(ctx).suggestNextSteps(
                "[\"把这份模板落成一份草稿文档\",\"按监理日志口径调整栏目\"]");
        Assert.assertTrue(reply.startsWith("已把 2 条"));
        List<String> steps = emittedSteps();
        Assert.assertNotNull("必须落一条 next_steps 事件", steps);
        Assert.assertEquals("候选须逐字原样（用户点哪条就发回那句原文）",
                java.util.Arrays.asList("把这份模板落成一份草稿文档", "按监理日志口径调整栏目"), steps);
    }

    @Test
    public void 条数上限三条收口() {
        new NextStepTools(ctx).suggestNextSteps(
                "[\"一\",\"二\",\"三\",\"四\",\"五\"]");
        List<String> steps = emittedSteps();
        Assert.assertEquals(java.util.Arrays.asList("一", "二", "三"), steps);
    }

    @Test
    public void 单条超长按四十字截断() {
        StringBuilder longItem = new StringBuilder();
        for (int i = 0; i < 50; i++) {
            longItem.append('字');
        }
        new NextStepTools(ctx).suggestNextSteps("[\"" + longItem + "\"]");
        List<String> steps = emittedSteps();
        Assert.assertEquals(NextStepTools.MAX_LEN, steps.get(0).length());
    }

    @Test
    public void 坏JSON拒绝且不落事件() {
        String reply = new NextStepTools(ctx).suggestNextSteps("不是 JSON");
        Assert.assertTrue(reply.startsWith("拒绝："));
        Assert.assertNull("拒绝时不得落事件（前端宁缺不编）", emittedSteps());
    }

    @Test
    public void 空数组拒绝且不落事件() {
        String reply = new NextStepTools(ctx).suggestNextSteps("[]");
        Assert.assertTrue(reply.startsWith("拒绝："));
        Assert.assertNull(emittedSteps());
    }
}
