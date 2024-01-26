package com.bidr.authorization.vo.admin;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.bidr.authorization.dao.entity.AcDept;
import com.bidr.authorization.dao.entity.AcUser;
import com.bidr.kernel.mybatis.anno.AutoInsert;
import com.bidr.kernel.mybatis.anno.PortalEntityField;
import com.bidr.platform.config.portal.PortalEntity;
import com.diboot.core.binding.annotation.BindDict;
import com.diboot.core.binding.annotation.BindField;
import com.fasterxml.jackson.annotation.JsonFormat;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.springframework.format.annotation.DateTimeFormat;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Size;
import java.util.Date;

/**
 * Title: UserRes
 * Description: Copyright: Copyright (c) 2023
 *
 * @author Sharp
 * @since 2023/12/28 21:20
 */
@Data
@PortalEntity("user")
public class UserRes  {

    @ApiModelProperty(value = "用户ID")
    @NotNull(message = "用户ID不能为null")
    private Long userId;

    @ApiModelProperty(value = "用户编码")
    private String customerNumber;

    @ApiModelProperty(value = "用户姓名")
    @Size(max = 50, message = "用户姓名最大长度要小于 50")
    private String name;

    @ApiModelProperty(value = "部门ID")
    @Size(max = 50, message = "部门ID最大长度要小于 50")
    private String deptId;

    @PortalEntityField(entity = AcDept.class, field = "name")
    @ApiModelProperty(value = "部门名称")
    private String deptName;

    @ApiModelProperty(value = "用户账号")
    @Size(max = 30, message = "用户账号最大长度要小于 30")
    @NotBlank(message = "用户账号不能为空")
    private String userName;

    @ApiModelProperty(value = "用户昵称")
    @Size(max = 30, message = "用户昵称最大长度要小于 30")
    private String nickName;

    @ApiModelProperty(value = "用户邮箱")
    @Size(max = 50, message = "用户邮箱最大长度要小于 50")
    private String email;

    @ApiModelProperty(value = "手机号码")
    @Size(max = 11, message = "手机号码最大长度要小于 11")
    private String phoneNumber;

    @ApiModelProperty(value = "用户性别（1男 2女）")
    @Size(max = 1, message = "用户性别（1男 2女）最大长度要小于 1")
    private String sex;

    @ApiModelProperty(value = "头像地址")
    @Size(max = 100, message = "头像地址最大长度要小于 100")
    private String avatar;

    @ApiModelProperty(value = "密码输入错误次数")
    private Integer passwordErrorTime;

    @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    @ApiModelProperty(value = "上次密码修改时间")
    private Date passwordLastTime;

    @ApiModelProperty(value = "帐号状态ACTIVE_STATUS_DICT")
    private Integer status;

    @ApiModelProperty(value = "最后登录IP")
    @Size(max = 128, message = "最后登录IP最大长度要小于 128")
    private String loginIp;

    @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    @ApiModelProperty(value = "最后登录时间")
    private Date loginDate;

    @ApiModelProperty(value = "备注")
    @Size(max = 500, message = "备注最大长度要小于 500")
    private String remark;

}
