package com.bidr.authorization.dao.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.bidr.kernel.mybatis.anno.AutoInsert;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

import java.util.Date;

/**
 * Title: AcUser
 * Description: Copyright: Copyright (c) 2023 Company: Sharp Ltd.
 *
 * @author Sharp
 * @date 2023/05/04 10:50
 */

/**
 * 用户信息表
 */
@ApiModel(value = "用户信息表")
@Data
@TableName(value = "ac_user")
public class AcUser {
    public static final String COL_USER_ID = "user_id";
    public static final String COL_CUSTOMER_NUMBER = "customer_number";
    public static final String COL_NAME = "name";
    public static final String COL_DEPT_ID = "dept_id";
    public static final String COL_USER_NAME = "user_name";
    public static final String COL_NICK_NAME = "nick_name";
    public static final String COL_USER_TYPE = "user_type";
    public static final String COL_EMAIL = "email";
    public static final String COL_PHONE_NUMBER = "phone_number";
    public static final String COL_SEX = "sex";
    public static final String COL_AVATAR = "avatar";
    public static final String COL_PASSWORD = "password";
    public static final String COL_PASSWORD_ERROR_TIME = "password_error_time";
    public static final String COL_PASSWORD_LAST_TIME = "password_last_time";
    public static final String COL_STATUS = "status";
    public static final String COL_LOGIN_IP = "login_ip";
    public static final String COL_LOGIN_DATE = "login_date";
    public static final String COL_CREATE_BY = "create_by";
    public static final String COL_CREATE_AT = "create_at";
    public static final String COL_UPDATE_BY = "update_by";
    public static final String COL_UPDATE_AT = "update_at";
    public static final String COL_REMARK = "remark";
    public static final String COL_VALID = "valid";
    /**
     * 用户ID
     */
    @TableId(value = "user_id", type = IdType.AUTO)
    @ApiModelProperty(value = "用户ID")
    private Long userId;
    /**
     * 用户编码
     */
    @AutoInsert(seq = "AC_USER_CUSTOMER_NUMBER_SEQ")
    @TableField(value = "customer_number", fill = FieldFill.INSERT)
    @ApiModelProperty(value = "用户编码")
    private String customerNumber;
    /**
     * 用户姓名
     */
    @TableField(value = "`name`")
    @ApiModelProperty(value = "用户姓名")
    private String name;
    /**
     * 部门ID
     */
    @TableField(value = "dept_id")
    @ApiModelProperty(value = "部门ID")
    private String deptId;
    /**
     * 用户账号
     */
    @TableField(value = "user_name")
    @ApiModelProperty(value = "用户账号")
    private String userName;
    /**
     * 用户昵称
     */
    @TableField(value = "nick_name")
    @ApiModelProperty(value = "用户昵称")
    private String nickName;
    /**
     * 用户类型（00系统用户）
     */
    @TableField(value = "user_type")
    @ApiModelProperty(value = "用户类型（00系统用户）")
    private String userType;
    /**
     * 用户邮箱
     */
    @TableField(value = "email")
    @ApiModelProperty(value = "用户邮箱")
    private String email;
    /**
     * 手机号码
     */
    @TableField(value = "phone_number")
    @ApiModelProperty(value = "手机号码")
    private String phoneNumber;
    /**
     * 用户性别（1男 2女）
     */
    @TableField(value = "sex")
    @ApiModelProperty(value = "用户性别（1男 2女）")
    private String sex;
    /**
     * 头像地址
     */
    @TableField(value = "avatar")
    @ApiModelProperty(value = "头像地址")
    private String avatar;
    /**
     * 密码
     */
    @TableField(value = "`password`")
    @ApiModelProperty(value = "密码")
    private String password;
    /**
     * 密码输入错误次数
     */
    @TableField(value = "password_error_time")
    @ApiModelProperty(value = "密码输入错误次数")
    private Integer passwordErrorTime;
    /**
     * 上次密码修改时间
     */
    @TableField(value = "password_last_time")
    @ApiModelProperty(value = "上次密码修改时间")
    private Date passwordLastTime;
    /**
     * 帐号状态ACTIVE_STATUS_DICT
     */
    @TableField(value = "`status`")
    @ApiModelProperty(value = "帐号状态ACTIVE_STATUS_DICT")
    private Integer status;
    /**
     * 最后登录IP
     */
    @TableField(value = "login_ip")
    @ApiModelProperty(value = "最后登录IP")
    private String loginIp;
    /**
     * 最后登录时间
     */
    @TableField(value = "login_date")
    @ApiModelProperty(value = "最后登录时间")
    private Date loginDate;
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
     * 备注
     */
    @TableField(value = "remark")
    @ApiModelProperty(value = "备注")
    private String remark;
    /**
     * 有效性
     */
    @TableField(value = "`valid`")
    @ApiModelProperty(value = "有效性")
    private String valid;
}
