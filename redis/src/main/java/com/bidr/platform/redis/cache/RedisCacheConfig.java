package com.bidr.platform.redis.cache;

import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.CachingConfigurerSupport;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.interceptor.KeyGenerator;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.core.LocalVariableTableParameterNameDiscoverer;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheWriter;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext;
import org.springframework.data.redis.serializer.StringRedisSerializer;

import java.lang.reflect.Method;
import java.time.Duration;

/**
 * Title: RedisCacheConfig
 * Description: Copyright: Copyright (c) 2019 Company: BHFAE
 *
 * @author Sharp
 * @date 2020/8/12 10:55
 */

@EnableCaching
@Configuration
public class RedisCacheConfig extends CachingConfigurerSupport {

    /**
     * Set RedisTemplate add Serializer key StringRedisSerializer value GenericJackson2JsonRedisSerializer another
     * easier way , change defaultSerializer implementation
     *
     * @return byteRedisTemplate
     */
    @Bean(name = "byteRedisTemplate")
    public RedisTemplate<String, byte[]> byteRedisTemplate(RedisConnectionFactory redisConnectionFactory) {
        RedisTemplate<String, byte[]> redisTemplate = new RedisTemplate<>();
        setRedisSerializer(redisConnectionFactory, redisTemplate);
        return redisTemplate;
    }

    private void setRedisSerializer(RedisConnectionFactory redisConnectionFactory,
                                    RedisTemplate<String, ?> redisTemplate) {
        redisTemplate.setConnectionFactory(redisConnectionFactory);
        redisTemplate.setDefaultSerializer(new GenericJackson2JsonRedisSerializer());
        redisTemplate.setKeySerializer(new StringRedisSerializer());
        redisTemplate.setHashKeySerializer(new StringRedisSerializer());
        redisTemplate.setValueSerializer(new GenericJackson2JsonRedisSerializer());
        redisTemplate.setHashValueSerializer(new GenericJackson2JsonRedisSerializer());
        redisTemplate.afterPropertiesSet();
    }

    /**
     * Set RedisTemplate add Serializer key StringRedisSerializer value GenericJackson2JsonRedisSerializer another
     * easier way , change defaultSerializer implementation
     *
     * @return redisTemplate
     */
    @Bean(name = "redisTemplate")
    public RedisTemplate<String, Object> redisTemplate(RedisConnectionFactory redisConnectionFactory) {
        RedisTemplate<String, Object> redisTemplate = new RedisTemplate<>();
        setRedisSerializer(redisConnectionFactory, redisTemplate);
        return redisTemplate;
    }

    /**
     * Customer generate redis-key
     *
     * @return 缓存key构造方法
     */
    @Bean
    @Override
    public KeyGenerator keyGenerator() {
        return new KeyGenerator() {

            @Override
            public String generate(Object target, Method method, Object... params) {
                return method.getDeclaringClass().getName() +
                        "." +
                        method.getName() +
                        "(" +
                        getParams(method, params) +
                        ")";
            }

            private String getParams(Method method, Object... args) {
                LocalVariableTableParameterNameDiscoverer u = new LocalVariableTableParameterNameDiscoverer();
                String[] names = u.getParameterNames(method);
                Class<?>[] types = method.getParameterTypes();
                String res = "";
                if (args != null && args.length > 0 && names != null) {
                    res = types[0].getSimpleName() + " " + names[0] + "<" + args[0].toString() + ">";
                    for (int i = 1; i < names.length; i++) {
                        res += ", ";
                        res += types[i].getSimpleName() + " " + names[i] + "<" + args[i].toString() + ">";
                    }
                }
                return res;
            }
        };
    }

    @Bean(name = "objectRedisTemplate")
    public RedisTemplate<Object, Object> objectRedisTemplate(RedisConnectionFactory redisConnectionFactory) {
        RedisTemplate<Object, Object> redisTemplate = new RedisTemplate<>();
        redisTemplate.setConnectionFactory(redisConnectionFactory);
        redisTemplate.setDefaultSerializer(new GenericJackson2JsonRedisSerializer());
        redisTemplate.setKeySerializer(new GenericJackson2JsonRedisSerializer());
        redisTemplate.setHashKeySerializer(new GenericJackson2JsonRedisSerializer());
        redisTemplate.setValueSerializer(new GenericJackson2JsonRedisSerializer());
        redisTemplate.setHashValueSerializer(new GenericJackson2JsonRedisSerializer());
        redisTemplate.afterPropertiesSet();
        return redisTemplate;
    }

    /**
     * Set RedisCacheManager Use Bean Manage Redis Cache
     *
     * @return 构造cacheManager
     */
    @Bean
    @Primary
    CacheManager cacheManager(RedisConnectionFactory redisConnectionFactory) {
        // Init RedisCacheWriter
        RedisCacheWriter redisCacheWriter = RedisCacheWriter.nonLockingRedisCacheWriter(redisConnectionFactory);
        GenericJackson2JsonRedisSerializer serializer = new GenericJackson2JsonRedisSerializer();
        RedisSerializationContext.SerializationPair<Object> pair =
                RedisSerializationContext.SerializationPair.fromSerializer(serializer);
        RedisCacheConfiguration defaultCacheConfig =
                RedisCacheConfiguration.defaultCacheConfig().serializeValuesWith(pair).entryTtl(Duration.ofSeconds(600));
        // Init RedisCacheManager
        return new MyRedisCacheManager(redisCacheWriter, defaultCacheConfig);
    }

}
