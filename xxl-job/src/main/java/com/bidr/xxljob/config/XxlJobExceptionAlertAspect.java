package com.bidr.xxljob.config;

import com.bidr.kernel.constant.err.ErrCodeLevel;
import com.bidr.kernel.event.ExceptionAlertEvent;
import com.xxl.job.core.handler.annotation.XxlJob;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.annotation.AfterThrowing;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Pointcut;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.context.ApplicationEventPublisher;

import javax.annotation.Resource;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.lang.reflect.Method;

/**
 * Title: XxlJobExceptionAlertAspect
 * Description: XxlJob定时任务异常告警切面，自动拦截所有 @XxlJob 方法异常并发布告警通知
 * <p>
 * 当定时任务方法抛出异常时，自动发布 {@link ExceptionAlertEvent}，
 * 由系统统一的通知机制（企业微信、邮件）发送告警，无需业务代码手动处理。
 * <p>
 * 注意：异步任务无HTTP请求上下文和用户上下文，告警中不包含请求信息和用户信息。
 * <p>
 * Copyright: Copyright (c) 2026 Company: Bidr Ltd.
 *
 * @author Sharp
 * @since 2026/05/19
 */
@Slf4j
@Aspect
public class XxlJobExceptionAlertAspect {

    @Resource
    private ApplicationEventPublisher eventPublisher;

    @Pointcut("@annotation(com.xxl.job.core.handler.annotation.XxlJob)")
    public void xxlJobPointcut() {
    }

    @AfterThrowing(pointcut = "xxlJobPointcut() && @annotation(xxlJob)", throwing = "e")
    public void afterThrowing(JoinPoint joinPoint, XxlJob xxlJob, Throwable e) {
        try {
            ExceptionAlertEvent event = buildExceptionAlertEvent(joinPoint, xxlJob, e);
            eventPublisher.publishEvent(event);

            log.info("XxlJob异常告警事件已发布，任务: {}, 严重级别: {}", xxlJob.value(), ErrCodeLevel.ERROR.name());
        } catch (Exception ex) {
            log.error("发布XxlJob异常告警事件失败", ex);
        }
    }

    /**
     * 构建XxlJob异常告警事件
     *
     * @param joinPoint 切点
     * @param xxlJob    XxlJob注解
     * @param e         异常
     * @return 异常告警事件
     */
    private ExceptionAlertEvent buildExceptionAlertEvent(JoinPoint joinPoint, XxlJob xxlJob, Throwable e) {
        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        Method method = signature.getMethod();
        String className = joinPoint.getTarget().getClass().getName();
        String methodName = method.getName();
        String description = "XxlJob定时任务异常 - 任务: " + xxlJob.value();

        return new ExceptionAlertEvent.Builder()
                .source(this)
                .description(description)
                .severity(ErrCodeLevel.ERROR)
                .throwable(e)
                .stackTrace(getStackTraceString(e))
                .stackTraceDepth(50)
                .className(className)
                .methodName(methodName)
                .includeArgs(true)
                .includeStackTrace(true)
                .build();
    }

    /**
     * 获取异常堆栈信息字符串
     *
     * @param e 异常
     * @return 堆栈字符串
     */
    private String getStackTraceString(Throwable e) {
        StringWriter sw = new StringWriter();
        PrintWriter pw = new PrintWriter(sw);
        e.printStackTrace(pw);
        return sw.toString();
    }
}
