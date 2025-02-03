package com.bidr.platform.redis.aop.publish;

import com.bidr.kernel.utils.JsonUtil;
import com.bidr.platform.config.aop.RedisPublish;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.*;
import org.aspectj.lang.reflect.MethodSignature;
import org.redisson.PubSubMessageListener;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.annotation.AnnotationUtils;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Title: RedisPublishAspect
 * Description: Copyright: Copyright (c) 2019 Company: BHFAE
 *
 * @author Sharp
 * @since 2020/10/19 11:28
 */
@Slf4j
@Aspect
@Component
public class RedisPublishAspect {
    @Resource
    private RedisPublishConfig redisPublishConfig;
    @Resource
    private RedisPublishManager redisPublishManager;

    @Pointcut("@annotation(com.bidr.platform.config.aop.RedisPublish)")
    public void redisPublish() {
    }

    @Before("redisPublish()")
    public void before(JoinPoint point) {

    }

    @Around("redisPublish()")
    public Object around(ProceedingJoinPoint pjp) throws Throwable {
        RedisPublish redisPublish = getRedisPublish(pjp);
        String topic = getTopic(redisPublish, pjp);
        Object[] agrs = pjp.getArgs();
        boolean needExec = needExec(redisPublish);
        if (callFromMessageListener()) {
            log.info("接受广播 {}\n{}", topic, JsonUtil.toJson(agrs));
            try {
                if (agrs != null) {
                    return pjp.proceed(agrs);
                } else {
                    return pjp.proceed();
                }
            } catch (Exception e) {
                e.printStackTrace();
                throw e;
            }
        } else {
            Object res = null;
            if (needExec) {
                if (pjp.getArgs() != null) {
                    res = pjp.proceed(pjp.getArgs());
                } else {
                    res = pjp.proceed();
                }
            }
            Object arg = getArg(pjp);
            log.debug("发布广播 {}", topic);
            if (arg != null) {
                redisPublishConfig.publish(topic, arg);
            } else {
                redisPublishConfig.publish(topic);
            }
            return res;
        }
    }

    private RedisPublish getRedisPublish(ProceedingJoinPoint pjp) {
        MethodSignature signature = (MethodSignature) pjp.getSignature();
        Method method = signature.getMethod();
        return AnnotationUtils.findAnnotation(method, RedisPublish.class);
    }

    private String getTopic(RedisPublish redisPublish, ProceedingJoinPoint pjp) {
        Class<?> clazz = pjp.getSignature().getDeclaringType();
        String methodName = pjp.getSignature().getName();
        String topic = redisPublishManager.getDefaultTopic(clazz, methodName);
        if (redisPublish != null) {
            if (StringUtils.isNotBlank(redisPublish.topic())) {
                topic = redisPublish.topic();
            }
        }
        return topic;
    }

    private boolean needExec(RedisPublish redisPublish) {
        if (redisPublish == null) {
            return false;
        } else {
            return redisPublish.exec();
        }
    }

    @SuppressWarnings("rawtypes")
    private boolean callFromMessageListener() {
        return new SecurityManager() {
            public List<Class> getClassArray() {
                return new ArrayList<>(Arrays.asList(getClassContext()));
            }
        }.getClassArray().contains(PubSubMessageListener.class);

    }

    private Object getArg(ProceedingJoinPoint pjp) {
        Object arg = null;
        Object[] args = pjp.getArgs();
        if (args != null) {
            arg = args[0];
        }
        return arg;
    }

    @After("redisPublish()")
    public void after() {

    }

    @AfterThrowing(pointcut = "redisPublish()", throwing = "e")
    public void afterThrow(JoinPoint point, Throwable e) {

    }

    @AfterReturning("redisPublish()")
    public void afterReturning() {
    }
}
