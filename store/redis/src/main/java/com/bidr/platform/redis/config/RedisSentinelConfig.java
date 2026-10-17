package com.bidr.platform.redis.config;

import lombok.Data;

import java.util.List;

/**
 * Title: RedissSentinelConfig
 * Description: Copyright: Copyright (c) 2023
 *
 * @author Sharp
 * @since 2023/05/24 13:15
 */
@Data
public class RedisSentinelConfig {
    private String master;
    private List<String> nodes;
}
