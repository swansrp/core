package com.bidr.kernel.config.log;

import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.*;
import org.springframework.stereotype.Component;

/**
 * Title: LogSilentAspect
 * Description: Copyright: Copyright (c) 2025 Company: Bidr Ltd.
 *
 * @author Sharp
 * @since 2025/6/13 11:05
 */

@Aspect
@Component
public class LogSilentAspect {

    @Pointcut("@annotation(com.bidr.kernel.config.log.LogSilent)||@within(com.bidr.kernel.config.log.LogSilent)")
    public void logSilent() {
    }

    @Before("logSilent()")
    public void before(JoinPoint point) {

    }

    @Around("logSilent()")
    public Object around(ProceedingJoinPoint joinPoint) throws Throwable {
        LogSuppressor.suppressLogs(true);
        try {
            Object[] args = joinPoint.getArgs();
            if (args != null) {
                return joinPoint.proceed(args);
            } else {
                return joinPoint.proceed();
            }
        } finally {
            LogSuppressor.suppressLogs(false);
        }
    }

    @After("logSilent()")
    public void after() {

    }

    @AfterThrowing(pointcut = "logSilent()", throwing = "e")
    public void afterThrow(JoinPoint point, Throwable e) {

    }

    @AfterReturning("logSilent()")
    public void afterReturning() {
    }
}
