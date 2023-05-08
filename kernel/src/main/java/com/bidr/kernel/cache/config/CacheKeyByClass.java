package com.bidr.kernel.cache.config;

import org.springframework.cache.interceptor.KeyGenerator;
import org.springframework.stereotype.Component;

import java.lang.reflect.Method;

/**
 * Title: CacheKeyByClass
 * Description: Copyright: Copyright (c) 2019 Company: BHFAE
 *
 * @author Sharp
 * @since 2020/8/12 9:46
 */
@Component
public class CacheKeyByClass implements KeyGenerator {
    @Override
    public Object generate(Object target, Method method, Object... params) {
        return MyRedisCacheKeyUtil.buildCacheKey(target.getClass().getSimpleName(), "*");
    }

}
