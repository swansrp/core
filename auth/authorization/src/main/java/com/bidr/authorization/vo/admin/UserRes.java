package com.bidr.authorization.vo.admin;

import com.bidr.authorization.constants.dict.GenderDict;
import com.bidr.authorization.dao.entity.AcDept;
import com.bidr.kernel.constant.dict.common.ActiveStatusDict;
import com.bidr.kernel.constant.dict.portal.anno.PortalDictField;
import com.bidr.kernel.constant.dict.portal.anno.PortalDisableField;
import com.bidr.kernel.constant.dict.portal.anno.PortalEditAbleField;
import com.bidr.kernel.constant.dict.portal.anno.PortalEntityField;
import com.bidr.kernel.constant.dict.portal.anno.PortalGroupDisplayField;
import com.bidr.kernel.constant.dict.portal.anno.PortalIdField;
import com.bidr.kernel.constant.dict.portal.anno.PortalImageField;
import com.bidr.kernel.constant.dict.portal.anno.PortalImportField;
import com.bidr.kernel.constant.dict.portal.anno.PortalLargeField;
import com.bidr.kernel.constant.dict.portal.anno.PortalNameField;
import com.bidr.kernel.constant.dict.portal.anno.PortalTextAreaField;
import com.fasterxml.jackson.annotation.JsonFormat;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;
import org.springframework.format.annotation.DateTimeFormat;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Size;
import java.util.Date;

/**
 * Title: UserRes
 * Description: Copyright: Copyright (c) 2023
 * <p>
 * 🔴 本类字段声明顺序＋Portal 注解是用户管理页列配置（sys_portal_column）的唯一正源：
 * PortalConfigService 启动扫描按声明序产出 display_order，注解产出显隐/字典/分组等属性。
 * 调整顺序＝调整表格版面；勿随手重排。
 *
 * @author Sharp
 * @since 2023-12-28 21:20
 */
@Data
public class UserRes {

    @PortalIdField
    @PortalDisableField
    @ApiModelProperty(value = "用户ID")
    @NotNull(message = "用户ID不能为null")
    private Long userId;

    @PortalDisableField
    @ApiModelProperty(value = "用户编码")
    private String customerNumber;

    @PortalDisableField
    @ApiModelProperty(value = "部门ID")
    @Size(max = 50, message = "部门ID最大长度要小于 50")
    private String deptId;

    @PortalImportField
    @PortalGroupDisplayField("账号信息")
    @ApiModelProperty(value = "用户账号")
    @Size(max = 30, message = "用户账号最大长度要小于 30")
    @NotBlank(message = "用户账号不能为空")
    private String userName;

    @PortalNameField
    @PortalGroupDisplayField("账号信息")
    @ApiModelProperty(value = "用户姓名")
    @Size(max = 50, message = "用户姓名最大长度要小于 50")
    private String name;

    @PortalImageField
    @PortalGroupDisplayField("账号信息")
    @ApiModelProperty(value = "头像地址")
    @Size(max = 100, message = "头像地址最大长度要小于 100")
    private String avatar;

    @PortalEntityField(entity = AcDept.class, field = "name", entityField = "deptId", joinField = "deptId")
    @PortalEditAbleField
    @PortalGroupDisplayField("账号信息")
    @ApiModelProperty(value = "部门名称")
    private String deptName;

    @PortalGroupDisplayField("账号信息")
    @ApiModelProperty(value = "用户昵称")
    @Size(max = 30, message = "用户昵称最大长度要小于 30")
    private String nickName;

    @PortalGroupDisplayField("账号信息")
    @ApiModelProperty(value = "用户邮箱")
    @Size(max = 50, message = "用户邮箱最大长度要小于 50")
    private String email;

    @PortalGroupDisplayField("账号信息")
    @ApiModelProperty(value = "手机号码")
    @Size(max = 11, message = "手机号码最大长度要小于 11")
    private String phoneNumber;

    @PortalDictField(GenderDict.class)
    @PortalGroupDisplayField("账号信息")
    @ApiModelProperty(value = "用户性别")
    @Size(max = 1, message = "用户性别最大长度要小于 1")
    private String sex;

    @PortalDictField(ActiveStatusDict.class)
    @PortalGroupDisplayField("账号信息")
    @ApiModelProperty(value = "帐号状态")
    private Integer status;

    @PortalGroupDisplayField("登录信息")
    @ApiModelProperty(value = "密码输入错误次数")
    private Integer passwordErrorTime;

    @PortalGroupDisplayField("登录信息")
    @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    @ApiModelProperty(value = "上次密码修改时间")
    private Date passwordLastTime;

    @PortalGroupDisplayField("登录信息")
    @ApiModelProperty(value = "最后登录IP")
    @Size(max = 128, message = "最后登录IP最大长度要小于 128")
    private String loginIp;

    @PortalGroupDisplayField("登录信息")
    @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    @ApiModelProperty(value = "最后登录时间")
    private Date loginDate;

    @PortalGroupDisplayField("账号信息")
    @PortalTextAreaField
    @PortalLargeField
    @ApiModelProperty(value = "备注")
    @Size(max = 500, message = "备注最大长度要小于 500")
    private String remark;

}
