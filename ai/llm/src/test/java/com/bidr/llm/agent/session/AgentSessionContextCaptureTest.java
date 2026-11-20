package com.bidr.llm.agent.session;

import com.bidr.llm.agent.AutonomousAgentDefinition;
import com.bidr.llm.agent.DetachPolicy;
import com.bidr.llm.flow.StaticObjectProvider;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.Test;
import org.springframework.beans.factory.NoUniqueBeanDefinitionException;
import org.springframework.beans.factory.ObjectProvider;

import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/**
 * Title: AgentSessionContextCaptureTest
 * Description: 会话宿主上下文采集协议钉桩（plain JUnit4，不起 Spring 容器）：
 * session/start 在<b>请求线程</b>经 {@link SessionContextContributor} 抓宿主上下文（项目/工号等
 * ThreadLocal 归属），写入 {@link AgentSessionState#getContext()} 随快照持久化，run 线程上的工具
 * 经 {@link AgentSessionContext#getSessionContext()} 读取。本测试按契约逐条钉死：
 * <ul>
 *     <li><b>正常采集</b>：贡献者 map 落进快照，存储可读，会话上下文可读；</li>
 *     <li><b>贡献者异常</b>：start 照常成功（run 线程照常起跑），上下文为空，不外溢异常；</li>
 *     <li><b>贡献者返回空</b>：等同于无上下文；</li>
 *     <li><b>多实现</b>：容器内多个贡献者不阻断起会话（按「无唯一贡献者」跳过）；</li>
 *     <li><b>无贡献者</b>：五参构造器（既有装配/测试口径）上下文为空，行为与改前一致；</li>
 *     <li><b>旧快照兼容</b>：{@code context=null} 时 {@code getSessionContext()} 返回空 map（非 null）；</li>
 *     <li><b>持久化兼容</b>：Jackson 序列化/反序列化往返后 context 内容保留；</li>
 *     <li><b>startPaused→resume</b>：暂停窗口内建会话采到的上下文，起跑收口后原值仍在。</li>
 * </ul>
 * 说明：本模块 surefire 走 JUnit4 provider（见 AgentSessionStartPausedTest 同款说明），
 * 写成 JUnit5 会被静默跳过（Tests run: 0）。
 *
 * @author Sharp
 * @since 2026/11/20
 */
public class AgentSessionContextCaptureTest {

    private static final String AGENT_KEY = "probe";

    private final InMemoryAgentSessionStore store = new InMemoryAgentSessionStore();

    /** 立即完成的探针执行体：start 到来即计数并返回（run 线程随即走终态收口） */
    private final ProbeDefinition probe = new ProbeDefinition();

    /** 正常采集：贡献者返回的键值随 start 落进会话快照，存储与 AgentSessionContext 两路可读 */
    @Test
    public void contributorMapLandsInSnapshotAndSessionContext() throws Exception {
        AgentSessionService service = service(AgentSessionContextCaptureTest::hostContext);

        AgentSessionState state = service.start(AGENT_KEY, payload(), "10086");

        Map<String, Object> context = state.getContext();
        assertNotNull("贡献者返回值应随 start 落进快照", context);
        assertEquals("PRJ-001", context.get("projectCode"));
        assertEquals("10086", context.get("user"));

        AgentSessionState stored = store.getState(state.getSessionId());
        assertEquals("快照已落库（存储侧同一份上下文）",
                "PRJ-001", stored.getContext().get("projectCode"));

        // 旧快照口径再顺带钉一次：context 非 null 时原样读出（不重命名、不拍平）
        Map<String, Object> viaContext = new AgentSessionContext(stored, store, payload()).getSessionContext();
        assertEquals("会话上下文可读（工具侧入口）", "10086", viaContext.get("user"));
        assertEquals("嵌套结构原样保留", "60", ((Map<?, ?>) viaContext.get("space")).get("id"));

        assertTrue("起会话不受影响（run 线程照常起跑）", probe.started.await(3, TimeUnit.SECONDS));
    }

    /** 贡献者抛异常：只记 warn，start 照常成功、上下文为空、异常不外溢 */
    @Test
    public void contributorFailureDoesNotBlockStart() throws Exception {
        AgentSessionService service = service(() -> {
            throw new IllegalStateException("请求线程 ThreadLocal 已清理");
        });

        // 不得抛：宿主上下文是增强信息，采集失败不能把起会话判死
        AgentSessionState state = service.start(AGENT_KEY, payload(), "10086");

        assertNotNull("start 必须成功返回快照", state);
        assertTrue("上下文按空处理", store.getState(state.getSessionId()).getContext().isEmpty());
        assertTrue("run 线程照常起跑", probe.started.await(3, TimeUnit.SECONDS));
    }

    /** 贡献者返回空 map / null：均等同无上下文，快照落空 map（非 null，便于调用方不判空） */
    @Test
    public void emptyContributionMeansNoContext() {
        AgentSessionService emptyMapService = service(Collections::emptyMap);
        AgentSessionState byEmptyMap = emptyMapService.start(AGENT_KEY, payload(), "10086");
        assertNotNull(byEmptyMap.getContext());
        assertTrue(byEmptyMap.getContext().isEmpty());

        AgentSessionService nullService = service(() -> null);
        AgentSessionState byNull = nullService.start(AGENT_KEY, payload(), "10086");
        assertNotNull(byNull.getContext());
        assertTrue(byNull.getContext().isEmpty());
    }

    /** 容器内多个实现：getIfUnique 抛 NoUniqueBeanDefinitionException，不得阻断起会话 */
    @Test
    public void duplicateContributorsDoNotBlockStart() throws Exception {
        AgentSessionService service = new AgentSessionService(store, Collections.singletonList(probe),
                new StaticObjectProvider<>(null), new StaticObjectProvider<>(null),
                new StaticObjectProvider<>(null), new DuplicateContributorProvider());

        AgentSessionState state = service.start(AGENT_KEY, payload(), "10086");

        assertNotNull("多实现只是「无唯一贡献者」，不阻断起会话", state);
        assertTrue(store.getState(state.getSessionId()).getContext().isEmpty());
        assertTrue("run 线程照常起跑", probe.started.await(3, TimeUnit.SECONDS));
    }

    /** 五参构造器（既有装配点）：无贡献者 ⇒ 上下文为空，行为与改前一致 */
    @Test
    public void fiveArgConstructorLeavesContextEmpty() {
        AgentSessionService service = new AgentSessionService(store, Collections.singletonList(probe),
                new StaticObjectProvider<>(null), new StaticObjectProvider<>(null),
                new StaticObjectProvider<>(null));

        AgentSessionState state = service.start(AGENT_KEY, payload(), "10086");

        assertNotNull("无贡献者时仍落空 map（不是 null）", state.getContext());
        assertTrue(state.getContext().isEmpty());
    }

    /** 旧快照（context 字段上线前写入，反序列化为 null）：getSessionContext 返回空 map，不 NPE */
    @Test
    public void legacySnapshotWithoutContextYieldsEmptyMap() {
        AgentSessionState legacy = new AgentSessionState();
        legacy.setSessionId("legacy-2026-11-19");
        legacy.setContext(null);

        AgentSessionContext ctx = new AgentSessionContext(legacy, store, Collections.emptyMap());

        Map<String, Object> context = ctx.getSessionContext();
        assertNotNull("旧快照读上下文不得 NPE", context);
        assertTrue(context.isEmpty());
        assertNull("状态字段本身仍为 null（未就地改写成空 map）", legacy.getContext());
    }

    /** 持久化兼容：Jackson 往返后 context 内容保留（Redis 快照即此路径） */
    @Test
    public void contextSurvivesJacksonRoundTrip() throws Exception {
        AgentSessionService service = service(AgentSessionContextCaptureTest::hostContext);
        AgentSessionState state = service.start(AGENT_KEY, payload(), "10086");

        // 与 RedisAgentSessionStore 同口径：容器注入的 Spring Boot ObjectMapper
        //（默认 FAIL_ON_UNKNOWN_PROPERTIES=false）。实测裸 new ObjectMapper() 反序列化
        // AgentSessionState 会因 isTerminal() 派生的只读属性 "terminal" 报
        // UnrecognizedPropertyException（既有形态，与本次新增字段无关）；未知字段清单里
        // 已含 "context"，即新字段对 Jackson 可见
        ObjectMapper mapper = new ObjectMapper()
                .configure(com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

        String json = mapper.writeValueAsString(state);
        AgentSessionState restored = mapper.readValue(json, AgentSessionState.class);

        assertNotNull("反序列化后上下文不丢", restored.getContext());
        assertEquals("PRJ-001", restored.getContext().get("projectCode"));
        assertEquals("10086", restored.getContext().get("user"));
        assertEquals("嵌套结构往返保真", "60",
                ((Map<?, ?>) restored.getContext().get("space")).get("id"));
    }

    /** startPaused 建会话 → resume 起跑：请求线程采到的宿主上下文在暂停窗口与终态后都保留 */
    @Test
    public void startPausedResumeKeepsCapturedContext() throws Exception {
        AgentSessionService service = service(AgentSessionContextCaptureTest::hostContext);

        AgentSessionState state = service.start(AGENT_KEY, payload(), "10086",
                DetachPolicy.KEEP_RUNNING, true);
        String sessionId = state.getSessionId();

        assertEquals("建后未起跑：快照为 PAUSED", AgentSessionState.PAUSED,
                store.getState(sessionId).getStatus());
        Map<String, Object> pausedContext = store.getState(sessionId).getContext();
        assertNotNull("startPaused 快照也须带宿主上下文", pausedContext);
        assertEquals("PRJ-001", pausedContext.get("projectCode"));
        assertEquals("10086", pausedContext.get("user"));

        service.resume(sessionId, null);
        assertTrue("resume 后 run 线程起跑", probe.started.await(3, TimeUnit.SECONDS));
        waitStatus(sessionId, AgentSessionState.FINISHED);

        Map<String, Object> resumedContext = store.getState(sessionId).getContext();
        assertNotNull("resume 收口后上下文不得被清空", resumedContext);
        assertEquals("PRJ-001", resumedContext.get("projectCode"));
        assertEquals("10086", resumedContext.get("user"));
        assertEquals("嵌套结构原值仍在", "60", ((Map<?, ?>) resumedContext.get("space")).get("id"));
    }

    /** 轮询等状态落定（run 线程收口异步） */
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

    /** 模拟宿主上下文：项目/工号 + 一层嵌套（真实贡献者形态） */
    private static Map<String, Object> hostContext() {
        Map<String, Object> context = new LinkedHashMap<>();
        context.put("projectId", "1024");
        context.put("projectCode", "PRJ-001");
        context.put("projectName", "城东污水厂");
        context.put("user", "10086");
        Map<String, Object> space = new LinkedHashMap<>();
        space.put("id", "60");
        context.put("space", space);
        return context;
    }

    private AgentSessionService service(SessionContextContributor contributor) {
        return new AgentSessionService(store, Collections.singletonList(probe),
                new StaticObjectProvider<>(null), new StaticObjectProvider<>(null),
                new StaticObjectProvider<>(null), new StaticObjectProvider<>(contributor));
    }

    private static Map<String, Object> payload() {
        Map<String, Object> payload = new HashMap<>();
        payload.put("instruction", "整理项目知识库里的会议纪要");
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

    /** 多实现场景的 provider：getIfUnique 按 Spring 语义抛 NoUniqueBeanDefinitionException */
    private static class DuplicateContributorProvider implements ObjectProvider<SessionContextContributor> {

        @Override
        public SessionContextContributor getObject() {
            throw new NoUniqueBeanDefinitionException(SessionContextContributor.class, 2,
                    "expected single matching bean but found 2");
        }

        @Override
        public SessionContextContributor getObject(Object... args) {
            return getObject();
        }

        @Override
        public SessionContextContributor getIfAvailable() {
            throw new NoUniqueBeanDefinitionException(SessionContextContributor.class, 2,
                    "expected single matching bean but found 2");
        }

        @Override
        public SessionContextContributor getIfUnique() {
            throw new NoUniqueBeanDefinitionException(SessionContextContributor.class, 2,
                    "expected single matching bean but found 2");
        }
    }
}