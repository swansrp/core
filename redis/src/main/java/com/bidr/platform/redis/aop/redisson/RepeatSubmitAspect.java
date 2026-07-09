package com.bidr.platform.redis.aop.redisson;

import com.fasterxml.jackson.databind.ObjectMapper;
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
import java.security.MessageDigest;
import java.util.Arrays;
import java.util.concurrent.TimeUnit;

/**
 * 防重复提交切面
 * <p>
 * 锁 key 构建优先级：
 * <ol>
 *   <li>有方法参数 → 基于参数 MD5 摘要（防网络重试 + APP 抖动 + 触摸屏双击）</li>
 *   <li>无方法参数但有 X-Request-Id → 基于 X-Request-Id（防网络重试）</li>
 *   <li>两者都没有 → 跳过防重复检查</li>
 * </ol>
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

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    @Around("repeatSubmit() && @annotation(repeatSubmitInfo)")
    public Object around(ProceedingJoinPoint pjp, RepeatSubmit repeatSubmitInfo) throws Throwable {
        String requestId = getRequestId();

        // 优先基于请求参数摘要，无参数时回退到 X-Request-Id
        String lockIdentity = buildArgsDigest(pjp);
        if (lockIdentity == null) {
            if (requestId != null && !requestId.isEmpty()) {
                lockIdentity = requestId;
            } else {
                log.warn("无法生成防重复标识（无方法参数且无 X-Request-Id），跳过防重复检查");
                return pjp.proceed();
            }
        }

        String lockKey = buildLockKey(pjp, lockIdentity);
        long interval = repeatSubmitInfo.interval();
        TimeUnit timeUnit = repeatSubmitInfo.timeUnit();

        RLock lock = redissonClient.getLock(lockKey);
        
        try {
            log.debug("尝试获取防重复锁: {}, requestId: {}", lockKey, requestId);
            
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
     * 将方法参数序列化为 MD5 摘要作为锁标识
     * <p>
     * 无论 X-Request-Id 是否存在或是否相同，只要请求参数相同就生成相同的摘要，
     * 从而在时间窗口内拦截重复提交（网络重试、APP 抖动、触摸屏双击等场景）
     */
    private String buildArgsDigest(ProceedingJoinPoint pjp) {
        Object[] args = pjp.getArgs();
        if (args == null || args.length == 0) {
            return null;
        }
        try {
            String json = OBJECT_MAPPER.writeValueAsString(Arrays.asList(args));
            MessageDigest md = MessageDigest.getInstance("MD5");
            byte[] digest = md.digest(json.getBytes("UTF-8"));
            StringBuilder sb = new StringBuilder();
            for (byte b : digest) {
                sb.append(String.format("%02x", b));
            }
            return "args:" + sb.toString();
        } catch (Exception e) {
            log.warn("序列化方法参数失败，跳过基于参数的防重复检查", e);
            return null;
        }
    }

    /**
     * 构建锁的key
     * 格式: RepeatSubmit:类名.方法名:identity
     */
    private String buildLockKey(ProceedingJoinPoint pjp, String lockIdentity) {
        StringBuilder sb = new StringBuilder(REDIS_LOCK_KEY_PREFIX);
        sb.append(pjp.getSignature().getDeclaringTypeName())
          .append(".")
          .append(pjp.getSignature().getName())
          .append(":")
          .append(lockIdentity);
        return sb.toString();
    }
}
