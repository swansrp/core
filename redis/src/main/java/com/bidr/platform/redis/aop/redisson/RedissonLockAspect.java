package com.bidr.platform.redis.aop.redisson;

import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.*;
import org.aspectj.lang.reflect.MethodSignature;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.context.expression.MethodBasedEvaluationContext;
import org.springframework.core.DefaultParameterNameDiscoverer;
import org.springframework.core.ParameterNameDiscoverer;
import org.springframework.expression.EvaluationContext;
import org.springframework.expression.Expression;
import org.springframework.expression.ExpressionParser;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.lang.reflect.Method;

/**
 * Title: RedissonLockAspect
 * Description: Copyright: Copyright (c) 2019 Company: BHFAE
 *
 * @author Sharp
 * @since 2019/9/29 21:50
 */
@Slf4j
@Aspect
@Component
public class RedissonLockAspect {

    private static final String REDIS_LOCK_KEY = "Redisson:";

    /**
     * SpEL 解析器(单例复用,线程安全)
     */
    private static final ExpressionParser SPEL_PARSER = new SpelExpressionParser();
    /**
     * 用于发现方法参数名(依赖 -parameters 编译选项或调试信息)
     */
    private static final ParameterNameDiscoverer PARAMETER_NAME_DISCOVERER = new DefaultParameterNameDiscoverer();

    @Resource
    private RedissonClient redissonClient;

    @Pointcut("@annotation(com.bidr.platform.redis.aop.redisson.RedisLock)")
    public void redisLock() {
    }

    @Around("redisLock() && @annotation(lockInfo)")
    public Object around(ProceedingJoinPoint pjp, RedisLock lockInfo) throws Throwable {
        String syncKey = getSyncKey(pjp, lockInfo);
        RLock lock = redissonClient.getLock(syncKey);
        Object obj = null;
        try {
            log.info("尝试获取锁 {}", syncKey);
            // 得到锁，没有人加过相同的锁
            if (lock.tryLock(lockInfo.waitTime(), lockInfo.releaseTime(), lockInfo.releaseTimeUint())) {
                log.info("成功获取锁 {}", syncKey);
                obj = pjp.proceed();
            } else {
                log.info("获取 {} 锁失败", syncKey);
            }
        } catch (Exception e) {
            log.error("", e);
            throw e;
        } finally {
            if (lock.isHeldByCurrentThread() && lockInfo.unlockPromptly()) {
                log.info("释放 {} 锁", syncKey);
                lock.unlock();
            }
        }
        return obj;
    }

    /**
     * 获取锁的 key
     * <p>
     * 规则: Redisson:{className}.{methodName}[.syncKey 或 SpEL 计算结果]
     * <p>
     * 优先级: syncKeySpel(动态) > syncKey(固定) > 无后缀
     *
     * @param pjp      切点
     * @param lockInfo 注解信息
     */
    private String getSyncKey(ProceedingJoinPoint pjp, RedisLock lockInfo) {
        StringBuffer synKeyBuffer = new StringBuffer(REDIS_LOCK_KEY);
        synKeyBuffer.append(pjp.getSignature().getDeclaringTypeName())
                .append(".")
                .append(pjp.getSignature().getName());

        String dynamicKey = resolveDynamicKey(pjp, lockInfo);
        if (StringUtils.isNoneEmpty(dynamicKey)) {
            synKeyBuffer.append(".").append(dynamicKey);
        }
        return synKeyBuffer.toString();
    }

    /**
     * 解析动态 key 后缀
     * <p>
     * 1. 优先用 syncKeySpel(SpEL 表达式,支持 #参数名 引用)
     * 2. 否则回退到 syncKey(固定字符串)
     * 3. 两者都为空时返回 null(锁粒度退化到方法级)
     */
    private String resolveDynamicKey(ProceedingJoinPoint pjp, RedisLock lockInfo) {
        // 优先使用 SpEL
        if (StringUtils.isNotEmpty(lockInfo.syncKeySpel())) {
            try {
                MethodSignature signature = (MethodSignature) pjp.getSignature();
                Method method = signature.getMethod();
                Object[] args = pjp.getArgs();

                EvaluationContext context = new MethodBasedEvaluationContext(
                        null, method, args, PARAMETER_NAME_DISCOVERER);
                Expression expression = SPEL_PARSER.parseExpression(lockInfo.syncKeySpel());
                Object value = expression.getValue(context);
                return value == null ? null : value.toString();
            } catch (Exception e) {
                log.error("解析 RedisLock.syncKeySpel 失败,表达式: {}", lockInfo.syncKeySpel(), e);
                throw new IllegalArgumentException("无效的 RedisLock SpEL 表达式: " + lockInfo.syncKeySpel(), e);
            }
        }
        // 回退到固定字符串
        return lockInfo.syncKey();
    }

    @After("redisLock()")
    public void after() {

    }

    @AfterThrowing(pointcut = "redisLock()", throwing = "e")
    public void afterThrow(JoinPoint point, Throwable e) {

    }

}
