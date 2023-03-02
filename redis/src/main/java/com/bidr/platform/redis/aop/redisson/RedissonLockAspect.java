package com.bidr.platform.redis.aop.redisson;

import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.*;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;

/**
 * Title: RedissonLockAspect
 * Description: Copyright: Copyright (c) 2019 Company: BHFAE
 *
 * @author Sharp
 * @date 2019/9/29 21:50
 */
@Slf4j
@Aspect
@Component
public class RedissonLockAspect {

    private static final String REDIS_LOCK_KEY = "Redisson:";

    @Resource
    private RedissonClient redissonClient;

    @Pointcut("@annotation(com.bidr.platform.redis.aop.redisson.RedisLock)")
    public void redisLock() {
    }

    @Around("redisLock() && @annotation(lockInfo)")
    public Object around(ProceedingJoinPoint pjp, RedisLock lockInfo) throws Throwable {
        String syncKey = getSyncKey(pjp, lockInfo.syncKey());
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
     * 获取包括方法参数上的key
     *
     * @param pjp
     * @param synKey
     * @return
     */
    private String getSyncKey(ProceedingJoinPoint pjp, String synKey) {
        StringBuffer synKeyBuffer = new StringBuffer(REDIS_LOCK_KEY);
        synKeyBuffer.append(pjp.getSignature().getDeclaringTypeName()).append(".").append(pjp.getSignature().getName());
        if (StringUtils.isNoneEmpty(synKey)) {
            synKeyBuffer.append(".").append(synKey);
        }
        return synKeyBuffer.toString();
    }

    @After("redisLock()")
    public void after() {

    }

    @AfterThrowing(pointcut = "redisLock()", throwing = "e")
    public void afterThrow(JoinPoint point, Throwable e) {

    }

}
