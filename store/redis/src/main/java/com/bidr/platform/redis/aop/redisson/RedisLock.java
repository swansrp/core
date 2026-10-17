package com.bidr.platform.redis.aop.redisson;

import java.lang.annotation.*;
import java.util.concurrent.TimeUnit;

/**
 * Title: redisLock
 * Description: Copyright: Copyright (c) 2019 Company: BHFAE
 *
 * @author 沙若鹏
 * @since 2019/9/29 21:49
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
     * <p>
     * 注意:syncKey 为固定字符串,不支持动态参数。如需基于方法参数生成动态 key,请使用 {@link #syncKeySpel()}
     */
    String syncKey() default "";

    /**
     * 基于 SpEL 表达式的动态锁 key(优先级高于 {@link #syncKey()})<br/>
     * 表达式可引用方法参数(按参数名),最终拼接到 redis key 中<br/>
     * 示例: <code>"'order:' + #req.orderId"</code><br/>
     * 为空时回退到 {@link #syncKey()} 的固定字符串逻辑
     * <p>
     * 加在 controller 层时可确保 service 的 @Transactional 在释放锁前已完成 commit,
     * 后续等待锁的线程能读到已提交数据,避免 check-then-act 竞态
     *
     * @since 2026/07/19
     */
    String syncKeySpel() default "";

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
