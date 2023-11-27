package com.bidr.neo4j.vo.configuration;

import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Size;

/**
 * Title: NodeRes
 * Description: Copyright: Copyright (c) 2023
 *
 * @author Sharp
 * @since 2023/11/08 14:04
 */
@Data
public class NodeRes {

    @ApiModelProperty(value = "id")
    @NotNull(message = "id不能为null")
    private Long id;

    @ApiModelProperty(value = "名称")
    @Size(max = 50, message = "名称最大长度要小于 50")
    @NotBlank(message = "名称不能为空")
    private String name;

    @ApiModelProperty(value = "节点名称")
    @Size(max = 50, message = "节点名称最大长度要小于 50")
    @NotBlank(message = "节点名称不能为空")
    private String label;
}
