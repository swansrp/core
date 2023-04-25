package com.bidr.authorization.dao.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

import java.util.Date;

/**
 * Title: AcDept
 * Description: Copyright: Copyright (c) 2022 Company: Sharp Ltd.
 *
 * @author Sharp
 * @date 2023/04/23 13:45
 */

/**
 * 部门表
 */
@ApiModel(value = "部门表")
@Data
@TableName(value = "ac_dept")
public class AcDept {
    public static final String COL_DEPT_ID = "dept_id";
    public static final String COL_PID = "pid";
    public static final String COL_GRAND_ID = "grand_id";
    public static final String COL_ANCESTORS = "ancestors";
    public static final String COL_NAME = "name";
    public static final String COL_ABBREVIATE = "abbreviate";
    public static final String COL_FOUNDED_TIME = "founded_time";
    public static final String COL_CATEGORY = "category";
    public static final String COL_TYPE = "type";
    public static final String COL_FUNCTION = "function";
    public static final String COL_LEADER = "leader";
    public static final String COL_CONTACT = "contact";
    public static final String COL_ADDRESS = "address";
    public static final String COL_STATUS = "status";
    public static final String COL_SHOW_ORDER = "show_order";
    public static final String COL_CREATE_BY = "create_by";
    public static final String COL_CREATE_AT = "create_at";
    public static final String COL_UPDATE_BY = "update_by";
    public static final String COL_UPDATE_AT = "update_at";
    public static final String COL_VALID = "valid";
    /**
     * 部门id
     */
    @TableId(value = "dept_id", type = IdType.AUTO)
    @ApiModelProperty(value = "部门id")
    private String deptId;
    /**
     * 父部门id
     */
    @TableField(value = "pid")
    @ApiModelProperty(value = "父部门id")
    private String pid;
    /**
     * 祖父id
     */
    @TableField(value = "grand_id")
    @ApiModelProperty(value = "祖父id")
    private String grandId;
    /**
     * 祖级列表
     */
    @TableField(value = "ancestors")
    @ApiModelProperty(value = "祖级列表")
    private String ancestors;
    /**
     * 部门名称
     */
    @TableField(value = "`name`")
    @ApiModelProperty(value = "部门名称")
    private String name;
    /**
     * 简称
     */
    @TableField(value = "abbreviate")
    @ApiModelProperty(value = "简称")
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
    private String category;
    /**
     * 类型
     */
    @TableField(value = "`type`")
    @ApiModelProperty(value = "类型")
    private String type;
    /**
     * 职能
     */
    @TableField(value = "`function`")
    @ApiModelProperty(value = "职能")
    private String function;
    /**
     * 负责人
     */
    @TableField(value = "leader")
    @ApiModelProperty(value = "负责人")
    private String leader;
    /**
     * 联系电话
     */
    @TableField(value = "contact")
    @ApiModelProperty(value = "联系电话")
    private String contact;
    /**
     * 地址
     */
    @TableField(value = "address")
    @ApiModelProperty(value = "地址")
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
    @TableField(value = "create_by")
    @ApiModelProperty(value = "创建者")
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
    @TableField(value = "update_by")
    @ApiModelProperty(value = "更新者")
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
    private String valid;
}
