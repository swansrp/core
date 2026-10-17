package com.bidr.platform.redis.aop.publish;

import lombok.Data;

/**
 * Title: RedisPublishDto
 * Description: Copyright: Copyright (c) 2019 Company: BHFAE
 *
 * @author Sharp
 * @since 2021/2/2 15:35
 */
@Data
public class RedisPublishDto<T> {
    private String serialNumber;
    private T data;
}

