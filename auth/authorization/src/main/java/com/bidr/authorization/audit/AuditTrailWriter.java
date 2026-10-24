package com.bidr.authorization.audit;

import com.bidr.kernel.constant.err.ErrCode;
import com.bidr.kernel.constant.err.ErrCodeLevel;
import com.bidr.kernel.event.ServiceExceptionEvent;
import com.bidr.kernel.exception.ServiceException;
import lombok.RequiredArgsConstructor;
import lombok.Getter;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.ApplicationEventPublisher;

import java.util.List;
import java.util.stream.Collectors;

/**
 * 审计写入共享段（包私有）：sink 收集＋逐个写入＋失败吞并告警——显式注解切面
 * （{@link AuditTrailAspect}）与默认拦截切面（{@link AuditTrailAutoAdvisor}）的唯一收口，
 * 两模式纪律一致（裁定 2：审计链路任何失败绝不影响业务返回值）。
 *
 * @author sharp (audit 框架增强：默认拦截模式 2026-10-24)
 */
@Slf4j
@RequiredArgsConstructor
class AuditTrailWriter {

    private final ObjectProvider<AuditTrailSink> sinkProvider;
    private final ApplicationEventPublisher eventPublisher;

    /**
     * 写一条审计行：sink 缺失＝log.error＋告警（行丢弃）；单 sink 抛错＝log.error、
     * 其余 sink 继续，任一失败即告警。任何异常不出本方法。
     */
    void writeQuietly(AuditRecord record) {
        String headline = null;
        try {
            headline = headline(record);
            List<AuditTrailSink> sinks = sinkProvider.orderedStream().collect(Collectors.toList());
            if (sinks.isEmpty()) {
                log.error("审计无 AuditTrailSink 装配，审计行丢弃（宿主需装实现） {}", headline);
                publishAlert(headline + " cause=missing-sink");
                return;
            }
            boolean anySinkFailed = false;
            for (AuditTrailSink sink : sinks) {
                try {
                    sink.write(record);
                } catch (Exception ex) {
                    anySinkFailed = true;
                    log.error("审计 sink 写入失败（不回滚业务） sink={} {}",
                            sink.getClass().getName(), headline, ex);
                }
            }
            if (anySinkFailed) {
                publishAlert(headline);
            }
        } catch (Exception ex) {
            log.error("审计写入段失败，审计行丢弃（不回滚业务） {}", headline, ex);
            publishAlert((headline == null ? "record=null" : headline) + " cause=" + ex);
        }
    }

    private String headline(AuditRecord record) {
        return "bizType=" + record.getBizType() + " action=" + record.getAction()
                + " operator=" + record.getOperatorUserId();
    }

    /**
     * 直接发布审计失败告警（组装段失败等 record 尚未成形的场景）——包私有，仅审计链路内部。
     */
    void alert(String detail) {
        publishAlert(detail);
    }

    /**
     * 接入运维通知链：显式发布 {@link ServiceExceptionEvent}（监听面
     * {@code ServiceExceptionEmailListener}，@Async 邮件族——切面吞并后异常不再外抛，
     * 不会自然进全局异常处理器的发布点，故显式 publish，零新依赖）。发布本身也可能失败，
     * 同样吞并只 log.error。
     */
    private void publishAlert(String detail) {
        try {
            eventPublisher.publishEvent(new ServiceExceptionEvent(
                    new ServiceException(AuditAlertCode.AUDIT_WRITE_FAILED, detail), null));
        } catch (Exception ex) {
            log.error("审计发布告警事件失败（原审计失败明细：{}）", detail, ex);
        }
    }

    /**
     * 审计失败告警码（模块本地、kernel 零改动——ErrCode 是公开接口，authorization 内
     * 新增实现属加法）。编号取 901 独立段：本异常只进告警事件、永不到客户端
     * （切面吞并，不 rethrow），无响应码冲突面。
     */
    @Getter
    @AllArgsConstructor
    enum AuditAlertCode implements ErrCode {

        /** 审计记录写入失败（业务未受影响）——占位符承载 bizType/operator/cause 定位明细 */
        AUDIT_WRITE_FAILED(901, "审计记录写入失败（业务未受影响）：%s", ErrCodeLevel.ERROR);

        private final Integer errCode;
        private final String errMsg;
        private final ErrCodeLevel errLevel;
    }
}
