package com.bidr.platform.vo.dict;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

import java.util.List;

/**
 * 树形字典节点移动/排序请求
 *
 * @author Sharp
 */
@ApiModel(description = "树形字典节点移动请求")
@Data
public class TreeDictMoveReq {

    @ApiModelProperty(value = "字典编码", required = true)
    private String dictCode;

    @ApiModelProperty(value = "被移动节点的ID", required = true)
    private Long movedId;

    @ApiModelProperty(value = "移动后的父节点value（null表示根级）")
    private String parentValue;

    @ApiModelProperty(value = "目标父节点下所有子节点的排序（含被移动节点）", required = true)
    private List<NodeSort> siblings;

    @Data
    public static class NodeSort {
        @ApiModelProperty(value = "节点ID")
        private Long id;

        @ApiModelProperty(value = "排序号")
        private Integer sort;
    }
}
