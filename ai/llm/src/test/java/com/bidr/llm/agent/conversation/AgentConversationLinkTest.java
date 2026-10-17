package com.bidr.llm.agent.conversation;

import org.junit.Assert;
import org.junit.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.ObjectProvider;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Title: AgentConversationLinkTest
 * Description: 会话↔对话定位广播契约测试（M14 棒2 增量）。这条腿补的是"对话会过期、定位不该过期"：
 * 框架把 sessionId→conversationId 广播给永久台账（装配单），本身不存任何东西。
 * 钉三件事：① 缺任一边的定位不广播（否则台账写出半截行）；② 多个台账都要收到；
 * ③ 台账抛错绝不外溢进对话写入——对话是辅助链路，写不定位最多页面少一个入口。
 * 全程 mock，不连 Redis。
 *
 * @author Sharp
 * @since 2026-10-12
 */
public class AgentConversationLinkTest {

    /** 收集广播的台账实现 */
    private static final class RecordingSink implements AgentConversationLinkSink {

        private final List<String> received = new ArrayList<>();

        private final boolean fail;

        RecordingSink(boolean fail) {
            this.fail = fail;
        }

        @Override
        public void onConversationLinked(String sessionId, String conversationId) {
            if (fail) {
                throw new IllegalStateException("台账写库炸了");
            }
            received.add(sessionId + "=" + conversationId);
        }
    }

    @SuppressWarnings("unchecked")
    private static AgentConversationService service(AgentConversationLinkSink... sinks) {
        ObjectProvider<AgentConversationLinkSink> provider = Mockito.mock(ObjectProvider.class);
        Mockito.when(provider.iterator()).thenReturn(Arrays.asList(sinks).iterator());
        // 定位广播这条腿不碰 Redis、也不读保留期参数，另两个 provider 给空壳即可
        return new AgentConversationService(Mockito.mock(ObjectProvider.class),
                Mockito.mock(ObjectProvider.class), provider);
    }

    @Test
    public void 定位两边齐全才广播() {
        RecordingSink sink = new RecordingSink(false);
        AgentConversationService service = service(sink);

        service.linkSession("  s-1  ", "  c-1  ");
        service.linkSession("", "c-2");
        service.linkSession("s-3", null);
        service.linkSession("s-4", "   ");

        Assert.assertEquals("空 sessionId/空 conversationId 都不 broadcast",
                Arrays.asList("s-1=c-1"), sink.received);
    }

    @Test
    public void 多个台账都收到() {
        RecordingSink first = new RecordingSink(false);
        RecordingSink second = new RecordingSink(false);

        service(first, second).linkSession("s-1", "c-1");

        Assert.assertEquals(Arrays.asList("s-1=c-1"), first.received);
        Assert.assertEquals(Arrays.asList("s-1=c-1"), second.received);
    }

    /** 🔴 同族事故形态：旁证写不进去把主链路带崩。这里台账抛的是运行时异常，广播方必须吞干净 */
    @Test
    public void 台账抛错不外溢且后续台账照收() {
        RecordingSink broken = new RecordingSink(true);
        RecordingSink healthy = new RecordingSink(false);

        service(broken, healthy).linkSession("s-1", "c-1");

        Assert.assertEquals(Arrays.asList("s-1=c-1"), healthy.received);
    }

    @Test
    public void 没有台账实现时静默返回() {
        AgentConversationService service = service();
        service.linkSession("s-1", "c-1");
    }
}
