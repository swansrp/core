package com.bidr.authorization.vo.user;

import com.bidr.authorization.dao.entity.AcDept;
import com.bidr.authorization.dao.entity.AcRole;
import com.diboot.core.binding.annotation.BindField;
import com.diboot.core.binding.annotation.BindFieldList;
import com.fasterxml.jackson.annotation.JsonIgnore;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

import java.util.List;

/**
 * Title: UserInfoRes
 * Description: Copyright: Copyright (c) 2023
 *
 * @author Sharp
 * @since 2023/05/18 09:00
 */
@Data
public class UserInfoRes {
    @JsonIgnore
    private Long userId;

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

    @BindFieldList(entity = AcRole.class, field = "roleId", condition = "this.userId = ac_user_role.user_id and ac_user_role.role_id = role_id")
    private List<Long> roleList;

}
