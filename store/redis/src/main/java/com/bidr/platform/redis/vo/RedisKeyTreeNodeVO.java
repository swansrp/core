package com.bidr.platform.redis.vo;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

import java.util.List;

/**
 * Title: RedisKeyTreeNodeVO
 * Description: Redis Key 树节点（按 ":" 分隔）
 *
 * @author Sharp
 * @since 2026/07/14
 */
@Data
@ApiModel("Redis Key 树节点")
public class RedisKeyTreeNodeVO {

    @ApiModelProperty("节点名称（key片段）")
    private String title;

    @ApiModelProperty("完整前缀路径")
    private String key;

    @ApiModelProperty("该前缀下的Key数量")
    private Long keyCount;

    @ApiModelProperty("是否为实际存在的Key")
    private Boolean isKey;

    @ApiModelProperty("子节点")
    private List<RedisKeyTreeNodeVO> children;
}
