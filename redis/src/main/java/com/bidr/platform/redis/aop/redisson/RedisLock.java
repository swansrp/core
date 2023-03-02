package com.bidr.platform.redis.aop.redisson;

import java.lang.annotation.*;
import java.util.concurrent.TimeUnit;

/**
 * Title: redisLock
 * Description: Copyright: Copyright (c) 2019 Company: BHFAE
 *
 * @author 沙若鹏
 * @date 2019/9/29 21:49
 * @description Project Name: Grote
 * @Package: com.srct.service.config.redisson
 */
@Target({ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
@Inherited
public @interface RedisLock {

    /**
     * 锁的key<br/>
     * 如果想增加坑的个数添加非固定锁，可以在参数上添加@RedisLock注解，但是本参数是必写选项<br/>
     * redis key的拼写规则为 当前注解类+所在的方法 + syncKey + @RedisLock<br/>
     */
    String syncKey() default "";

    /**
     * 自动释放锁时间,
     * 单位毫秒,默认20秒<br/>
     * 如果为0表示永远不释放锁，
     * 但是没有比较强的业务要求下，不建议设置为0
     */
    long releaseTime() default 20 * 1000L;

    /**
     * 自动释放锁时间单位,
     * 默认为毫秒
     */
    TimeUnit releaseTimeUint() default TimeUnit.MILLISECONDS;

    /**
     * 锁获取超时时间：<br/>
     * 单位毫秒,默认不等待
     */
    long waitTime() default 0L;

    /**
     * 是否及时释放锁：<br/>
     * 默认是
     * 若为否,则保持锁直至自动释放
     */
    boolean unlockPromptly() default true;
}
