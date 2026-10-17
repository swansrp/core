package com.bidr.forge.vo.widetable;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

import java.util.List;

/**
 * 宽表配置保存请求
 *
 * @author sharp
 */
@ApiModel(description = "宽表配置保存请求")
@Data
public class FormWideTableConfigReq {
    /**
     * 主键 ID（更新时传，新增时不传）
     */
    @ApiModelProperty(value = "主键 ID（更新时传）")
    private Long id;

    /**
     * 关联表单 ID
     */
    @ApiModelProperty(value = "关联表单 ID")
    private String formId;

    /**
     * 配置名称
     */
    @ApiModelProperty(value = "配置名称")
    private String title;

    /**
     * 描述
     */
    @ApiModelProperty(value = "描述")
    private String description;

    /**
     * 状态: draft/active/inactive
     */
    @ApiModelProperty(value = "状态: draft/active/inactive")
    private String status;

    /**
     * 选中的表单字段 ID 列表
     */
    @ApiModelProperty(value = "选中的表单字段 ID 列表")
    private List<Long> attributeIds;
}
