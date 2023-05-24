package com.bidr.authorization.dao.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.bidr.kernel.mybatis.anno.AutoInsert;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import java.util.Date;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Size;
import lombok.Data;

 /**
 * Title: AcUser
 * Description: Copyright: Copyright (c) 2023
 *
 * @author Sharp
 * @since 2023/05/24 10:04
 */

/**
 * 用户信息表
 */
@ApiModel(description = "用户信息表")
@Data
@TableName(value = "ac_user")
public class AcUser {
    /**
     * 用户ID
     */
    @TableId(value = "user_id", type = IdType.AUTO)
    @ApiModelProperty(value = "用户ID")
    @NotNull(message = "用户ID不能为null")
    private Long userId;

    /**
     * 用户编码
     */
    @AutoInsert(seq = "AC_USER_CUSTOMER_NUMBER_SEQ")
    @TableField(value = "customer_number")
    @ApiModelProperty(value = "用户编码")
    @Size(max = 50, message = "用户编码最大长度要小于 50")
    @NotBlank(message = "用户编码不能为空")
    private String customerNumber;

    /**
     * 用户姓名
     */
    @TableField(value = "`name`")
    @ApiModelProperty(value = "用户姓名")
    @Size(max = 50, message = "用户姓名最大长度要小于 50")
    private String name;

    /**
     * 部门ID
     */
    @TableField(value = "dept_id")
    @ApiModelProperty(value = "部门ID")
    @Size(max = 50, message = "部门ID最大长度要小于 50")
    private String deptId;

    /**
     * 用户账号
     */
    @TableField(value = "user_name")
    @ApiModelProperty(value = "用户账号")
    @Size(max = 30, message = "用户账号最大长度要小于 30")
    @NotBlank(message = "用户账号不能为空")
    private String userName;

    /**
     * 用户昵称
     */
    @TableField(value = "nick_name")
    @ApiModelProperty(value = "用户昵称")
    @Size(max = 30, message = "用户昵称最大长度要小于 30")
    private String nickName;

    /**
     * 用户类型（00系统用户）
     */
    @TableField(value = "user_type")
    @ApiModelProperty(value = "用户类型（00系统用户）")
    @Size(max = 2, message = "用户类型（00系统用户）最大长度要小于 2")
    private String userType;

    /**
     * 用户邮箱
     */
    @TableField(value = "email")
    @ApiModelProperty(value = "用户邮箱")
    @Size(max = 50, message = "用户邮箱最大长度要小于 50")
    private String email;

    /**
     * 手机号码
     */
    @TableField(value = "phone_number")
    @ApiModelProperty(value = "手机号码")
    @Size(max = 11, message = "手机号码最大长度要小于 11")
    private String phoneNumber;

    /**
     * 用户性别（1男 2女）
     */
    @TableField(value = "sex")
    @ApiModelProperty(value = "用户性别（1男 2女）")
    @Size(max = 1, message = "用户性别（1男 2女）最大长度要小于 1")
    private String sex;

    /**
     * 头像地址
     */
    @TableField(value = "avatar")
    @ApiModelProperty(value = "头像地址")
    @Size(max = 100, message = "头像地址最大长度要小于 100")
    private String avatar;

    /**
     * 密码
     */
    @TableField(value = "`password`")
    @ApiModelProperty(value = "密码")
    @Size(max = 100, message = "密码最大长度要小于 100")
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
    @Size(max = 128, message = "最后登录IP最大长度要小于 128")
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
    private Long createBy;

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
    private Long updateBy;

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
    @Size(max = 500, message = "备注最大长度要小于 500")
    private String remark;

    /**
     * 有效性
     */
    @TableField(value = "`valid`")
    @ApiModelProperty(value = "有效性")
    @Size(max = 1, message = "有效性最大长度要小于 1")
    private String valid;

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
}
