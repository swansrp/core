package com.bidr.authorization.config.async;

import com.bidr.authorization.bo.account.AccountInfo;
import com.bidr.authorization.bo.token.TokenInfo;
import com.bidr.authorization.config.log.MdcConfig;
import com.bidr.authorization.holder.AccountContext;
import com.bidr.authorization.holder.TokenHolder;
import com.bidr.kernel.config.db.DynamicTableNameHolder;
import com.bidr.kernel.constant.err.ErrCodeLevel;
import com.bidr.kernel.event.ExceptionAlertEvent;
import com.bidr.kernel.utils.FuncUtil;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.aop.interceptor.AsyncUncaughtExceptionHandler;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.task.TaskDecorator;
import org.springframework.scheduling.annotation.AsyncConfigurer;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;

import javax.annotation.Resource;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Executor;
import java.util.concurrent.ThreadPoolExecutor;

/**
 * Title: AsyncTreadPoolConfig
 * Description: Copyright: Copyright (c) 2019 Company: BHFAE
 *
 * @author Sharp
 * @since 2022/6/6 11:10
 */
@Slf4j
@EnableAsync(proxyTargetClass = true)
@Configuration
public class AsyncContextTreadPoolConfig implements AsyncConfigurer {

    @Resource
    private ApplicationEventPublisher eventPublisher;

    @Override
    public Executor getAsyncExecutor() {
        return threadPoolTaskExecutor();
    }

    public ThreadPoolTaskExecutor threadPoolTaskExecutor() {
        ThreadPoolTaskExecutor threadPoolTaskExecutor = new ThreadPoolTaskExecutor();
        threadPoolTaskExecutor.setCorePoolSize(10);
        threadPoolTaskExecutor.setMaxPoolSize(30);
        threadPoolTaskExecutor.setQueueCapacity(100);
        threadPoolTaskExecutor.setKeepAliveSeconds(60);
        threadPoolTaskExecutor.setThreadNamePrefix("async-thread-");
        threadPoolTaskExecutor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        threadPoolTaskExecutor.setWaitForTasksToCompleteOnShutdown(true);
        threadPoolTaskExecutor.setAwaitTerminationSeconds(3);
        threadPoolTaskExecutor.setTaskDecorator(new ContextCopyingDecorator());
        threadPoolTaskExecutor.initialize();
        return threadPoolTaskExecutor;
    }

    @Override
    public AsyncUncaughtExceptionHandler getAsyncUncaughtExceptionHandler() {
        return new SpringAsyncExceptionHandler(eventPublisher);
    }

    private Map<String, String> getMdcMap() {
        Map<String, String> map = MDC.getCopyOfContextMap();
        if (FuncUtil.isEmpty(map)) {
            map = new HashMap<>(0);
        }
        return map;
    }

    private RequestAttributes getMvcContext() {
        RequestAttributes attributes = null;
        try {
            attributes = RequestContextHolder.currentRequestAttributes();
        } catch (IllegalStateException ignore) {

        }
        return attributes;
    }

    static class SpringAsyncExceptionHandler implements AsyncUncaughtExceptionHandler {

        private final ApplicationEventPublisher eventPublisher;

        SpringAsyncExceptionHandler(ApplicationEventPublisher eventPublisher) {
            this.eventPublisher = eventPublisher;
        }

        @Override
        public void handleUncaughtException(Throwable throwable, Method method, Object... obj) {
            log.error("Exception occurs in async method {}", throwable);
            publishExceptionAlertEvent(throwable, method, obj);
        }

        /**
         * 发布异步方法异常告警事件
         *
         * @param throwable 异常
         * @param method    异步方法
         * @param args      方法参数
         */
        private void publishExceptionAlertEvent(Throwable throwable, Method method, Object[] args) {
            if (eventPublisher == null) {
                return;
            }
            try {
                String description = "异步方法异常 - " + method.getDeclaringClass().getName() + "." + method.getName();

                ExceptionAlertEvent event = new ExceptionAlertEvent.Builder()
                        .source(this)
                        .description(description)
                        .severity(ErrCodeLevel.ERROR)
                        .throwable(throwable)
                        .stackTrace(getStackTraceString(throwable))
                        .stackTraceDepth(50)
                        .className(method.getDeclaringClass().getName())
                        .methodName(method.getName())
                        .args(args)
                        .includeArgs(true)
                        .includeStackTrace(true)
                        .build();

                eventPublisher.publishEvent(event);
                log.info("异步方法异常告警事件已发布，方法: {}.{}", method.getDeclaringClass().getSimpleName(), method.getName());
            } catch (Exception ex) {
                log.error("发布异步方法异常告警事件失败", ex);
            }
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

    class ContextCopyingDecorator implements TaskDecorator {

        @Override
        public Runnable decorate(Runnable runnable) {
            // 复制线程上下文信息
            Map<String, String> copyOfContextMap = getMdcMap();
            RequestAttributes context = getMvcContext();
            AccountInfo accountInfo = AccountContext.get();
            TokenInfo tokenInfo = TokenHolder.get();
            Map<String, String> dynamicTableNameInfo = DynamicTableNameHolder.get();
            return () -> {
                try {
                    MdcConfig.forkLogInfo(copyOfContextMap);
                    RequestContextHolder.setRequestAttributes(context);
                    AccountContext.set(accountInfo);
                    TokenHolder.set(tokenInfo);
                    DynamicTableNameHolder.set(dynamicTableNameInfo);
                    runnable.run();
                } finally {
                    destroyLocalTreadInfo();
                    MdcConfig.destroyMdc();
                }
            };
        }

        private void destroyLocalTreadInfo() {
            RequestContextHolder.resetRequestAttributes();
            AccountContext.remove();
            TokenHolder.remove();
            DynamicTableNameHolder.remove();
        }
    }
}
