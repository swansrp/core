package com.bidr.framework.redis.aop.publish;

import lombok.Data;

/**
 * Title: RedisPublishDto
 * Description: Copyright: Copyright (c) 2019 Company: BHFAE
 *
 * @author Sharp
 * @date 2021/2/2 15:35
 */
@Data
public class RedisPublishDto<T> {
    private String serialNumber;
    private T data;
}

