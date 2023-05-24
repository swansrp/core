package com.bidr.platform.redis.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * Title: RedisConfiguration
 * Description: Copyright: Copyright (c) 2023
 *
 * @author Sharp
 * @since 2023/05/24 13:13
 */
@Data
@Configuration
@ConfigurationProperties("spring.redis")
public class RedisConfiguration {
    private String host;
    private String port;
    private String userName;
    private String password;
    private RedisSentinelConfig sentinel;
}
