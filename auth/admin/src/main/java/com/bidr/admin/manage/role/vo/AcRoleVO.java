package com.bidr.admin.manage.role.vo;

import com.bidr.kernel.constant.dict.portal.anno.PortalDictField;
import com.bidr.kernel.constant.dict.portal.anno.PortalIdField;
import com.bidr.kernel.constant.dict.portal.anno.PortalNameField;
import com.bidr.kernel.constant.dict.portal.anno.PortalOrderField;
import com.bidr.kernel.constant.dict.common.BoolDict;
import com.fasterxml.jackson.annotation.JsonIgnore;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

import javax.validation.constraints.NotNull;

/**
 * Title: AcRoleVO
 * Description: Copyright: Copyright (c) 2025 Company: Bidr Ltd.
 *
 * @author Sharp
 * @since 2025/8/25 14:16
 */
@Data
public class AcRoleVO {
    @PortalIdField
    @ApiModelProperty(value = "角色ID")
    private Long roleId;

    @PortalNameField
    @ApiModelProperty(value = "角色名称")
    private String roleName;

    @PortalDictField(BoolDict.class)
    @NotNull(message = "启用")
    private Integer status;

    @PortalOrderField
    @ApiModelProperty(value = "显示顺序")
    private Integer displayOrder;
}
