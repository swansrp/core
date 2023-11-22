package com.bidr.platform.dao.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Size;
import lombok.Data;

 /**
 * Title: SysPortalColumn
 * Description: Copyright: Copyright (c) 2023
 *
 * @author Sharp
 * @since 2023/11/21 18:00
 */
/**
    * 系统表表头
    */
@ApiModel(description="系统表表头")
@Data
@TableName(value = "sys_portal_column")
public class SysPortalColumn {
    /**
     * id
     */
    @TableId(value = "id", type = IdType.INPUT)
    @ApiModelProperty(value="id")
    @NotNull(message = "id不能为null")
    private Long id;

    /**
     * 表id
     */
    @TableField(value = "portal_id")
    @ApiModelProperty(value="表id")
    @NotNull(message = "表id不能为null")
    private Long portalId;

    /**
     * 属性名
     */
    @TableField(value = "property")
    @ApiModelProperty(value="属性名")
    @Size(max = 50,message = "属性名最大长度要小于 50")
    @NotBlank(message = "属性名不能为空")
    private String property;

    /**
     * 显示名称
     */
    @TableField(value = "display_name")
    @ApiModelProperty(value="显示名称")
    @Size(max = 50,message = "显示名称最大长度要小于 50")
    @NotBlank(message = "显示名称不能为空")
    private String displayName;

    /**
     * 属性类型PORTAL_FIELD_DICT
     */
    @TableField(value = "field_type")
    @ApiModelProperty(value="属性类型PORTAL_FIELD_DICT")
    @NotNull(message = "属性类型PORTAL_FIELD_DICT不能为null")
    private Integer fieldType;

    /**
     * 字典或者跳转地址
     */
    @TableField(value = "reference")
    @ApiModelProperty(value="字典或者跳转地址")
    @Size(max = 50,message = "字典或者跳转地址最大长度要小于 50")
    private String reference;

    /**
     * 宽度
     */
    @TableField(value = "width")
    @ApiModelProperty(value="宽度")
    @NotNull(message = "宽度不能为null")
    private Integer width;

    /**
     * 是否固定
     */
    @TableField(value = "fixed")
    @ApiModelProperty(value="是否固定")
    @NotNull(message = "是否固定不能为null")
    private Integer fixed;

    /**
     * 显示顺序
     */
    @TableField(value = "display_order")
    @ApiModelProperty(value="显示顺序")
    @NotNull(message = "显示顺序不能为null")
    private Integer displayOrder;

    /**
     * 是否可以编辑
     */
    @TableField(value = "editable")
    @ApiModelProperty(value="是否可以编辑")
    @NotNull(message = "是否可以编辑不能为null")
    private Integer editable;

    /**
     * 是否必填
     */
    @TableField(value = "required")
    @ApiModelProperty(value="是否必填")
    @NotNull(message = "是否必填不能为null")
    private Integer required;
}