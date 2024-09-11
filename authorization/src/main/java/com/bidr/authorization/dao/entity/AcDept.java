package com.bidr.authorization.dao.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.bidr.authorization.mybatis.anno.AccountContextFill;
import com.bidr.kernel.mybatis.anno.AutoInsert;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.Size;
import java.util.Date;

/**
 * Title: AcDept
 * Description: Copyright: Copyright (c) 2023
 *
 * @author Sharp
 * @since 2023/05/29 16:51
 */

/**
 * 部门表
 */
@ApiModel(description = "部门表")
@Data
@TableName(value = "ac_dept")
@AccountContextFill
public class AcDept {
    /**
     * 部门id
     */
    @TableId(value = "dept_id", type = IdType.INPUT)
    @AutoInsert(seq = "AC_DEPT_ID_SEQ")
    @ApiModelProperty(value = "部门id")
    @Size(max = 20, message = "部门id最大长度要小于 20")
    @NotBlank(message = "部门id不能为空")
    private String deptId;

    /**
     * 父部门id
     */
    @TableField(value = "pid")
    @ApiModelProperty(value = "父部门id")
    @Size(max = 20, message = "父部门id最大长度要小于 20")
    private String pid;

    /**
     * 祖父id
     */
    @TableField(value = "grand_id")
    @ApiModelProperty(value = "祖父id")
    @Size(max = 20, message = "祖父id最大长度要小于 20")
    private String grandId;

    /**
     * 祖级列表
     */
    @TableField(value = "ancestors")
    @ApiModelProperty(value = "祖级列表")
    @Size(max = 50, message = "祖级列表最大长度要小于 50")
    private String ancestors;

    /**
     * 部门名称
     */
    @TableField(value = "`name`")
    @ApiModelProperty(value = "部门名称")
    @Size(max = 30, message = "部门名称最大长度要小于 30")
    private String name;

    /**
     * 简称
     */
    @TableField(value = "abbreviate")
    @ApiModelProperty(value = "简称")
    @Size(max = 50, message = "简称最大长度要小于 50")
    private String abbreviate;

    /**
     * 建立时间
     */
    @TableField(value = "founded_time")
    @ApiModelProperty(value = "建立时间")
    private Date foundedTime;

    /**
     * 类别
     */
    @TableField(value = "category")
    @ApiModelProperty(value = "类别")
    @Size(max = 20, message = "类别最大长度要小于 20")
    private String category;

    /**
     * 类型
     */
    @TableField(value = "`type`")
    @ApiModelProperty(value = "类型")
    @Size(max = 20, message = "类型最大长度要小于 20")
    private String type;

    /**
     * 职能
     */
    @TableField(value = "`function`")
    @ApiModelProperty(value = "职能")
    @Size(max = 20, message = "职能最大长度要小于 20")
    private String function;

    /**
     * 负责人
     */
    @TableField(value = "leader")
    @ApiModelProperty(value = "负责人")
    @Size(max = 20, message = "负责人最大长度要小于 20")
    private String leader;

    /**
     * 联系电话
     */
    @TableField(value = "contact")
    @ApiModelProperty(value = "联系电话")
    @Size(max = 11, message = "联系电话最大长度要小于 11")
    private String contact;

    /**
     * 地址
     */
    @TableField(value = "address")
    @ApiModelProperty(value = "地址")
    @Size(max = 50, message = "地址最大长度要小于 50")
    private String address;

    /**
     * 部门状态
     */
    @TableField(value = "`status`")
    @ApiModelProperty(value = "部门状态")
    private Integer status;

    /**
     * 显示顺序
     */
    @TableField(value = "show_order")
    @ApiModelProperty(value = "显示顺序")
    private Integer showOrder;

    /**
     * 创建者
     */
    @TableField(value = "create_by", fill = FieldFill.INSERT)
    @ApiModelProperty(value = "创建者")
    @Size(max = 50, message = "创建者最大长度要小于 50")
    private String createBy;

    /**
     * 创建时间
     */
    @TableField(value = "create_at")
    @ApiModelProperty(value = "创建时间")
    private Date createAt;

    /**
     * 更新者
     */
    @TableField(value = "update_by", fill = FieldFill.INSERT_UPDATE)
    @ApiModelProperty(value = "更新者")
    @Size(max = 50, message = "更新者最大长度要小于 50")
    private String updateBy;

    /**
     * 更新时间
     */
    @TableField(value = "update_at")
    @ApiModelProperty(value = "更新时间")
    private Date updateAt;

    /**
     * 有效性
     */
    @TableField(value = "`valid`")
    @ApiModelProperty(value = "有效性")
    @Size(max = 1, message = "有效性最大长度要小于 1")
    private String valid;
}
