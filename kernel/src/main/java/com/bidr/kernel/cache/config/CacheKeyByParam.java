package com.bidr.kernel.cache.config;

import org.springframework.cache.interceptor.KeyGenerator;
import org.springframework.core.DefaultParameterNameDiscoverer;
import org.springframework.stereotype.Component;

import java.lang.reflect.Method;

/**
 * @author sharp
 */
@Component
public class CacheKeyByParam implements KeyGenerator {

    @Override
    public Object generate(Object target, Method method, Object... params) {
        StringBuilder key = MyRedisCacheKeyUtil.buildCacheKey(target.getClass().getSimpleName(), method.getName());
        DefaultParameterNameDiscoverer discover = new DefaultParameterNameDiscoverer();
        String[] parameterNames = discover.getParameterNames(method);
        if (params.length > 0) {
            for (int index = 0; index < parameterNames.length; index++) {
                Object object = params[index];
                key.append("&" + parameterNames[index] + "-");
                if (object != null) {
                    key.append(object.toString()).append("(").append(object.hashCode()).append(")");
                } else {
                    key.append("NULL");
                }
            }
        }
        return key;
    }
}
