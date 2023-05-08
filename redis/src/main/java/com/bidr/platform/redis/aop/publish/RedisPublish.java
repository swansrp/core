package com.bidr.platform.redis.aop.publish;

import java.lang.annotation.*;

/**
 * Title: RedisPublish
 * Description: Copyright: Copyright (c) 2019 Company: BHFAE
 *
 * @author Sharp
 * @since 2020/10/19 10:31
 */
@Target({ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
@Inherited
@Documented
public @interface RedisPublish {
    /**
     * 广播主题
     */
    String topic() default "";

    /**
     * 是否执行当前函数
     */
    boolean exec() default true;
}
