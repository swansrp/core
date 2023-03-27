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
 * @date 2023/03/17 10:02
 */

@ApiModel(value = "部门表")
@Data
@TableName(value = "ac_dept")
public class AcDept {
    public static final String COL_DEPT_ID = "dept_id";
    public static final String COL_PID = "pid";
    public static final String COL_ANCESTORS = "ancestors";
    public static final String COL_DEPT_NAME = "dept_name";
    public static final String COL_SHOW_ORDER = "show_order";
    public static final String COL_LEADER = "leader";
    public static final String COL_PHONE = "phone";
    public static final String COL_EMAIL = "email";
    public static final String COL_STATUS = "status";
    public static final String COL_CREATE_BY = "create_by";
    public static final String COL_CREATE_TIME = "create_time";
    public static final String COL_UPDATE_BY = "update_by";
    public static final String COL_UPDATE_TIME = "update_time";
    public static final String COL_VALID = "valid";
    /**
     * 部门id
     */
    @TableId(value = "dept_id", type = IdType.INPUT)
    @ApiModelProperty(value = "部门id")
    private Long deptId;
    /**
     * 父部门id
     */
    @TableField(value = "pid")
    @ApiModelProperty(value = "父部门id")
    private Long pid;
    /**
     * 祖级列表
     */
    @TableField(value = "ancestors")
    @ApiModelProperty(value = "祖级列表")
    private String ancestors;
    /**
     * 部门名称
     */
    @TableField(value = "dept_name")
    @ApiModelProperty(value = "部门名称")
    private String deptName;
    /**
     * 显示顺序
     */
    @TableField(value = "show_order")
    @ApiModelProperty(value = "显示顺序")
    private Integer showOrder;
    /**
     * 负责人
     */
    @TableField(value = "leader")
    @ApiModelProperty(value = "负责人")
    private String leader;
    /**
     * 联系电话
     */
    @TableField(value = "phone")
    @ApiModelProperty(value = "联系电话")
    private String phone;
    /**
     * 邮箱
     */
    @TableField(value = "email")
    @ApiModelProperty(value = "邮箱")
    private String email;
    /**
     * 部门状态（0正常 1停用）
     */
    @TableField(value = "`status`")
    @ApiModelProperty(value = "部门状态（0正常 1停用）")
    private String status;
    /**
     * 创建者
     */
    @TableField(value = "create_by")
    @ApiModelProperty(value = "创建者")
    private String createBy;
    /**
     * 创建时间
     */
    @TableField(value = "create_time")
    @ApiModelProperty(value = "创建时间")
    private Date createTime;
    /**
     * 更新者
     */
    @TableField(value = "update_by")
    @ApiModelProperty(value = "更新者")
    private String updateBy;
    /**
     * 更新时间
     */
    @TableField(value = "update_time")
    @ApiModelProperty(value = "更新时间")
    private Date updateTime;
    /**
     * 有效性
     */
    @TableField(value = "`valid`")
    @ApiModelProperty(value = "有效性")
    private String valid;
}
