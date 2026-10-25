package com.bidr.notification;

import com.bidr.kernel.constant.err.ErrCode;
import com.bidr.kernel.constant.err.ErrCodeLevel;
import com.bidr.kernel.constant.err.ErrCodeSys;
import com.bidr.kernel.constant.err.ErrCodeType;
import com.bidr.kernel.event.ServiceExceptionEvent;
import com.bidr.kernel.exception.ServiceException;
import com.bidr.platform.service.cache.SysConfigCacheService;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;

import javax.servlet.http.HttpServletRequest;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 报错告警腿框架单测（M21 迁 core；JUnit4——该模块 surefire 链静默跳 JUnit5）：
 * severe 非 biz 发布且 draft 逐字段钉值（缺省收件人 000001、dedupeKey 全串）／
 * WARN 不发／biz severe（匿名 ErrCode 覆写 errType=BIZ）不发／config 覆盖收件人＋
 * requestUrl 优先作 summary／config=off 整腿关／config 读异常回缺省照发。
 *
 * <p>JUnit4 断言口径：{@code assertEquals(message, expected, actual)}（消息在前）。</p>
 *
 * @author sharp (M21：报错告警腿迁 core 2026-10-25)
 */
public class ServiceExceptionNotificationListenerTest {

    private NotificationPublisher publisher;
    private SysConfigCacheService configCache;
    private ServiceExceptionNotificationListener listener;

    @Before
    public void setUp() {
        publisher = mock(NotificationPublisher.class);
        configCache = mock(SysConfigCacheService.class);
        listener = new ServiceExceptionNotificationListener(publisher, configCache);
    }

    /** 正例夹具：ErrCodeSys.SYS_ERR＝ERROR 级（severe）、SYSTEM 类型（非 biz），core kernel 同源 */
    private static ServiceExceptionEvent severeSystemEvent() {
        return new ServiceExceptionEvent(new ServiceException(ErrCodeSys.SYS_ERR));
    }

    /** biz 反例夹具：匿名 ErrCode 覆写 errType=BIZ、级别 ERROR——severe 但属业务话术，应被过滤 */
    private static ServiceExceptionEvent bizSevereEvent() {
        return new ServiceExceptionEvent(new ServiceException(new ErrCode() {
            @Override
            public Integer getErrCode() {
                return 99001;
            }

            @Override
            public String name() {
                return "TEST_BIZ_SEVERE";
            }

            @Override
            public String getErrMsg() {
                return "业务侧严重失败";
            }

            @Override
            public ErrCodeLevel getErrLevel() {
                return ErrCodeLevel.ERROR;
            }

            @Override
            public String getErrType() {
                return ErrCodeType.BIZ.getValue();
            }
        }));
    }

    @Test
    public void severeNonBizPublishesWithPinnedFields_defaultReceiver() {
        listener.onServiceException(severeSystemEvent());

        ArgumentCaptor<NotificationDraft> captor = ArgumentCaptor.forClass(NotificationDraft.class);
        verify(publisher).publish(captor.capture());
        NotificationDraft draft = captor.getValue();
        assertEquals("msgType", "SYSTEM_ERROR_ALERT", draft.getMsgType());
        assertEquals("level", NotificationLevel.ERROR, draft.getLevel());
        assertEquals("缺省收件人＝内置管理员（MenuSeeder.SYSTEM_USER 同源）", "000001", draft.getTargetUser());
        assertEquals("title＝[errCode] errorMessage", "[100] 系统错误", draft.getTitle());
        assertEquals("无请求信息＝summary 回落 errType（SYSTEM='0'）", "0", draft.getSummary());
        // ServiceException 带 Lombok @Data：printStackTrace 首行走覆写 toString()（字段形态），
        // 故钉简单类名＋堆栈帧行（\tat …），不钉全限定名
        assertTrue("content＝完整堆栈（publisher 侧截 2000）",
                draft.getContent().contains("ServiceException") && draft.getContent().contains("\tat "));
        assertEquals("bizType", "ServiceException", draft.getBizType());
        assertEquals("bizId", "100", draft.getBizId());
        assertEquals("dedupeKey＝errCode＋异常类名（同错误未读合并）",
                "ERR:100:com.bidr.kernel.exception.ServiceException", draft.getDedupeKey());
        assertNull("框架腿无跳转动作", draft.getActionType());
        assertNull("不设 tenantId（归属兜底行为见监听器 javadoc 租户段）", draft.getTenantId());
    }

    @Test
    public void warnLevelSkipped() {
        listener.onServiceException(new ServiceExceptionEvent(
                new ServiceException(ErrCodeSys.SYS_VALIDATE_NOT_PASS, "非法转移")));

        verify(publisher, never()).publish(any());
    }

    @Test
    public void bizExceptionSkippedEvenWhenSevere() {
        listener.onServiceException(bizSevereEvent());

        verify(publisher, never()).publish(any());
    }

    @Test
    public void configOverride_changesReceiver_andRequestUrlPreferredAsSummary() {
        when(configCache.getSysConfigValue(ServiceExceptionNotificationListener.CONFIG_TARGET_USER))
                .thenReturn("888888");
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getRequestURL()).thenReturn(new StringBuffer("http://10.3.6.60/inkhub/api/demo"));
        when(request.getMethod()).thenReturn("POST");

        listener.onServiceException(new ServiceExceptionEvent(new ServiceException(ErrCodeSys.SYS_ERR), request));

        ArgumentCaptor<NotificationDraft> captor = ArgumentCaptor.forClass(NotificationDraft.class);
        verify(publisher).publish(captor.capture());
        assertEquals("配置覆盖收件人", "888888", captor.getValue().getTargetUser());
        assertEquals("有请求信息＝requestUrl 优先作 summary",
                "http://10.3.6.60/inkhub/api/demo", captor.getValue().getSummary());
    }

    @Test
    public void configOff_suppressesSending() {
        // "OFF"＝忽略大小写命中关闭态（用大写钉住 equalsIgnoreCase）
        when(configCache.getSysConfigValue(ServiceExceptionNotificationListener.CONFIG_TARGET_USER))
                .thenReturn("OFF");

        listener.onServiceException(severeSystemEvent());

        verify(publisher, never()).publish(any());
    }

    @Test
    public void configBlank_suppressesSending() {
        // 空串/纯空白＝显式配置了空值，视同关闭（review S1：与"缺项缺省 000001"区分开）
        when(configCache.getSysConfigValue(ServiceExceptionNotificationListener.CONFIG_TARGET_USER))
                .thenReturn("   ");

        listener.onServiceException(severeSystemEvent());

        verify(publisher, never()).publish(any());
    }

    @Test
    public void configReadFailure_fallsBackToDefaultAndStillSends() {
        // 真机上缺配置＝getSysConfigValue 抛 PA_DATA_NOT_EXIST——必须吞掉按缺省 000001 照发
        doThrow(new ServiceException("sys_config 缺项（PA_DATA_NOT_EXIST 模拟）"))
                .when(configCache).getSysConfigValue(ServiceExceptionNotificationListener.CONFIG_TARGET_USER);

        listener.onServiceException(severeSystemEvent());

        ArgumentCaptor<NotificationDraft> captor = ArgumentCaptor.forClass(NotificationDraft.class);
        verify(publisher).publish(captor.capture());
        assertEquals("读异常回缺省收件人 000001", "000001", captor.getValue().getTargetUser());
    }
}
