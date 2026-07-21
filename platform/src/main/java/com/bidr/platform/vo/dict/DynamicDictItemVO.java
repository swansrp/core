package com.bidr.platform.vo.dict;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

/**
 * 动态字典预览项（支持树形模式）
 */
@Data
@ApiModel("动态字典预览项")
public class DynamicDictItemVO {

    @ApiModelProperty("字典值")
    private String value;

    @ApiModelProperty("字典标签")
    private String label;

    @ApiModelProperty("父级ID（树形模式时有值）")
    private String pid;
}
