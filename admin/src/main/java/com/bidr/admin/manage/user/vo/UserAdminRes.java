package com.bidr.admin.manage.user.vo;

import com.bidr.admin.config.PortalEntityField;
import com.bidr.authorization.dao.entity.AcDept;
import com.bidr.authorization.vo.admin.UserRes;
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

    @PortalEntityField(entity = AcDept.class, field = "name")
    @ApiModelProperty(value = "部门名称")
    private String deptName;
}
