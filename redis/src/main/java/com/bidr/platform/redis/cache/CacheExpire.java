package com.bidr.platform.redis.cache;

import java.lang.annotation.*;

/**
 * Title: CacheExpire
 * Description: Copyright: Copyright (c) 2019 Company: BHFAE
 *
 * @author Sharp
 * @since 2022/6/6 15:07
 */
@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
@Inherited
@Documented
public @interface CacheExpire {

    /**
     * expire time, default ttl = -1
     */
    long expire() default 0L;
}
