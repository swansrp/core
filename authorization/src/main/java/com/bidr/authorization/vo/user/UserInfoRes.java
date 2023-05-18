package com.bidr.authorization.vo.user;

import com.bidr.authorization.dao.entity.AcDept;
import com.diboot.core.binding.annotation.BindField;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

/**
 * Title: UserInfoRes
 * Description: Copyright: Copyright (c) 2023
 *
 * @author Sharp
 * @since 2023/05/18 09:00
 */
@Data
public class UserInfoRes {

    @ApiModelProperty(value = "用户编码")
    private String customerNumber;

    @ApiModelProperty(value = "用户姓名")
    private String name;

    @BindField(entity = AcDept.class, field = "name", condition = "this.deptId = dept_id")
    private String deptName;

    @ApiModelProperty(value = "部门ID")
    private String deptId;

    @ApiModelProperty(value = "用户账号")
    private String userName;

    @ApiModelProperty(value = "用户昵称")
    private String nickName;

    @ApiModelProperty(value = "用户邮箱")
    private String email;

    @ApiModelProperty(value = "手机号码")
    private String phoneNumber;

    @ApiModelProperty(value = "用户性别（1男 2女）")
    private String sex;

    @ApiModelProperty(value = "头像地址")
    private String avatar;
}
