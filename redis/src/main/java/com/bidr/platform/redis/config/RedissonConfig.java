package com.bidr.platform.redis.config;

import org.apache.commons.lang3.StringUtils;
import org.redisson.Redisson;
import org.redisson.api.RedissonClient;
import org.redisson.config.BaseConfig;
import org.redisson.config.Config;
import org.redisson.config.SentinelServersConfig;
import org.redisson.config.SingleServerConfig;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

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
    private final String SENTINEL_URL_FORMAT = "redis://%s";

    @Autowired
    private RedisConfiguration redisConfig;

    @Value("${app.projectId:}")
    private String projectId;

    @Value("${spring.application.name}")
    private String applicationName;

    @Bean(destroyMethod = "shutdown")
    public RedissonClient redisson() {
        String host = redisConfig.getHost();
        String port = redisConfig.getPort();
        String userName = redisConfig.getUserName();
        String password = redisConfig.getPassword();
        RedisSentinelConfig sentinel = redisConfig.getSentinel();
        Config config = new Config();
        BaseConfig<?> serversConfig = null;
        if (sentinel != null) {
            String master = redisConfig.getSentinel().getMaster();
            List<String> nodes = redisConfig.getSentinel().getNodes();
            if (StringUtils.isNotEmpty(master) && nodes.size() > 0) {
                String[] sentinelAddressesWithSchema = new String[nodes.size()];
                for (int i = 0; i < nodes.size(); i++) {
                    sentinelAddressesWithSchema[i] = String.format(SENTINEL_URL_FORMAT, nodes.get(i));
                }
                SentinelServersConfig sentinelServersConfig = config.useSentinelServers();
                sentinelServersConfig.setMasterName(master).addSentinelAddress(sentinelAddressesWithSchema);
                serversConfig = sentinelServersConfig;
            }
        }
        if (serversConfig == null) {
            String redisUrl = String.format(HOST_URL_FORMAT, host, port);
            SingleServerConfig singleServerConfig = config.useSingleServer();
            singleServerConfig.setAddress(redisUrl);
            serversConfig = singleServerConfig;
        }
        setRedisAuth(userName, password, serversConfig);

        return Redisson.create(config);
    }

    private void setRedisAuth(String userName, String password, BaseConfig<?> serversConfig) {
        if (StringUtils.isNotEmpty(password)) {
            serversConfig.setPassword(password);
        }
        if (StringUtils.isNotEmpty(userName)) {
            serversConfig.setUsername(userName);
        }
    }

    /**
     * 构建缓存 key 前缀
     */
    public String buildKeyPrefix() {
        if (StringUtils.isNotBlank(projectId)) {
            return projectId;
        }
        return applicationName;
    }
}
