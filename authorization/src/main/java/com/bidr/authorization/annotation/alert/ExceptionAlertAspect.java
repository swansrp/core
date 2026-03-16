package com.bidr.authorization.annotation.alert;

import com.bidr.authorization.bo.account.AccountInfo;
import com.bidr.authorization.holder.AccountContext;
import com.bidr.email.constant.param.EmailParam;
import com.bidr.kernel.constant.err.ErrCodeLevel;
import com.bidr.kernel.event.ExceptionAlertEvent;
import com.bidr.kernel.utils.FuncUtil;
import com.bidr.kernel.utils.HttpUtil;
import com.bidr.platform.service.cache.SysConfigCacheService;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.annotation.AfterThrowing;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Pointcut;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.lang.reflect.Method;

/**
 * Title: ExceptionAlertAspect
 * Description: 异常告警切面，捕获被 @ExceptionAlert 注解的方法异常并发送告警通知
 * Copyright: Copyright (c) 2022 Company: Sharp Ltd.
 *
 * @author Sharp
 * @since 2023/03/31 09:24
 */
@Slf4j
@Aspect
@Component
public class ExceptionAlertAspect {

    @Resource
    private ApplicationEventPublisher eventPublisher;

    @Resource
    private SysConfigCacheService sysConfigCacheService;

    @Pointcut("@annotation(com.bidr.authorization.annotation.alert.ExceptionAlert)")
    public void exceptionAlert() {
    }

    @AfterThrowing(pointcut = "exceptionAlert() && @annotation(alert)", throwing = "e")
    public void afterThrowing(JoinPoint joinPoint, ExceptionAlert alert, Throwable e) {
        try {
            // 构建并发布异常告警事件
            ExceptionAlertEvent event = buildExceptionAlertEvent(joinPoint, alert, e);
            eventPublisher.publishEvent(event);

            log.info("异常告警事件已发布，严重级别: {}", alert.severity().name());
        } catch (Exception ex) {
            log.error("发布异常告警事件失败", ex);
        }
    }

    /**
     * 构建异常告警事件
     */
    private ExceptionAlertEvent buildExceptionAlertEvent(JoinPoint joinPoint, ExceptionAlert alert, Throwable e) {
        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        Method method = signature.getMethod();
        String className = joinPoint.getTarget().getClass().getName();
        String methodName = method.getName();

        // 获取堆栈深度：注解配置优先，-2 表示使用系统配置
        int stackTraceDepth = alert.stackTraceDepth();
        if (stackTraceDepth == -2) {
            stackTraceDepth = getStackDepthFromConfig();
        }

        ExceptionAlertEvent.Builder builder = new ExceptionAlertEvent.Builder()
                .source(this)
                .description(alert.value())
                .severity(alert.severity())
                .throwable(e)
                .stackTrace(getStackTraceString(e))
                .stackTraceDepth(stackTraceDepth)
                .className(className)
                .methodName(methodName)
                .includeArgs(alert.includeArgs())
                .includeStackTrace(alert.includeStackTrace())
                .notifyEmails(alert.notifyEmails())
                .notifyWx(alert.notifyWx());

        // 添加方法参数
        if (alert.includeArgs()) {
            builder.args(joinPoint.getArgs());
        }

        // 添加用户信息
        if (alert.includeUserInfo()) {
            appendUserInfo(builder);
        }

        // 添加请求信息
        if (alert.includeRequestInfo()) {
            appendRequestInfo(builder);
        }

        return builder.build();
    }

    /**
     * 添加用户信息
     */
    private void appendUserInfo(ExceptionAlertEvent.Builder builder) {
        try {
            AccountInfo accountInfo = AccountContext.get();
            if (accountInfo != null) {
                builder.userId(accountInfo.getUserId())
                        .userName(accountInfo.getUserName())
                        .name(accountInfo.getName())
                        .customerNumber(accountInfo.getCustomerNumber())
                        .email(accountInfo.getEmail())
                        .phoneNumber(accountInfo.getPhoneNumber());
            }
        } catch (Exception e) {
            log.debug("获取用户信息失败", e);
        }
    }

    /**
     * 添加请求信息
     */
    private void appendRequestInfo(ExceptionAlertEvent.Builder builder) {
        try {
            ServletRequestAttributes attributes = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
            if (attributes != null) {
                HttpServletRequest request = attributes.getRequest();
                builder.requestUrl(request.getRequestURL().toString())
                        .requestMethod(request.getMethod())
                        .clientIp(HttpUtil.getRemoteIp(request))
                        .queryString(request.getQueryString());
            }
        } catch (Exception e) {
            log.debug("获取请求信息失败", e);
        }
    }

    /**
     * 获取异常堆栈信息字符串
     */
    private String getStackTraceString(Throwable e) {
        StringWriter sw = new StringWriter();
        PrintWriter pw = new PrintWriter(sw);
        e.printStackTrace(pw);
        return sw.toString();
    }

    /**
     * 从系统配置获取堆栈深度
     */
    private int getStackDepthFromConfig() {
        String depthStr = sysConfigCacheService.getSysConfigValue(EmailParam.EXCEPTION_NOTIFY_STACK_DEPTH);
        if (FuncUtil.isEmpty(depthStr)) {
            return 50;
        }
        try {
            return Integer.parseInt(depthStr);
        } catch (NumberFormatException e) {
            return 50;
        }
    }
}
