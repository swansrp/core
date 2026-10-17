package com.bidr.llm.agent;

import com.bidr.llm.agent.session.AgentSessionContext;
import com.bidr.llm.model.LiveModelFactory;
import com.bidr.llm.model.StreamingProgressChatModel;
import dev.langchain4j.model.chat.ChatLanguageModel;
import org.junit.Assert;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

import java.util.function.Consumer;

/**
 * AgentLiveModels 单测：live 状态行/归档行的分流判据与工厂缺席回落。
 * 分流词汇表是发射端（StreamingProgressChatModel.push）的镜像契约——
 * 若发射文案前缀变更而这里不改，live 会静默漏进事件流（实时思考块失效），本测试即守卫针。
 *
 * @author Sharp
 * @since 2026/10/16
 */
public class AgentLiveModelsTest {

    private final LiveModelFactory factory = Mockito.mock(LiveModelFactory.class);
    private final AgentSessionContext ctx = Mockito.mock(AgentSessionContext.class);
    private final ChatLanguageModel fallback = Mockito.mock(ChatLanguageModel.class);

    /** 从 build(...) 参数里捕出 forSession 传入的 live 回调，逐行喂给它验分流 */
    @SuppressWarnings("unchecked")
    private Consumer<String> capturedSink() {
        ArgumentCaptor<Consumer<String>> sink = ArgumentCaptor.forClass(Consumer.class);
        Mockito.verify(factory).build(Mockito.eq("AGENT"), sink.capture(),
                Mockito.any(), Mockito.isNull());
        return sink.getValue();
    }

    @Test
    public void liveStatusLinesGoToPushLive() {
        Mockito.when(factory.build(Mockito.anyString(), Mockito.any(),
                Mockito.any(), Mockito.isNull())).thenReturn(fallback);

        ChatLanguageModel model = AgentLiveModels.forSession(factory, ctx, () -> fallback);

        Assert.assertSame("返回值透传 build 产物", fallback, model);
        capturedSink().accept("思考中·已思 12 字\n部分思考");
        capturedSink().accept("应答中·已收 8 字\n部分应答");
        capturedSink().accept("等待 LLM 流式响应… 已等 3s");
        capturedSink().accept("流式通道不可用，降级同步模型…");
        Mockito.verify(ctx, Mockito.times(4)).pushLive(Mockito.anyString());
        Mockito.verify(ctx, Mockito.never()).log(Mockito.anyString());
    }

    @Test
    public void archiveAndProcessLinesGoToEventLog() {
        Mockito.when(factory.build(Mockito.anyString(), Mockito.any(),
                Mockito.any(), Mockito.isNull())).thenReturn(fallback);

        AgentLiveModels.forSession(factory, ctx, () -> fallback);

        Consumer<String> sink = capturedSink();
        sink.accept("【LLM 思考归档】45 字\n全部思考");
        sink.accept("LLM 第 2 轮请求已发出（携带 6 条上下文消息）");
        Mockito.verify(ctx, Mockito.times(2)).log(Mockito.anyString());
        Mockito.verify(ctx, Mockito.never()).pushLive(Mockito.anyString());
    }

    @Test
    public void nullFactoryFallsBackWithoutTouchingSession() {
        ChatLanguageModel model = AgentLiveModels.forSession(null, ctx, () -> fallback);

        Assert.assertSame("工厂缺席＝原同步模型", fallback, model);
        Mockito.verify(ctx, Mockito.never()).pushLive(Mockito.anyString());
        Mockito.verify(ctx, Mockito.never()).log(Mockito.anyString());
    }

    @Test
    public void liveLinePredicateIsTheRoutingVocabulary() {
        Assert.assertFalse(StreamingProgressChatModel.isLiveStatusLine(null));
        Assert.assertFalse("归档行走事件流，不是 live 状态行",
                StreamingProgressChatModel.isLiveStatusLine("【LLM 思考归档】5 字"));
        Assert.assertFalse(StreamingProgressChatModel.isLiveStatusLine("LLM 第 1 轮请求已发出"));
        Assert.assertTrue(StreamingProgressChatModel.isLiveStatusLine("思考中·已思 1 字"));
        Assert.assertTrue(StreamingProgressChatModel.isLiveStatusLine("应答中·已收 0 字…"));
        Assert.assertTrue(StreamingProgressChatModel.isLiveStatusLine("等待 LLM 流式响应…"));
        Assert.assertTrue(StreamingProgressChatModel.isLiveStatusLine("流式通道不可用，降级同步模型…"));
    }
}
