package com.bidr.feishu.config;

import com.bidr.platform.redis.service.RedisService;
import com.lark.oapi.Client;
import com.lark.oapi.core.cache.ICache;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import javax.annotation.Resource;
import java.util.concurrent.TimeUnit;

/**
 * Title: FeishuConfig
 * Description: 飞书 SDK 客户端配置，注册 Client 与 redis token 缓存
 * Copyright: Copyright (c) 2025 Company: Bidr Ltd.
 *
 * @author Sharp
 * @since 2025/01/01 00:00
 */
@Slf4j
@Configuration
@ConditionalOnProperty(prefix = "feishu", name = "app-id")
public class FeishuConfig {

    @Resource
    private FeishuProperties feishuProperties;

    @Resource
    private RedisService redisService;

    /**
     * 飞书 token redis 缓存（tenant_access_token 有效期 7200 秒，由 SDK 通过此缓存复用）
     */
    @Bean
    @ConditionalOnMissingBean(name = "feishuTokenCache")
    public ICache feishuTokenCache() {
        return new ICache() {
            @Override
            public String get(String key) {
                return redisService.get("FEISHU:TOKEN:" + key, String.class);
            }

            @Override
            public void set(String key, String value, int expireSecond, TimeUnit timeUnit) {
                redisService.set("FEISHU:TOKEN:" + key, expireSecond, value);
            }
        };
    }

    /**
     * 飞书官方 SDK 客户端（自动管理 tenant_access_token，缓存走 redis）
     */
    @Bean
    @ConditionalOnMissingBean(Client.class)
    public Client feishuClient(ICache feishuTokenCache) {
        Client.Builder builder = Client.newBuilder(feishuProperties.getAppId(), feishuProperties.getAppSecret())
                .tokenCache(feishuTokenCache)
                .logReqAtDebug(true);
        if (!"https://open.feishu.cn".equals(feishuProperties.getHost())) {
            builder.openBaseUrl(feishuProperties.getHost());
        }
        return builder.build();
    }
}
