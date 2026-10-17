package com.bidr.llm.usage;

import com.bidr.llm.agent.AbstractToolLoopAgent;
import com.bidr.llm.agent.AgentLoopOptions;
import com.bidr.llm.agent.session.AgentSessionContext;
import com.bidr.llm.agent.session.AgentSessionState;
import com.bidr.llm.agent.session.InMemoryAgentSessionStore;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.output.Response;
import org.junit.After;
import org.junit.Assert;
import org.junit.Test;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Title: ModelCallIdentityTest
 * Description: 会话身份怎么走到用量事实里（M14 棒5 计量行"替谁跑的"那一半）。钉四件事：
 * ① 持有器只在 set 之后有值，{@code set(null)}／{@code set(空)} 等同清除；
 * ② 身份三栏取自会话状态（不是登录线程现读）；
 * ③ 🔴 <b>跨线程那一跳带得过去</b>——实际 HTTP 调用跑在 {@code llm-call-interruptible} 子线程，
 * ThreadLocal 不会自己跟过去，必须由 runner 显式快照再 set；这条不钉住，计量行就永远没有会话归属；
 * ④ set/clear 成对：循环收口与抛错两条路径都必须清干净，线程池复用线程时漏 clear
 * 等于把上一场的身份写给下一场。
 *
 * @author Sharp
 * @since 2026-10-13
 */
public class ModelCallIdentityTest {

    @After
    public void clearHolder() {
        ModelCallContexts.clear();
    }

    // ---------------- 持有器语义 ----------------

    @Test
    public void 未设置时读到null() {
        Assert.assertNull("读不到就是没有，调用方按\"有则填\"留空", ModelCallContexts.current());
    }

    @Test
    public void set后同线程读得到() {
        ModelCallContext identity = new ModelCallContext("s-1", "writer", "bidrAdmin");
        ModelCallContexts.set(identity);
        Assert.assertSame(identity, ModelCallContexts.current());
    }

    /** 空对象不该盖掉真实值：三栏全空的上下文等同没有 */
    @Test
    public void 空上下文与null都等同清除() {
        ModelCallContexts.set(new ModelCallContext("s-1", "writer", "bidrAdmin"));
        ModelCallContexts.set(new ModelCallContext(null, null, null));
        Assert.assertNull("三栏全空要落回 null，别让空对象冒充一份身份", ModelCallContexts.current());

        ModelCallContexts.set(new ModelCallContext("s-2", "writer", "bidrAdmin"));
        ModelCallContexts.set(null);
        Assert.assertNull(ModelCallContexts.current());
    }

    // ---------------- 身份取自会话状态 ----------------

    @Test
    public void 会话上下文的身份三栏来自状态() {
        AgentSessionContext ctx = sessionContext("s-9", "inkhub-check", "zhangsan");
        ModelCallContext identity = ctx.usageIdentity();

        Assert.assertEquals("s-9", identity.getSessionId());
        Assert.assertEquals("inkhub-check", identity.getAgentKey());
        Assert.assertEquals("zhangsan", identity.getAskedBy());
        Assert.assertFalse(identity.isEmpty());
    }

    /** 会话状态没带操作人（历史行、系统触发的会话）时不编占位值 */
    @Test
    public void 会话状态缺操作人则该栏为空() {
        AgentSessionContext ctx = sessionContext("s-10", "writer", null);
        Assert.assertNull(ctx.usageIdentity().getAskedBy());
    }

    // ---------------- 跨线程那一跳 ----------------

    /**
     * 🔴 本棒存在理由：模型实际调用在池线程收口，读不到发起线程的上下文。
     * 这里让假模型在<b>被调用的那个线程</b>上现读身份，读到即证明快照带过去了。
     */
    @Test
    public void 子线程的模型调用读得到会话身份() throws Exception {
        AtomicReference<ModelCallContext> seen = new AtomicReference<>();
        AtomicReference<String> seenThread = new AtomicReference<>();
        RecordingModel model = new RecordingModel(seen, seenThread, false);

        ProbeAgent agent = new ProbeAgent(model);
        agent.start(sessionContext("s-11", "inkhub-check", "zhangsan"), new HashMap<String, Object>());

        Assert.assertNotNull("子线程必须读得到身份，否则计量行永远没有会话归属", seen.get());
        Assert.assertEquals("s-11", seen.get().getSessionId());
        Assert.assertEquals("inkhub-check", seen.get().getAgentKey());
        Assert.assertEquals("zhangsan", seen.get().getAskedBy());
        Assert.assertNotEquals("身份是靠显式快照跨线程传的，读的确实是池线程：" + seenThread.get(),
                Thread.currentThread().getName(), seenThread.get());
    }

    /**
     * 会话状态只认得出部分身份（有 sessionId、没 agentKey／operator，如历史行补不全）：
     * 读到什么填什么，缺的栏留 NULL，不拿 "unknown" 之类的占位串冒充。
     * 三栏全空的极端形态在 {@code ModelUsageReporterTest#无身份时三栏留null} 里钉
     *（那种形态压根没有会话上下文，构造不出可保存的会话状态）。
     */
    @Test
    public void 部分身份只填得出的那一栏() throws Exception {
        AtomicReference<ModelCallContext> seen = new AtomicReference<>();

        new ProbeAgent(new RecordingModel(seen, new AtomicReference<String>(), false))
                .start(sessionContext("s-partial", null, null), new HashMap<String, Object>());

        Assert.assertNotNull(seen.get());
        Assert.assertEquals("s-partial", seen.get().getSessionId());
        Assert.assertNull("读不出的栏留 NULL，不编占位值", seen.get().getAgentKey());
        Assert.assertNull(seen.get().getAskedBy());
    }

    // ---------------- set/clear 成对 ----------------

    @Test
    public void 循环收口后线程上不留身份() throws Exception {
        new ProbeAgent(new RecordingModel(new AtomicReference<ModelCallContext>(),
                new AtomicReference<String>(), false))
                .start(sessionContext("s-12", "writer", "zhangsan"), new HashMap<String, Object>());

        Assert.assertNull("漏 clear＝下一场复用同一线程的会话被写上别人的身份", ModelCallContexts.current());
    }

    /** 异常路径：模型调用抛错（发生在 set 之后、try 之内），finally 照样清 */
    @Test
    public void 抛错路径也清身份() {
        ProbeAgent agent = new ProbeAgent(new RecordingModel(new AtomicReference<ModelCallContext>(),
                new AtomicReference<String>(), true));
        try {
            agent.start(sessionContext("s-13", "writer", "zhangsan"), new HashMap<String, Object>());
            Assert.fail("模型抛错应向会话层冒");
        } catch (Exception expected) {
            Assert.assertTrue("冒上来的应是模型抛的那条: " + expected,
                    expected instanceof IllegalStateException);
        }
        Assert.assertNull("异常路径漏清同样是把身份写给下一场", ModelCallContexts.current());
    }

    // ---------------- 脚手架 ----------------

    private static AgentSessionContext sessionContext(String sessionId, String agentKey, String operator) {
        AgentSessionState state = new AgentSessionState();
        state.setSessionId(sessionId);
        state.setAgentKey(agentKey);
        state.setOperator(operator);
        state.setStatus(AgentSessionState.RUNNING);
        InMemoryAgentSessionStore store = new InMemoryAgentSessionStore();
        store.saveState(state);
        return new AgentSessionContext(state, store, new HashMap<String, Object>());
    }

    /** 假模型：在"它被调用的线程"上现读身份，然后一次收口成纯文本答案；fail=true 时改为抛错 */
    private static final class RecordingModel implements ChatLanguageModel {

        private final AtomicReference<ModelCallContext> captured;
        private final AtomicReference<String> capturedThread;
        private final boolean fail;

        RecordingModel(AtomicReference<ModelCallContext> captured, AtomicReference<String> capturedThread,
                       boolean fail) {
            this.captured = captured;
            this.capturedThread = capturedThread;
            this.fail = fail;
        }

        @Override
        public Response<AiMessage> generate(List<ChatMessage> messages) {
            return answer();
        }

        @Override
        public Response<AiMessage> generate(List<ChatMessage> messages,
                                            List<ToolSpecification> toolSpecifications) {
            return answer();
        }

        @Override
        public Response<AiMessage> generate(List<ChatMessage> messages, ToolSpecification toolSpecification) {
            return answer();
        }

        private Response<AiMessage> answer() {
            if (fail) {
                throw new IllegalStateException("网关炸了");
            }
            if (captured.get() == null) {
                captured.set(ModelCallContexts.current());
                capturedThread.set(Thread.currentThread().getName());
            }
            return Response.from(AiMessage.from("结论：已完成"));
        }
    }

    /** 最小工具循环 agent：四要素给最省事的值，只为止住 start 的身份接线 */
    private static final class ProbeAgent extends AbstractToolLoopAgent<String> {

        private final ChatLanguageModel model;

        ProbeAgent(ChatLanguageModel model) {
            this.model = model;
        }

        @Override
        public String agentKey() {
            return "probe";
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
        protected dev.langchain4j.model.chat.ChatLanguageModel model(String state, AgentSessionContext ctx,
                                                                     java.util.Map<String, Object> payload) {
            return model;
        }

        @Override
        protected String systemPrompt(String state, AgentSessionContext ctx,
                                      java.util.Map<String, Object> payload) {
            return "你是探针。";
        }

        @Override
        protected String userPrompt(String state, AgentSessionContext ctx,
                                    java.util.Map<String, Object> payload) {
            return "给个结论。";
        }

        @Override
        protected List<Object> tools(String state, AgentSessionContext ctx,
                                     java.util.Map<String, Object> payload) {
            return Collections.emptyList();
        }

        @Override
        protected AgentLoopOptions loopOptions() {
            return new AgentLoopOptions(2, 5);
        }
    }
}
