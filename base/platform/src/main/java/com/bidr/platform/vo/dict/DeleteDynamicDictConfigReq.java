package com.bidr.platform.vo.dict;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

/**
 * 删除动态字典配置请求
 *
 * @author Sharp
 * @since 2026-07-14
 */
@ApiModel(description = "删除动态字典配置请求")
@Data
public class DeleteDynamicDictConfigReq {

    @ApiModelProperty(value = "配置ID", required = true)
    private Long id;
}
