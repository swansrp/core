package com.bidr.authorization.config.async;

import com.bidr.authorization.bo.account.AccountInfo;
import com.bidr.authorization.bo.token.TokenInfo;
import com.bidr.authorization.config.log.MdcConfig;
import com.bidr.authorization.holder.AccountContext;
import com.bidr.authorization.holder.TokenHolder;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.aop.interceptor.AsyncUncaughtExceptionHandler;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.task.TaskDecorator;
import org.springframework.scheduling.annotation.AsyncConfigurer;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;

import java.lang.reflect.Method;
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
        return new SpringAsyncExceptionHandler();
    }

    static class SpringAsyncExceptionHandler implements AsyncUncaughtExceptionHandler {
        @Override
        public void handleUncaughtException(Throwable throwable, Method method, Object... obj) {
            log.error("Exception occurs in async method {}", throwable.getMessage());
        }
    }

    class ContextCopyingDecorator implements TaskDecorator {

        @Override
        public Runnable decorate(Runnable runnable) {
            // 复制线程上下文信息
            Map<String, String> copyOfContextMap = MDC.getCopyOfContextMap();
            RequestAttributes context = RequestContextHolder.currentRequestAttributes();
            AccountInfo accountInfo = AccountContext.get();
            TokenInfo tokenInfo = TokenHolder.get();
            return () -> {
                try {
                    MdcConfig.forkLogInfo(copyOfContextMap);
                    RequestContextHolder.setRequestAttributes(context);
                    AccountContext.set(accountInfo);
                    TokenHolder.set(tokenInfo);
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
        }
    }
}
