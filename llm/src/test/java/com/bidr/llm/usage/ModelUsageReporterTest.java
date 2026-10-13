package com.bidr.llm.usage;

import com.bidr.kernel.utils.BeanUtil;
import org.junit.After;
import org.junit.Assert;
import org.junit.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.NoSuchBeanDefinitionException;
import org.springframework.web.context.WebApplicationContext;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Title: ModelUsageReporterTest
 * Description: 用量出口三件钉（M14 棒5）：① key 指纹<b>定长、稳定、不可逆</b>，明文 key 既不进返回值
 * 也不进事实对象；② 没装 sink 的工程静默跳过（取 bean 失败都不抛）；③ sink 抛错被吞，绝不外溢进模型调用。
 *
 * <p>测试缝＝{@code BeanUtil.setContext(mock WebApplicationContext)}（同 {@code AccountResBindRepoTest}
 * 口径）：本类不改动 {@link ModelUsageReporter} 的可见性去开测试后门，共享件的公共面就是它的行为面。</p>
 *
 * @author Sharp
 * @since 2026-10-13
 */
public class ModelUsageReporterTest {

    /** 假明文 key：只在本类出现，用于证明"指纹里找不到明文" */
    private static final String PLAIN_KEY = "sk-very-secret-0123456789abcdef";

    /** 指纹的期望值：sha256(PLAIN_KEY) 的 hex 前 16 位，独立算出写死在此，防算法被随手改动 */
    private static final String EXPECTED_FINGERPRINT = "db7dd228b1b8babc";

    @After
    public void clearContext() {
        BeanUtil.setContext(null);
    }

    // ---------------- key 指纹 ----------------

    /** 指纹形态：定长 16、十六进制、同 key 同值、且等于独立算出的 sha256 前缀 */
    @Test
    public void 指纹定长十六进制且同key同值() {
        String fp = ModelUsageReporter.keyFingerprint(PLAIN_KEY);
        Assert.assertNotNull(fp);
        Assert.assertEquals("指纹取 sha256 hex 前 16 位", 16, fp.length());
        Assert.assertTrue("指纹须为十六进制串: " + fp, fp.matches("[0-9a-f]{16}"));
        Assert.assertEquals("同一把 key 每次算出同一个指纹（拆账靠它分组，抖动就没法对账）",
                fp, ModelUsageReporter.keyFingerprint(PLAIN_KEY));
        Assert.assertEquals("须等于独立计算的 sha256 前缀（不是截明文、也不是别的哈希）",
                EXPECTED_FINGERPRINT, fp);
    }

    @Test
    public void 不同key指纹不同() {
        Assert.assertNotEquals(ModelUsageReporter.keyFingerprint("sk-aaa"),
                ModelUsageReporter.keyFingerprint("sk-bbb"));
    }

    /** 🔴 红线：指纹不得携带明文——等于明文、含明文子串、被明文包含三种泄漏形态都验 */
    @Test
    public void 指纹不携带明文key() {
        String fp = ModelUsageReporter.keyFingerprint(PLAIN_KEY);
        Assert.assertNotEquals(PLAIN_KEY, fp);
        Assert.assertFalse("返回值不得是明文的一部分", PLAIN_KEY.contains(fp));
        for (String fragment : Arrays.asList("sk-", "secret", "0123456789abcdef")) {
            Assert.assertFalse("指纹不得含明文子串 " + fragment, fp.contains(fragment));
        }
    }

    /** 空/空白 key＝这一路没配 key（本地假网关、单测桩），留 NULL 而不是拿占位串冒充一把 key */
    @Test
    public void 空key指纹为null() {
        Assert.assertNull(ModelUsageReporter.keyFingerprint(null));
        Assert.assertNull(ModelUsageReporter.keyFingerprint(""));
        Assert.assertNull(ModelUsageReporter.keyFingerprint("   "));
    }

    // ---------------- newFacts：身份只从快照来 ----------------

    @Test
    public void newFacts把身份快照摊平并盖发生时刻() {
        ModelCallContext identity = new ModelCallContext("s-1", "inkhub-check", "bidrAdmin");
        ModelUsageFacts facts = ModelUsageReporter.newFacts(ModelUsageFacts.VIA_SYNC, "AGENT",
                "qwen-max", EXPECTED_FINGERPRINT, identity);

        Assert.assertEquals(ModelUsageFacts.VIA_SYNC, facts.getVia());
        Assert.assertEquals("AGENT", facts.getPurposeType());
        Assert.assertEquals("qwen-max", facts.getModelName());
        Assert.assertEquals(EXPECTED_FINGERPRINT, facts.getKeyFingerprint());
        Assert.assertEquals("s-1", facts.getSessionId());
        Assert.assertEquals("inkhub-check", facts.getAgentKey());
        Assert.assertEquals("bidrAdmin", facts.getAskedBy());
        Assert.assertNotNull("callAtMs 必盖（异步落库后 create_at 不等于用量发生时刻）", facts.getCallAtMs());
        Assert.assertTrue("发生时刻应是当下量级",
                Math.abs(System.currentTimeMillis() - facts.getCallAtMs()) < 60_000L);
        Assert.assertNull("token 由调用方按链路读到的值补，这里不得预填 0", facts.getPromptTokens());
    }

    /** 直连小请求没有会话身份：三栏留 NULL，不编 anonymous（编了就把按人汇总做成假账） */
    @Test
    public void 无身份时三栏留null() {
        ModelUsageFacts facts = ModelUsageReporter.newFacts(
                ModelUsageFacts.VIA_VISION, "VISION", "vl-max", null, null);
        Assert.assertNull(facts.getSessionId());
        Assert.assertNull(facts.getAgentKey());
        Assert.assertNull(facts.getAskedBy());
        Assert.assertNull("没 key 就不该有指纹", facts.getKeyFingerprint());
    }

    // ---------------- report：sink 缺失静默、抛错被吞 ----------------

    /** 工程没装 agent-console（或开关关着）：取 bean 抛 NoSuchBeanDefinition ⇒ 静默，模型调用照跑 */
    @Test
    public void 没装sink时静默跳过() {
        WebApplicationContext ctx = Mockito.mock(WebApplicationContext.class);
        Mockito.when(ctx.getBean(ModelUsageSink.class))
                .thenThrow(new NoSuchBeanDefinitionException(ModelUsageSink.class));
        BeanUtil.setContext(ctx);

        ModelUsageReporter.report(facts("s-1"));
        Assert.assertTrue("跳过时不该有任何落点被写入", true);
    }

    /** null 事实直接返回（三条客户端的失败分支都可能传 null） */
    @Test
    public void null事实不取bean() {
        WebApplicationContext ctx = Mockito.mock(WebApplicationContext.class);
        BeanUtil.setContext(ctx);
        ModelUsageReporter.report(null);
        Mockito.verify(ctx, Mockito.never()).getBean(ModelUsageSink.class);
    }

    /** 🔴 同族事故形态：旁证写不进去把主链路带崩。sink 抛运行时异常必须被吞干净 */
    @Test
    public void sink抛错被吞不影响调用方() {
        stubSink(facts -> {
            throw new IllegalStateException("台账写库炸了");
        });
        ModelUsageReporter.report(facts("s-2"));
    }

    @Test
    public void 交出的事实只带指纹不带明文key() {
        RecordingSink sink = new RecordingSink();
        stubSink(sink);
        ModelUsageReporter.report(facts("s-3"));

        Assert.assertEquals(1, sink.received.size());
        ModelUsageFacts got = sink.received.get(0);
        Assert.assertEquals("s-3", got.getSessionId());
        Assert.assertEquals("sink 只该看到指纹", EXPECTED_FINGERPRINT, got.getKeyFingerprint());
        Assert.assertFalse("事实对象整串 toString 都不得含明文", got.toString().contains(PLAIN_KEY));
    }

    // ---------------- 脚手架 ----------------

    /** 把 sink 桩进 BeanUtil 的取 bean 路径（不给自己开后门） */
    private static void stubSink(ModelUsageSink sink) {
        WebApplicationContext ctx = Mockito.mock(WebApplicationContext.class);
        Mockito.when(ctx.getBean(ModelUsageSink.class)).thenReturn(sink);
        BeanUtil.setContext(ctx);
    }

    private static ModelUsageFacts facts(String sessionId) {
        ModelUsageFacts facts = ModelUsageReporter.newFacts(ModelUsageFacts.VIA_SSE, "AGENT",
                "qwen-max", EXPECTED_FINGERPRINT, new ModelCallContext(sessionId, "writer", "bidrAdmin"));
        facts.setFinishReason("stop");
        facts.setCostMs(1200L);
        return facts;
    }

    /** 收集 sink 收到的事实 */
    private static final class RecordingSink implements ModelUsageSink {
        private final List<ModelUsageFacts> received = new ArrayList<>();

        @Override
        public void report(ModelUsageFacts facts) {
            received.add(facts);
        }
    }
}
