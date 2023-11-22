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
 * @since 2023/11/22 13:43
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
     * 是否支持顺序
     */
    @TableField(value = "order_mode")
    @ApiModelProperty(value = "是否支持顺序")
    @Size(max = 1, message = "是否支持顺序最大长度要小于 1")
    @NotBlank(message = "是否支持顺序不能为空")
    private String orderMode;

    /**
     * 表格大小
     */
    @TableField(value = "`size`")
    @ApiModelProperty(value = "表格大小")
    @Size(max = 50, message = "表格大小最大长度要小于 50")
    @NotBlank(message = "表格大小不能为空")
    private String size;

    /**
     * 行id列名
     */
    @TableField(value = "id_column")
    @ApiModelProperty(value = "行id列名")
    @Size(max = 50, message = "行id列名最大长度要小于 50")
    @NotBlank(message = "行id列名不能为空")
    private String idColumn;

    /**
     * 父id列名
     */
    @TableField(value = "pid_column")
    @ApiModelProperty(value = "父id列名")
    @Size(max = 50, message = "父id列名最大长度要小于 50")
    private String pidColumn;

    /**
     * 名称列名
     */
    @TableField(value = "name_column")
    @ApiModelProperty(value = "名称列名")
    @Size(max = 50, message = "名称列名最大长度要小于 50")
    @NotBlank(message = "名称列名不能为空")
    private String nameColumn;
}