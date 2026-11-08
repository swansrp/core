package com.bidr.feishu.notify;

import com.bidr.notification.NotifyMessage;
import com.bidr.platform.service.cache.SysConfigCacheService;
import com.bidr.feishu.constant.param.FeishuParam;
import com.bidr.feishu.service.message.FeishuSendMessageService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Title: FeishuNotifyChannelTest
 * Description: 飞书通知渠道离线单测（T1.1 棒5）——enabled() 开关口径（"true"/"1" 开）与
 * send() 渠道闸（最低级别闸/接收人空跳过/卡片投递透传），纯 Mockito 不碰 sys_config 存储与
 * 飞书 API。
 * <p>🔴 JUnit 5（jupiter）形态：本模块 surefire＝junit-platform provider 且 classpath 无 vintage
 * 引擎，JUnit 4 风格的测试类会被静默跳过（pack-bidr 棒4 实证教训）。</p>
 * Copyright: Copyright (c) 2026 Company: Bidr Ltd.
 *
 * @author Sharp
 * @since 2026-11-01
 */
public class FeishuNotifyChannelTest {

    private SysConfigCacheService sysConfigCacheService;
    private FeishuSendMessageService feishuSendMessageService;
    private FeishuNotifyChannel channel;

    @BeforeEach
    public void setUp() {
        sysConfigCacheService = Mockito.mock(SysConfigCacheService.class);
        feishuSendMessageService = Mockito.mock(FeishuSendMessageService.class);
        channel = new FeishuNotifyChannel(sysConfigCacheService, feishuSendMessageService);
    }

    private void stubConfig(String enabled, String minLevel, String receivers, String subject) {
        when(sysConfigCacheService.getSysConfigValue(FeishuParam.EXCEPTION_NOTIFY_FEISHU_ENABLED))
                .thenReturn(enabled);
        when(sysConfigCacheService.getSysConfigValue(FeishuParam.EXCEPTION_NOTIFY_FEISHU_MIN_LEVEL))
                .thenReturn(minLevel);
        when(sysConfigCacheService.getSysConfigValue(FeishuParam.EXCEPTION_NOTIFY_FEISHU))
                .thenReturn(receivers);
        when(sysConfigCacheService.getSysConfigValue(FeishuParam.EXCEPTION_NOTIFY_FEISHU_SUBJECT))
                .thenReturn(subject);
    }

    /** enabled()：sys_config 开关口径与原监听器一致（"true"/"1" 开，其余含 "0"/null 关） */
    @Test
    public void enabledReflectsSysConfigSwitch() {
        stubConfig("true", null, null, null);
        assertTrue(channel.enabled());
        stubConfig("1", null, null, null);
        assertTrue(channel.enabled());
        stubConfig("0", null, null, null);
        assertFalse(channel.enabled());
        stubConfig(null, null, null, null);
        assertFalse(channel.enabled());
    }

    /** channelId：core SPI 契约渠道标识 */
    @Test
    public void channelIdIsFeishu() {
        org.junit.jupiter.api.Assertions.assertEquals("feishu", channel.channelId());
    }

    /** 渠道闸①：级别未达配置最低级别（缺省 ERROR）→ 跳过投递 */
    @Test
    public void sendSkipsWhenLevelBelowFloor() {
        stubConfig("true", null, "ou_1", "【系统异常通知】");
        NotifyMessage message = new NotifyMessage();
        message.content = "内容";
        message.level = NotifyMessage.Level.WARN;

        channel.send(message);

        verify(feishuSendMessageService, never()).sendCardMessage(anyList(), anyString(), anyString());
    }

    /** 渠道闸①补充：FATAL→ERROR 达档、INFO 低于缺省档；显式配置 WARN 档时 WARN 放行 */
    @Test
    public void sendAppliesConfiguredMinLevel() {
        stubConfig("true", "WARN", "ou_1", null);
        NotifyMessage warn = new NotifyMessage();
        warn.title = "标题";
        warn.content = "warn 内容";
        warn.level = NotifyMessage.Level.WARN;
        channel.send(warn);
        verify(feishuSendMessageService).sendCardMessage(anyList(), anyString(), eq("warn 内容"));

        Mockito.reset(feishuSendMessageService);
        stubConfig("true", "WARN", "ou_1", null);
        NotifyMessage fatal = new NotifyMessage();
        fatal.title = "标题";
        fatal.content = "fatal 内容";
        fatal.level = NotifyMessage.Level.ERROR;
        channel.send(fatal);
        verify(feishuSendMessageService).sendCardMessage(anyList(), anyString(), eq("fatal 内容"));
    }

    /** 渠道闸②③：接收人解析（中英文逗号分隔）＋标题/正文投递（标题取渠道配置） */
    @Test
    public void sendDeliversCardToConfiguredReceivers() {
        stubConfig("true", null, "ou_1，ou_2 , ou_3", "【系统异常通知】");
        NotifyMessage message = new NotifyMessage();
        message.title = "内核标题";
        message.content = "**错误码**: 500";
        message.level = NotifyMessage.Level.ERROR;

        channel.send(message);

        List<String> expectedReceivers = Arrays.asList("ou_1", "ou_2", "ou_3");
        verify(feishuSendMessageService).sendCardMessage(eq(expectedReceivers),
                eq("【系统异常通知】"), eq("**错误码**: 500"));
    }

    /** 渠道闸②：接收人未配置＝渠道广播语义自定（参考实现语义：log.warn 跳过不发送、不广播） */
    @Test
    public void sendSkipsWhenReceiversUnconfigured() {
        stubConfig("true", null, "", "【系统异常通知】");
        NotifyMessage message = new NotifyMessage();
        message.content = "内容";
        message.level = NotifyMessage.Level.ERROR;

        channel.send(message);

        verify(feishuSendMessageService, never()).sendCardMessage(anyList(), anyString(), anyString());
    }

    /** 幂等面：send 不改写传入消息（纯投递语义） */
    @Test
    public void sendDoesNotMutateMessage() {
        stubConfig("true", null, "ou_1", null);
        NotifyMessage message = new NotifyMessage();
        message.title = "标题";
        message.content = "内容";
        message.level = NotifyMessage.Level.ERROR;
        message.receivers = new ArrayList<>();

        channel.send(message);

        assertTrue(message.receivers.isEmpty());
        verify(feishuSendMessageService).sendCardMessage(anyList(), anyString(), anyString());
    }
}
