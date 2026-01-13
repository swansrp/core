package com.bidr.platform.vo.dict;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

import java.util.Date;

/**
 * 字典表VO - 前端交互
 *
 * @author sharp
 * @since 2026-01-13
 */
@ApiModel(description = "业务字典表")
@Data
public class BizDictVO {

    /**
     * 主键ID
     */
    @ApiModelProperty(value = "主键ID")
    private Long id;

    @ApiModelProperty(value = "字典编码")
    private String dictCode;

    @ApiModelProperty(value = "字典名称")
    private String dictName;

    @ApiModelProperty(value = "NULL表示系统共用字典")
    private String bizId;

    /**
     * 字典项显示名称
     */
    @ApiModelProperty(value = "字典项显示名称")
    private String label;

    @ApiModelProperty(value = "字典项值")
    private String value;

    @ApiModelProperty(value = "描述")
    private String description;

    /**
     * 排序号
     */
    @ApiModelProperty(value = "排序号")
    private Integer sort;

    @ApiModelProperty(value = "是否为默认项")
    private String isDefault;

    @ApiModelProperty(value = "创建者")
    private String createBy;

    @ApiModelProperty(value = "创建时间")
    private Date createAt;

    @ApiModelProperty(value = "更新者")
    private String updateBy;

    @ApiModelProperty(value = "更新时间")
    private Date updateAt;

    @ApiModelProperty(value = "是否有效")
    private String valid;
}