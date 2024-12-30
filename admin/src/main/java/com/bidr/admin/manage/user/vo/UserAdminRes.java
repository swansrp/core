package com.bidr.admin.manage.user.vo;

import com.bidr.admin.config.*;
import com.bidr.authorization.dao.entity.AcDept;
import com.bidr.authorization.dao.entity.AcUser;
import com.bidr.authorization.vo.admin.UserRes;
import com.diboot.core.binding.annotation.BindField;
import com.fasterxml.jackson.annotation.JsonFormat;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.springframework.format.annotation.DateTimeFormat;

import javax.validation.constraints.Size;
import java.util.Date;

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

    @ApiModelProperty(value = "头像地址")
    @PortalImageField
    private String avatar;
}
