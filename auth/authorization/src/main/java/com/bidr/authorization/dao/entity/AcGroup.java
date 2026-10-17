package com.bidr.authorization.dao.entity;

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

 /**
 * Title: AcGroup
 * Description: Copyright: Copyright (c) 2023
 *
 * @author Sharp
 * @since 2023/05/18 11:18
 */

/**
 * 用户逻辑组群
 */
@ApiModel(description = "用户逻辑组群")
@Data
@TableName(value = "ac_group")
public class AcGroup {
    @TableId(value = "id", type = IdType.AUTO)
    @ApiModelProperty(value = "")
    @NotNull(message = "不能为null")
    private Long id;

    /**
     * 父id
     */
    @TableField(value = "pid")
    @ApiModelProperty(value = "父id")
    private Long pid;

    @TableField(value = "`key`")
    @ApiModelProperty(value = "")
    @NotNull(message = "不能为null")
    private Long key;

    /**
     * 组类型
     */
    @TableField(value = "`type`")
    @ApiModelProperty(value = "组类型")
    @Size(max = 50, message = "组类型最大长度要小于 50")
    @NotBlank(message = "组类型不能为空")
    private String type;

    /**
     * 组群名
     */
    @TableField(value = "`name`")
    @ApiModelProperty(value = "组群名")
    @Size(max = 50, message = "组群名最大长度要小于 50")
    @NotBlank(message = "组群名不能为空")
    private String name;

    /**
     * 显示顺序
     */
    @TableField(value = "display_order")
    @ApiModelProperty(value = "显示顺序")
    @NotNull(message = "显示顺序不能为null")
    private Integer displayOrder;
}
