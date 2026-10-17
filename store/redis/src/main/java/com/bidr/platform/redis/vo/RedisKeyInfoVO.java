package com.bidr.platform.redis.vo;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

/**
 * Title: RedisKeyInfoVO
 * Description: Redis Key 信息
 *
 * @author Sharp
 * @since 2026/07/14
 */
@Data
@ApiModel("Redis Key 信息")
public class RedisKeyInfoVO {

    @ApiModelProperty("Key 名称")
    private String key;

    @ApiModelProperty("数据类型 (string/hash/list/set/zset)")
    private String type;

    @ApiModelProperty("TTL（秒），-1表示永不过期，-2表示Key不存在")
    private Long ttl;

    @ApiModelProperty("大小/长度（string为字节长度，其他为元素个数）")
    private Long size;
}
