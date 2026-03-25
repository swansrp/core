package com.bidr.platform.redis.aop.redisson;

import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Pointcut;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;
import java.util.concurrent.TimeUnit;

/**
 * 防重复提交切面
 * <p>
 * 基于 X-Request-Id 请求头实现分布式锁，防止网络重试导致的重复提交
 *
 * @author sharuopeng
 * @since 2026/03/25
 */
@Slf4j
@Aspect
@Component
public class RepeatSubmitAspect {

    private static final String REDIS_LOCK_KEY_PREFIX = "RepeatSubmit:";
    private static final String REQUEST_ID_HEADER = "X-Request-Id";

    @Resource
    private RedissonClient redissonClient;

    @Pointcut("@annotation(com.bidr.platform.redis.aop.redisson.RepeatSubmit)")
    public void repeatSubmit() {
    }

    @Around("repeatSubmit() && @annotation(repeatSubmitInfo)")
    public Object around(ProceedingJoinPoint pjp, RepeatSubmit repeatSubmitInfo) throws Throwable {
        // 获取请求ID
        String requestId = getRequestId();
        
        if (requestId == null || requestId.isEmpty()) {
            log.warn("未找到 X-Request-Id 请求头，跳过防重复检查");
            return pjp.proceed();
        }

        // 构建锁的key
        String lockKey = buildLockKey(pjp, requestId);
        long interval = repeatSubmitInfo.interval();
        TimeUnit timeUnit = repeatSubmitInfo.timeUnit();

        RLock lock = redissonClient.getLock(lockKey);
        
        try {
            log.debug("尝试获取防重复锁: {}", lockKey);
            
            // 尝试获取锁，不等待，获取失败立即返回
            boolean acquired = lock.tryLock(0, interval, timeUnit);
            
            if (acquired) {
                log.debug("成功获取防重复锁: {}", lockKey);
                return pjp.proceed();
            } else {
                log.warn("防重复提交拦截: requestId={}, lockKey={}", requestId, lockKey);
                throw new RepeatSubmitException(repeatSubmitInfo.message());
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.error("获取防重复锁被中断: {}", lockKey, e);
            throw new RepeatSubmitException("系统繁忙，请稍后重试");
        }
    }

    /**
     * 从请求头中获取 X-Request-Id
     */
    private String getRequestId() {
        ServletRequestAttributes attributes = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        if (attributes == null) {
            return null;
        }
        HttpServletRequest request = attributes.getRequest();
        return request.getHeader(REQUEST_ID_HEADER);
    }

    /**
     * 构建锁的key
     * 格式: RepeatSubmit:类名.方法名:requestId
     */
    private String buildLockKey(ProceedingJoinPoint pjp, String requestId) {
        StringBuilder sb = new StringBuilder(REDIS_LOCK_KEY_PREFIX);
        sb.append(pjp.getSignature().getDeclaringTypeName())
          .append(".")
          .append(pjp.getSignature().getName())
          .append(":")
          .append(requestId);
        return sb.toString();
    }
}
