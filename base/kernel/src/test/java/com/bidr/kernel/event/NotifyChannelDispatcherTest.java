package com.bidr.kernel.event;

import com.bidr.hub.spi.NotifyChannel;
import com.bidr.hub.spi.dto.NotifyMessage;
import com.bidr.kernel.constant.err.ErrCodeLevel;
import com.bidr.kernel.exception.ServiceException;
import org.junit.Assert;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.springframework.beans.factory.ObjectProvider;

import java.util.Arrays;


/**
 * Title: NotifyChannelDispatcherTest
 * Description: 通知渠道派发器离线单测（T1.1 棒5）——事件→NotifyMessage 映射（级别七档收敛三档、
 * TRACE/HIDE 无出口不产出）、渠道遍历（enabled 过滤/单渠道失败隔离/零渠道空安全）、消息组装要素。
 * 纯 Mockito 不起 Spring、不连库。
 * <p>引擎口径：JUnit 4（org.junit）——本模块（core/base/kernel）既有测试（如
 * VoidEndpointNoticeConventionTest）同引擎；与 pack 模块的 jupiter 口径是两回事
 * （surefire provider 由各模块自身依赖决定，kernel 依赖带 JUnit4 provider）。</p>
 * Copyright: Copyright (c) 2026 Company: Bidr Ltd.
 *
 * @author Sharp
 * @since 2026-11-01
 */
public class NotifyChannelDispatcherTest {

    @SuppressWarnings("unchecked")
    private static ObjectProvider<NotifyChannel> providerOf(NotifyChannel... channels) {
        ObjectProvider<NotifyChannel> provider = Mockito.mock(ObjectProvider.class);
        // Stream 只能被消费一次——多次 dispatch 需要每次给新流（thenReturn 单实例会在第二次
        // orderedStream 时抛 "stream has already been operated upon or closed"，实测抓出）
        Mockito.when(provider.orderedStream()).thenAnswer(inv -> Arrays.stream(channels));
        return provider;
    }

    private static NotifyChannel channel(String id, boolean enabled) {
        NotifyChannel channel = Mockito.mock(NotifyChannel.class);
        Mockito.when(channel.channelId()).thenReturn(id);
        Mockito.when(channel.enabled()).thenReturn(enabled);
        return channel;
    }

    private static ExceptionAlertEvent alertEvent(ErrCodeLevel severity) {
        ExceptionAlertEvent.Builder builder = new ExceptionAlertEvent.Builder()
                .source(NotifyChannelDispatcherTest.class)
                .severity(severity)
                .description("打包失败")
                .className("PcService")
                .methodName("zip")
                .throwable(new IllegalStateException("zip stream closed"))
                .stackTrace("line1\nline2\nline3");
        builder.userId(9L);
        builder.userName("zhangsan");
        builder.requestUrl("http://host/pan/zip");
        builder.requestMethod("POST");
        builder.clientIp("10.0.0.9");
        builder.queryString("folderId=3");
        return builder.build();
    }

    /** 遍历投递：enabled 过滤在前；单渠道抛异常不影响下家（渠道间故障隔离） */
    @Test
    public void dispatchesToEnabledChannelsAndIsolatesFailures() {
        NotifyChannel disabled = channel("wx", false);
        NotifyChannel broken = channel("feishu", true);
        Mockito.doThrow(new RuntimeException("feishu down")).when(broken)
                .send(Mockito.any(NotifyMessage.class));
        NotifyChannel healthy = channel("sms", true);

        NotifyChannelDispatcher dispatcher = new NotifyChannelDispatcher(
                providerOf(disabled, broken, healthy));
        dispatcher.onExceptionAlert(alertEvent(ErrCodeLevel.ERROR));

        Mockito.verify(disabled, Mockito.never()).send(Mockito.any(NotifyMessage.class));
        Mockito.verify(broken, Mockito.times(1)).send(Mockito.any(NotifyMessage.class));
        Mockito.verify(healthy, Mockito.times(1)).send(Mockito.any(NotifyMessage.class));
    }

    /** 零渠道空安全：无 NotifyChannel bean（bidr 形态）时静默返回，不抛不告警 */
    @Test
    public void noChannelsMeansNoOp() {
        NotifyChannelDispatcher dispatcher = new NotifyChannelDispatcher(providerOf());
        dispatcher.onExceptionAlert(alertEvent(ErrCodeLevel.ERROR));
        dispatcher.onServiceException(new ServiceExceptionEvent(new ServiceException("打包失败"), null));
        // 无断言异常即绿：SPI 缺席＝零渠道＝零投递
    }

    /** 级别映射：TRACE/HIDE 无出口——源侧不产出消息，渠道侧不会收到 */
    @Test
    public void traceLevelProducesNoDispatch() {
        NotifyChannel channel = channel("feishu", true);
        NotifyChannelDispatcher dispatcher = new NotifyChannelDispatcher(providerOf(channel));

        dispatcher.onExceptionAlert(alertEvent(ErrCodeLevel.TRACE));

        Mockito.verify(channel, Mockito.never()).send(Mockito.any(NotifyMessage.class));
    }

    /** 消息组装与映射：FATAL→ERROR、正文要素齐、标题留空、receivers 留空（渠道自解析） */
    @Test
    public void alertEventMapsToMessageWithGenericAssembly() {
        NotifyChannel channel = channel("feishu", true);
        NotifyChannelDispatcher dispatcher = new NotifyChannelDispatcher(providerOf(channel));

        dispatcher.onExceptionAlert(alertEvent(ErrCodeLevel.FATAL));

        ArgumentCaptor<NotifyMessage> captor = ArgumentCaptor.forClass(NotifyMessage.class);
        Mockito.verify(channel).send(captor.capture());
        NotifyMessage message = captor.getValue();
        Assert.assertEquals(NotifyMessage.Level.ERROR, message.level);
        Assert.assertTrue(message.title == null || message.title.isEmpty());
        Assert.assertTrue(message.receivers == null || message.receivers.isEmpty());
        Assert.assertNotNull(message.content);
        Assert.assertTrue(message.content.contains("**级别**: FATAL"));
        Assert.assertTrue(message.content.contains("**描述**: 打包失败"));
        Assert.assertTrue(message.content.contains("**位置**: PcService#zip"));
        Assert.assertTrue(message.content.contains("**异常类型**: java.lang.IllegalStateException"));
        Assert.assertTrue(message.content.contains("**用户**: zhangsan"));
        Assert.assertTrue(message.content.contains("**请求**: POST http://host/pan/zip"));
        Assert.assertTrue(message.content.contains("**参数**: folderId=3"));
        Assert.assertTrue(message.content.contains("**堆栈**"));
    }

    /** 服务异常腿：错误码行＋堆栈透传（getStackTrace 全串在 50 行内原样） */
    @Test
    public void serviceExceptionEventMapsToMessageWithErrCode() {
        NotifyChannel channel = channel("feishu", true);
        NotifyChannelDispatcher dispatcher = new NotifyChannelDispatcher(providerOf(channel));

        dispatcher.onServiceException(new ServiceExceptionEvent(new ServiceException("打包失败"), null));

        ArgumentCaptor<NotifyMessage> captor = ArgumentCaptor.forClass(NotifyMessage.class);
        Mockito.verify(channel).send(captor.capture());
        NotifyMessage message = captor.getValue();
        Assert.assertNotNull(message.level);
        Assert.assertTrue(message.content.contains("**错误码**"));
        Assert.assertTrue(message.content.contains("**错误信息**"));
        Assert.assertTrue(message.content.contains("**堆栈**"));
    }
}
