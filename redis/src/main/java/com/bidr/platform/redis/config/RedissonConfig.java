package com.bidr.platform.redis.config;

import org.apache.commons.lang3.StringUtils;
import org.redisson.Redisson;
import org.redisson.api.RedissonClient;
import org.redisson.config.Config;
import org.redisson.config.SingleServerConfig;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Title: RedissonConfig
 * Description: Copyright: Copyright (c) 2019 Company: BHFAE
 *
 * @author 沙若鹏
 * @since 2019/9/29 21:50
 */
@Configuration
public class RedissonConfig {

    private final String HOST_URL_FORMAT = "redis://%s:%s";

    @Value("${spring.redis.port}")
    private String port;
    @Value("${spring.redis.host}")
    private String host;
    @Value("${spring.redis.password}")
    private String password;

    @Value("${spring.redis.username:}")
    private String username;


    @Bean(destroyMethod = "shutdown")
    public RedissonClient redisson() {
        String redisUrl = String.format(HOST_URL_FORMAT, host, port);
        Config config = new Config();
        SingleServerConfig singleServerConfig = config.useSingleServer();
        singleServerConfig.setAddress(redisUrl);
        if (StringUtils.isNotEmpty(password)) {
            singleServerConfig.setPassword(password);
        }
        if (StringUtils.isNotEmpty(username)) {
            singleServerConfig.setUsername(username);
        }
        RedissonClient redisson = Redisson.create(config);
        return redisson;
    }


}
