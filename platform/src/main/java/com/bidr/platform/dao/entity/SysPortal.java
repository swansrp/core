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
 * Title: SysPortal
 * Description: Copyright: Copyright (c) 2023
 *
 * @author Sharp
 * @since 2023/12/24 23:03
 */

/**
 * 后台管理表
 */
@ApiModel(description = "后台管理表")
@Data
@TableName(value = "sys_portal")
public class SysPortal {
    /**
     * id
     */
    @TableId(value = "id", type = IdType.AUTO)
    @ApiModelProperty(value = "id")
    @NotNull(message = "id不能为null")
    private Long id;

    /**
     * 英文名
     */
    @TableField(value = "`name`")
    @ApiModelProperty(value = "英文名")
    @Size(max = 50, message = "英文名最大长度要小于 50")
    @NotBlank(message = "英文名不能为空")
    private String name;

    /**
     * 中文名
     */
    @TableField(value = "display_name")
    @ApiModelProperty(value = "中文名")
    @Size(max = 50, message = "中文名最大长度要小于 50")
    @NotBlank(message = "中文名不能为空")
    private String displayName;

    /**
     * 是否树形结构
     */
    @TableField(value = "tree_mode")
    @ApiModelProperty(value = "是否树形结构")
    @Size(max = 1, message = "是否树形结构最大长度要小于 1")
    @NotBlank(message = "是否树形结构不能为空")
    private String treeMode;

    /**
     * 表格大小PORTAL_TABLE_SIZE_DICT
     */
    @TableField(value = "`size`")
    @ApiModelProperty(value = "表格大小PORTAL_TABLE_SIZE_DICT")
    @Size(max = 50, message = "表格大小PORTAL_TABLE_SIZE_DICT最大长度要小于 50")
    @NotBlank(message = "表格大小PORTAL_TABLE_SIZE_DICT不能为空")
    private String size;

    /**
     * 只读
     */
    @TableField(value = "read_only")
    @ApiModelProperty(value = "只读")
    @Size(max = 1, message = "只读最大长度要小于 1")
    @NotBlank(message = "只读不能为空")
    private String readOnly;

    /**
     * 总结栏
     */
    @TableField(value = "summary")
    @ApiModelProperty(value = "总结栏")
    @Size(max = 1, message = "总结栏最大长度要小于 1")
    @NotBlank(message = "总结栏不能为空")
    private String summary;

    /**
     * 行id字段名
     */
    @TableField(value = "id_column")
    @ApiModelProperty(value = "行id字段名")
    @Size(max = 50, message = "行id字段名最大长度要小于 50")
    @NotBlank(message = "行id字段名不能为空")
    private String idColumn;

    /**
     * 父id字段名
     */
    @TableField(value = "pid_column")
    @ApiModelProperty(value = "父id字段名")
    @Size(max = 50, message = "父id字段名最大长度要小于 50")
    private String pidColumn;

    /**
     * 名称字段名
     */
    @TableField(value = "name_column")
    @ApiModelProperty(value = "名称字段名")
    @Size(max = 50, message = "名称字段名最大长度要小于 50")
    @NotBlank(message = "名称字段名不能为空")
    private String nameColumn;

    /**
     * 排序字段名
     */
    @TableField(value = "order_column")
    @ApiModelProperty(value = "排序字段名")
    @Size(max = 50, message = "排序字段名最大长度要小于 50")
    @NotBlank(message = "排序字段名不能为空")
    private String orderColumn;

    /**
     * 是否支持顺序调整
     */
    @TableField(value = "order_mode")
    @ApiModelProperty(value = "是否支持顺序调整")
    @Size(max = 1, message = "是否支持顺序调整最大长度要小于 1")
    @NotBlank(message = "是否支持顺序调整不能为空")
    private String orderMode;

    /**
     * 详情每行显示个数
     */
    @TableField(value = "description_count")
    @ApiModelProperty(value = "详情每行显示个数")
    @NotNull(message = "详情每行显示个数不能为null")
    private Integer descriptionCount;

    /**
     * 支持导出
     */
    @TableField(value = "export_able")
    @ApiModelProperty(value = "支持导出")
    @Size(max = 1, message = "支持导出最大长度要小于 1")
    @NotBlank(message = "支持导出不能为空")
    private String exportAble;

    /**
     * 支持导入
     */
    @TableField(value = "import_able")
    @ApiModelProperty(value = "支持导入")
    @Size(max = 1, message = "支持导入最大长度要小于 1")
    @NotBlank(message = "支持导入不能为空")
    private String importAble;
}