package com.bidr.platform.dao.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Size;
import java.math.BigDecimal;

 /**
 * Title: SysPortalColumn
 * Description: Copyright: Copyright (c) 2023
 *
 * @author Sharp
 * @since 2024/01/17 16:22
 */

/**
 * 系统表表头
 */
@ApiModel(description = "系统表表头")
@Data
@TableName(value = "sys_portal_column")
public class SysPortalColumn {
    /**
     * id
     */
    @TableId(value = "id", type = IdType.AUTO)
    @ApiModelProperty(value = "id")
    @NotNull(message = "id不能为null")
    private Long id;

    /**
     * 表id
     */
    @TableField(value = "portal_id")
    @ApiModelProperty(value = "表id")
    @NotNull(message = "表id不能为null")
    private Long portalId;

    /**
     * 属性名
     */
    @TableField(value = "property")
    @ApiModelProperty(value = "属性名")
    @Size(max = 50, message = "属性名最大长度要小于 50")
    @NotBlank(message = "属性名不能为空")
    private String property;

    /**
     * 数据字段名
     */
    @TableField(value = "db_field")
    @ApiModelProperty(value = "数据字段名")
    @Size(max = 50, message = "数据字段名最大长度要小于 50")
    @NotBlank(message = "数据字段名不能为空")
    private String dbField;

    /**
     * 显示名称
     */
    @TableField(value = "display_name")
    @ApiModelProperty(value = "显示名称")
    @Size(max = 50, message = "显示名称最大长度要小于 50")
    @NotBlank(message = "显示名称不能为空")
    private String displayName;

    /**
     * 属性类型PORTAL_FIELD_DICT
     */
    @TableField(value = "field_type")
    @ApiModelProperty(value = "属性类型PORTAL_FIELD_DICT")
    @Size(max = 2, message = "属性类型PORTAL_FIELD_DICT最大长度要小于 2")
    @NotBlank(message = "属性类型PORTAL_FIELD_DICT不能为空")
    private String fieldType;

    /**
     * 字典或者跳转地址
     */
    @TableField(value = "reference")
    @ApiModelProperty(value = "字典或者跳转地址")
    @Size(max = 50, message = "字典或者跳转地址最大长度要小于 50")
    private String reference;

    /**
     * 相关实体字段
     */
    @TableField(value = "entity_field")
    @ApiModelProperty(value = "相关实体字段")
    @Size(max = 50, message = "相关实体字段最大长度要小于 50")
    private String entityField;

    /**
     * 查询实体关系
     */
    @TableField(value = "entity_condition")
    @ApiModelProperty(value = "查询实体关系")
    private String entityCondition;

    /**
     * 显示顺序
     */
    @TableField(value = "display_order")
    @ApiModelProperty(value = "显示顺序")
    @NotNull(message = "显示顺序不能为null")
    private Integer displayOrder;

    /**
     * 对齐方式
     */
    @TableField(value = "align")
    @ApiModelProperty(value = "对齐方式")
    @Size(max = 20, message = "对齐方式最大长度要小于 20")
    private String align;

    /**
     * 宽度
     */
    @TableField(value = "width")
    @ApiModelProperty(value = "宽度")
    @NotNull(message = "宽度不能为null")
    private Integer width;

    /**
     * 是否固定
     */
    @TableField(value = "fixed")
    @ApiModelProperty(value = "是否固定")
    @Size(max = 1, message = "是否固定最大长度要小于 1")
    @NotBlank(message = "是否固定不能为空")
    private String fixed;

    /**
     * 是否显示tooltip
     */
    @TableField(value = "tooltip")
    @ApiModelProperty(value = "是否显示tooltip")
    @Size(max = 1, message = "是否显示tooltip最大长度要小于 1")
    @NotBlank(message = "是否显示tooltip不能为空")
    private String tooltip;

    /**
     * 表格是否可以编辑
     */
    @TableField(value = "edit_able")
    @ApiModelProperty(value = "表格是否可以编辑")
    @Size(max = 1, message = "表格是否可以编辑最大长度要小于 1")
    @NotBlank(message = "表格是否可以编辑不能为空")
    private String editAble;

    /**
     * 是否必填
     */
    @TableField(value = "required")
    @ApiModelProperty(value = "是否必填")
    @Size(max = 1, message = "是否必填最大长度要小于 1")
    @NotBlank(message = "是否必填不能为空")
    private String required;

    /**
     * 是否有效
     */
    @TableField(value = "`enable`")
    @ApiModelProperty(value = "是否有效")
    @Size(max = 1, message = "是否有效最大长度要小于 1")
    @NotBlank(message = "是否有效不能为空")
    private String enable;

    /**
     * 是否显示
     */
    @TableField(value = "`show`")
    @ApiModelProperty(value = "是否显示")
    @Size(max = 1, message = "是否显示最大长度要小于 1")
    @NotBlank(message = "是否显示不能为空")
    private String show;

    /**
     * 详情时是否显示
     */
    @TableField(value = "detail_show")
    @ApiModelProperty(value = "详情时是否显示")
    @Size(max = 1, message = "详情时是否显示最大长度要小于 1")
    @NotBlank(message = "详情时是否显示不能为空")
    private String detailShow;

    /**
     * 详情弹框布局大小
     */
    @TableField(value = "detail_size")
    @ApiModelProperty(value = "详情弹框布局大小")
    @NotNull(message = "详情弹框布局大小不能为null")
    private Integer detailSize;

    /**
     * 添加时是否显示
     */
    @TableField(value = "add_show")
    @ApiModelProperty(value = "添加时是否显示")
    @Size(max = 1, message = "添加时是否显示最大长度要小于 1")
    @NotBlank(message = "添加时是否显示不能为空")
    private String addShow;

    /**
     * 新增弹框布局大小
     */
    @TableField(value = "add_size")
    @ApiModelProperty(value = "新增弹框布局大小")
    @NotNull(message = "新增弹框布局大小不能为null")
    private Integer addSize;

    /**
     * 新增弹框布局显示后占位填充
     */
    @TableField(value = "add_padding")
    @ApiModelProperty(value = "新增弹框布局显示后占位填充")
    @NotNull(message = "新增弹框布局显示后占位填充不能为null")
    private Integer addPadding;

    /**
     * 编辑框是否显示
     */
    @TableField(value = "edit_show")
    @ApiModelProperty(value = "编辑框是否显示")
    @Size(max = 1, message = "编辑框是否显示最大长度要小于 1")
    @NotBlank(message = "编辑框是否显示不能为空")
    private String editShow;

    /**
     * 编辑弹框布局大小
     */
    @TableField(value = "edit_size")
    @ApiModelProperty(value = "编辑弹框布局大小")
    @NotNull(message = "编辑弹框布局大小不能为null")
    private Integer editSize;

    /**
     * 编辑弹框布局显示后占位填充
     */
    @TableField(value = "edit_padding")
    @ApiModelProperty(value = "编辑弹框布局显示后占位填充")
    @NotNull(message = "编辑弹框布局显示后占位填充不能为null")
    private Integer editPadding;

    /**
     * 是否可做筛选项
     */
    @TableField(value = "filter_able")
    @ApiModelProperty(value = "是否可做筛选项")
    @Size(max = 1, message = "是否可做筛选项最大长度要小于 1")
    @NotBlank(message = "是否可做筛选项不能为空")
    private String filterAble;

    /**
     * 是否可做排序项
     */
    @TableField(value = "sort_able")
    @ApiModelProperty(value = "是否可做排序项")
    @Size(max = 1, message = "是否可做排序项最大长度要小于 1")
    @NotBlank(message = "是否可做排序项不能为空")
    private String sortAble;

    /**
     * 最小值(长度)
     */
    @TableField(value = "`min`")
    @ApiModelProperty(value = "最小值(长度)")
    private BigDecimal min;

    /**
     * 最大值(长度)
     */
    @TableField(value = "`max`")
    @ApiModelProperty(value = "最大值(长度)")
    private BigDecimal max;

    /**
     * 默认内容
     */
    @TableField(value = "default_value")
    @ApiModelProperty(value = "默认内容")
    @Size(max = 200, message = "默认内容最大长度要小于 200")
    private String defaultValue;
}
