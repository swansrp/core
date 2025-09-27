package com.bidr.admin.manage.user.vo;

import com.bidr.admin.config.PortalDictField;
import com.bidr.admin.config.PortalEntityField;
import com.bidr.admin.config.PortalIdField;
import com.bidr.admin.config.PortalImageField;
import com.bidr.authorization.constants.dict.GenderDict;
import com.bidr.authorization.dao.entity.AcDept;
import com.bidr.authorization.vo.admin.UserRes;
import com.bidr.kernel.constant.dict.common.ActiveStatusDict;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * Title: UserAdminRes
 * Description: Copyright: Copyright (c) 2023
 *
 * @author Sharp
 * @since 2023/12/28 21:20
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class UserAdminRes extends UserRes {

    @PortalIdField
    @ApiModelProperty(value = "用户ID")
    private Long userId;

    @PortalDictField(GenderDict.class)
    @ApiModelProperty(value = "用户性别")
    private String sex;

    @PortalDictField(ActiveStatusDict.class)
    @ApiModelProperty(value = "帐号状态")
    private Integer status;

    @PortalEntityField(entity = AcDept.class, field = "name")
    @ApiModelProperty(value = "部门名称")
    private String deptName;

    @ApiModelProperty(value = "头像地址")
    @PortalImageField
    private String avatar;
}
